package com.example.ai.needle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

data class MirageObject(
    val name: String,
    val score: Float = 0f,
    val attributes: String = "",
    val id: String = "",
    val bbox: List<Int> = emptyList()
)

data class MirageTextRegion(
    val text: String,
    val score: Float = 0f,
    val bbox: List<Int> = emptyList()
)

data class MirageResult(
    val description: String = "",
    val objects: List<MirageObject> = emptyList(),
    val textLines: List<MirageTextRegion> = emptyList(),
    val relations: List<String> = emptyList(),
    val formFields: Map<String, String> = emptyMap(),
    val language: String? = null,
    val ttftMs: Double? = null,
    val decodeTps: Double? = null,
    val rawJson: String = ""
) {
    fun mergedOcrText(): String =
        textLines.map { it.text.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")

    fun mergedLabels(limit: Int = 8): List<String> =
        objects.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().take(limit)

    /** Compact Spanish caption for saving inside MediaItem.caption. */
    fun formattedJournalCaption(): String {
        val parts = mutableListOf<String>()
        if (description.isNotBlank()) {
            parts.add(description.trim())
        }
        val topObjects = objects
            .filter { it.name.isNotBlank() && (it.score <= 0f || it.score >= 0.30f) }
            .distinctBy { it.name.lowercase() }
            .take(5)
            .map { obj ->
                if (obj.attributes.isNotBlank()) "${obj.name} (${obj.attributes})" else obj.name
            }
        if (topObjects.isNotEmpty() && description.isBlank()) {
            parts.add("Escena con: ${topObjects.joinToString(", ")}")
        }
        return parts.joinToString(" · ")
    }

    fun isEmpty(): Boolean =
        description.isBlank() && objects.isEmpty() && textLines.isEmpty() && relations.isEmpty() && formFields.isEmpty()
}

object MirageVision {

    /**
     * Default options JSON passed to `cactus_describe`: requests scene,
     * objects, OCR text, and spatial relations while omitting heavy segmentation
     * bitmaps unless requested.
     */
    const val DEFAULT_OPTIONS_JSON =
        """{"sections":["scene","objects","text","relations"],"conf":0.30,"text_conf":0.30,"max_objects":16,"max_text_lines":24}"""

    fun decodePackedRgbFromUri(context: Context, uri: Uri, maxDim: Int = 768): Triple<ByteArray, Int, Int>? {
        val rawBitmap = decodeScaledBitmap(context, uri, maxDim) ?: return null
        return try {
            bitmapToPackedRgb(rawBitmap, maxDim)
        } finally {
            if (!rawBitmap.isRecycled) rawBitmap.recycle()
        }
    }

    fun bitmapToPackedRgb(bitmap: Bitmap, maxDim: Int = 768): Triple<ByteArray, Int, Int>? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val scale = max(bitmap.width, bitmap.height).let { longest ->
            if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1f
        }
        val targetW = max(1, (bitmap.width * scale).roundToInt())
        val targetH = max(1, (bitmap.height * scale).roundToInt())

        val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        val argb = if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, false)
        }

        val w = argb.width
        val h = argb.height
        val pixels = IntArray(w * h)
        argb.getPixels(pixels, 0, w, 0, 0, w, h)

        if (argb !== scaled && argb !== bitmap && !argb.isRecycled) argb.recycle()
        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()

        val rgb = ByteArray(w * h * 3)
        var j = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            rgb[j++] = ((c ushr 16) and 0xFF).toByte()
            rgb[j++] = ((c ushr 8) and 0xFF).toByte()
            rgb[j++] = (c and 0xFF).toByte()
        }
        return Triple(rgb, w, h)
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var inSampleSize = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (inSampleSize * 2) >= maxDim) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val rotation = readExifRotationDegrees(context, uri)
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
        return rotated
    }

    private fun readExifRotationDegrees(context: Context, uri: Uri): Float = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Throwable) {
        0f
    }

    fun parseJson(raw: String?): MirageResult? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            val root = JSONObject(trimmed)
            val sceneDesc = extractSceneDescription(root)
            val objs = parseObjects(root.optJSONArray("objects"))
            val texts = parseTexts(root)
            val relations = parseRelations(root.optJSONArray("relations"))
            val formMap = parseForm(root.optJSONObject("form"))
            val ttft = if (root.has("ttft_ms")) root.optDouble("ttft_ms") else null
            val tps = if (root.has("decode_tps")) root.optDouble("decode_tps") else null
            val lang = root.optString("language").takeIf { it.isNotBlank() }

            MirageResult(
                description = sceneDesc,
                objects = objs,
                textLines = texts,
                relations = relations,
                formFields = formMap,
                language = lang,
                ttftMs = ttft?.takeIf { !it.isNaN() },
                decodeTps = tps?.takeIf { !it.isNaN() },
                rawJson = trimmed
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractSceneDescription(root: JSONObject): String {
        val direct = root.optString("description").trim()
        if (direct.isNotEmpty()) return direct
        val sceneAny = root.opt("scene")
        return when (sceneAny) {
            is String -> sceneAny.trim()
            is JSONObject -> {
                listOf("description", "summary", "setting", "lighting", "surface")
                    .mapNotNull { key -> sceneAny.optString(key).trim().takeIf { it.isNotEmpty() } }
                    .joinToString(". ")
            }
            else -> ""
        }
    }

    private fun parseObjects(arr: JSONArray?): List<MirageObject> {
        if (arr == null) return emptyList()
        val list = ArrayList<MirageObject>(arr.length())
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let {
                    list.add(MirageObject(name = it))
                }
                is JSONObject -> {
                    val name = item.optString("name")
                        .ifBlank { item.optString("label") }
                        .ifBlank { item.optString("object") }
                        .trim()
                    if (name.isEmpty()) continue
                    val score = when {
                        item.has("score") -> item.optDouble("score", 0.0).toFloat()
                        item.has("confidence") -> item.optDouble("confidence", 0.0).toFloat()
                        else -> 0f
                    }
                    val attrsList = mutableListOf<String>()
                    val attrsObj = item.optJSONArray("attributes")
                    if (attrsObj != null) {
                        for (j in 0 until attrsObj.length()) {
                            attrsObj.optString(j).trim().takeIf { it.isNotEmpty() }?.let { attrsList.add(it) }
                        }
                    } else {
                        val attrStr = item.optString("attributes").trim()
                        if (attrStr.isNotEmpty()) attrsList.add(attrStr)
                    }
                    listOf("color", "colour", "shape", "material", "state", "pose").forEach { k ->
                        item.optString(k).trim().takeIf { it.isNotEmpty() }?.let { attrsList.add(it) }
                    }
                    val bboxArr = item.optJSONArray("bbox")
                    val bbox = if (bboxArr != null) {
                        (0 until bboxArr.length()).map { idx -> bboxArr.optInt(idx) }
                    } else {
                        emptyList()
                    }
                    list.add(
                        MirageObject(
                            name = name,
                            score = score,
                            attributes = attrsList.distinct().joinToString(" · "),
                            id = item.optString("id").trim(),
                            bbox = bbox
                        )
                    )
                }
            }
        }
        return list
    }

    private fun parseTexts(root: JSONObject): List<MirageTextRegion> {
        val out = ArrayList<MirageTextRegion>()
        when (val textAny = root.opt("text")) {
            is String -> textAny.trim().takeIf { it.isNotEmpty() }?.let {
                out.add(MirageTextRegion(text = it))
            }
            is JSONArray -> {
                for (i in 0 until textAny.length()) {
                    when (val entry = textAny.opt(i)) {
                        is String -> entry.trim().takeIf { it.isNotEmpty() }?.let {
                            out.add(MirageTextRegion(text = it))
                        }
                        is JSONObject -> {
                            val txt = entry.optString("text")
                                .ifBlank { entry.optString("value") }
                                .trim()
                            if (txt.isEmpty()) continue
                            val score = when {
                                entry.has("score") -> entry.optDouble("score", 0.0).toFloat()
                                entry.has("confidence") -> entry.optDouble("confidence", 0.0).toFloat()
                                else -> 0f
                            }
                            val bboxArr = entry.optJSONArray("bbox")
                            val bbox = if (bboxArr != null) {
                                (0 until bboxArr.length()).map { idx -> bboxArr.optInt(idx) }
                            } else {
                                emptyList()
                            }
                            out.add(MirageTextRegion(text = txt, score = score, bbox = bbox))
                        }
                    }
                }
            }
        }
        return out
    }

    private fun parseRelations(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            when (val rel = arr.opt(i)) {
                is String -> rel.trim().takeIf { it.isNotEmpty() }?.let { list.add(it) }
                is JSONObject -> {
                    val summary = rel.optString("description").trim()
                    if (summary.isNotEmpty()) {
                        list.add(summary)
                    } else {
                        val subj = rel.optString("subject").ifBlank { rel.optString("source") }.trim()
                        val pred = rel.optString("predicate").ifBlank { rel.optString("relation") }.trim()
                        val obj = rel.optString("object").ifBlank { rel.optString("target") }.trim()
                        val joined = listOf(subj, pred, obj).filter { it.isNotEmpty() }.joinToString(" ")
                        if (joined.isNotEmpty()) list.add(joined)
                    }
                }
            }
        }
        return list
    }

    private fun parseForm(formObj: JSONObject?): Map<String, String> {
        if (formObj == null) return emptyMap()
        val map = LinkedHashMap<String, String>()
        val keys = formObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = formObj.opt(k)?.toString()?.trim().orEmpty()
            if (v.isNotEmpty()) map[k] = v
        }
        return map
    }
}

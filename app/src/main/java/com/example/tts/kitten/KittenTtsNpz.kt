package com.example.tts.kitten

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Minimal reader for NumPy `.npz` files (a ZIP of `.npy` arrays).
 *
 * The pico voice packs (`voices_<lang>.npz`) hold one float32 matrix per
 * voice: 510 rows of 256 style values, addressed by the token count of the
 * chunk being synthesized.
 */
object KittenTtsNpz {

    data class NpyArray(val data: FloatArray, val shape: IntArray)

    fun parseNpz(input: InputStream): Map<String, NpyArray> {
        val result = mutableMapOf<String, NpyArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".npy")) {
                    val name = entry.name.removeSuffix(".npy")
                    result[name] = parseNpy(zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        return result
    }

    fun parseNpy(bytes: ByteArray): NpyArray {
        require(bytes.size > 12) { "archivo .npy vacío" }
        require(bytes[0].toInt() and 0xFF == 0x93) { "no es un archivo .npy" }
        val majorVersion = bytes[6].toInt() and 0xFF
        val headerLen: Int
        val headerStart: Int
        if (majorVersion == 1) {
            headerLen = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
            headerStart = 10
        } else {
            headerLen = (bytes[8].toInt() and 0xFF) or
                ((bytes[9].toInt() and 0xFF) shl 8) or
                ((bytes[10].toInt() and 0xFF) shl 16) or
                ((bytes[11].toInt() and 0xFF) shl 24)
            headerStart = 12
        }
        val headerEnd = headerStart + headerLen
        require(headerEnd in 0..bytes.size) { "cabecera .npy inválida" }
        val header = String(bytes, headerStart, headerLen, Charsets.US_ASCII)

        val dtype = Regex("""'descr'\s*:\s*'([^']+)'""").find(header)?.groupValues?.get(1)?.trim()
            ?: "<f4"
        require(dtype == "<f4" || dtype == "float32") { "tipo .npy no soportado: $dtype" }

        val shapeText = Regex("""'shape'\s*:\s*\(([^)]*)\)""").find(header)?.groupValues?.get(1) ?: ""
        val shape = shapeText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
            .toIntArray()

        val total = if (shape.isEmpty()) 1 else shape.fold(1) { acc, v -> acc * v }
        val buffer = ByteBuffer.wrap(bytes, headerEnd, bytes.size - headerEnd)
            .order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(total)
        for (i in 0 until total) floats[i] = buffer.getFloat()
        return NpyArray(floats, shape)
    }
}

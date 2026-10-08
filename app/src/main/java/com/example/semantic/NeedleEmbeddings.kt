package com.example.semantic

import android.content.Context
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.data.local.AppDatabase
import com.example.data.model.EntryEmbedding
import com.example.data.model.EntryWithRelations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TRUE on-device semantic embeddings with the local Needle 3 model —
 * "squeeze the model to the max" part 1.
 *
 * Every journal entry gets a vector (needle_embed) stored in Room; search,
 * related-entries, duplicate detection and the assistant's RAG memory are
 * plain cosine similarity against those vectors — no server, no API key,
 * works offline. When the model is not downloaded everything degrades
 * gracefully to the lexical engine.
 */
object NeedleEmbeddings {

    /** True when the native engine exists AND needle3.cact is on disk. */
    fun isAvailable(context: Context): Boolean =
        NeedleRuntime.isSupported() && NeedleModelManager.isNeedleDownloaded(context)

    /** Loads the model if needed, then embeds one text. Null when unusable. */
    suspend fun embed(context: Context, text: String): FloatArray? {
        if (!isAvailable(context)) return null
        if (!NeedleRuntime.isTextModelLoaded()) {
            NeedleModelManager.ensureLoaded(context)
        }
        return NeedleRuntime.embedText(text)
    }

    // ------------------------------------------------------------------
    // Index maintenance
    // ------------------------------------------------------------------

    /** (Re)embeds one entry; returns true when the vector was stored. */
    suspend fun upsertEntry(context: Context, entryId: Long, title: String, body: String): Boolean =
        withContext(Dispatchers.IO) {
            val text = (title.trim() + "\n" + body.trim()).trim().take(1600)
            if (text.isBlank()) return@withContext false
            val vector = embed(context, text) ?: return@withContext false
            try {
                AppDatabase.getInstance(context.applicationContext).entryEmbeddingDao().upsert(
                    EntryEmbedding(
                        entryId = entryId,
                        dim = vector.size,
                        updatedAt = System.currentTimeMillis(),
                        vector = serialize(vector)
                    )
                )
                true
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Backfills missing/stale vectors for the given entries (bounded, so a
     * search never spends minutes embedding a huge journal). Returns how
     * many vectors were written.
     */
    suspend fun ensureIndexed(
        context: Context,
        entries: List<EntryWithRelations>,
        maxNew: Int = 40
    ): Int = withContext(Dispatchers.IO) {
        if (!isAvailable(context) || entries.isEmpty()) return@withContext 0
        val dao = AppDatabase.getInstance(context.applicationContext).entryEmbeddingDao()
        val stored = try {
            dao.all().associateBy { it.entryId }
        } catch (_: Exception) {
            return@withContext 0
        }
        var written = 0
        for (e in entries) {
            if (written >= maxNew) break
            val entry = e.entry
            val current = stored[entry.id]
            val stale = current == null || current.updatedAt < (entry.updatedAt ?: entry.createdAt)
            if (stale) {
                if (upsertEntry(context, entry.id, entry.title, entry.body)) written++
            }
        }
        written
    }

    // ------------------------------------------------------------------
    // Similarity
    // ------------------------------------------------------------------

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na < 1e-8f || nb < 1e-8f) return 0f
        return (dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))).coerceIn(-1f, 1f)
    }

    /**
     * Vector search over the journal: embeds the query and ranks the given
     * entries by cosine similarity. Returns entryId -> similarity, best
     * first, at most [topK].
     */
    suspend fun search(
        context: Context,
        query: String,
        entries: List<EntryWithRelations>,
        topK: Int = 12
    ): List<Pair<Long, Float>> = withContext(Dispatchers.IO) {
        if (query.isBlank() || entries.isEmpty()) return@withContext emptyList()
        val queryVec = embed(context, query) ?: return@withContext emptyList()
        val dao = AppDatabase.getInstance(context.applicationContext).entryEmbeddingDao()
        val stored = try {
            dao.all().associateBy { it.entryId }
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        val scored = entries.mapNotNull { e ->
            val row = stored[e.entry.id] ?: return@mapNotNull null
            val vec = deserialize(row.vector, row.dim) ?: return@mapNotNull null
            e.entry.id to cosine(queryVec, vec)
        }
        scored.sortedByDescending { it.second }.take(topK)
    }

    /** Most similar entries to [entryId] — the "related" card + duplicates. */
    suspend fun relatedEntries(
        context: Context,
        entryId: Long,
        entries: List<EntryWithRelations>,
        topK: Int = 3
    ): List<Pair<Long, Float>> = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext emptyList()
        val dao = AppDatabase.getInstance(context.applicationContext).entryEmbeddingDao()
        val base = try {
            dao.forEntry(entryId)
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()
        val baseVec = deserialize(base.vector, base.dim) ?: return@withContext emptyList()
        val stored = try {
            dao.all().associateBy { it.entryId }
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        val scored = entries.mapNotNull { e ->
            if (e.entry.id == entryId) return@mapNotNull null
            val row = stored[e.entry.id] ?: return@mapNotNull null
            val vec = deserialize(row.vector, row.dim) ?: return@mapNotNull null
            e.entry.id to cosine(baseVec, vec)
        }
        scored.sortedByDescending { it.second }.take(topK)
    }

    /**
     * RAG fragments for the assistant: top-k entries for a query as short
     * "title — excerpt" strings, ready to hand to Needle's consultar_diario.
     */
    suspend fun ragFragments(
        context: Context,
        query: String,
        entries: List<EntryWithRelations>,
        k: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        val hits = search(context, query, entries, topK = k)
        val byId = entries.associateBy { it.entry.id }
        hits.mapNotNull { (id, _) ->
            val e = byId[id] ?: return@mapNotNull null
            val title = e.entry.title.ifBlank { "(sin título)" }
            val excerpt = e.entry.body.replace(Regex("[#*_`>]"), " ")
                .replace(Regex("\\s+"), " ").trim().take(220)
            "«$title» — $excerpt"
        }
    }

    // ------------------------------------------------------------------
    // Serialization (little-endian float32)
    // ------------------------------------------------------------------

    private fun serialize(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun deserialize(bytes: ByteArray, dim: Int): FloatArray? {
        if (bytes.size < dim * 4 || dim <= 0) return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(dim)
            for (i in 0 until dim) out[i] = buffer.float
            out
        } catch (_: Exception) {
            null
        }
    }
}

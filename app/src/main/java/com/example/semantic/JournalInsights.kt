package com.example.semantic

import com.example.data.model.EntryWithRelations
import com.example.data.model.GraphNodeType
import com.example.data.model.KnowledgeGraphData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure-Kotlin on-device insights mined across journal entries and the
 * knowledge graph. Deterministic, zero-allocation-heavy, and testable on JVM.
 */
object JournalInsights {

    data class WritingStats(
        val totalNotes: Int,
        val notesLast7Days: Int,
        val streakDays: Int,
        val dominantMood: String?,
        val moodCounts: List<Pair<String, Int>>,
        val topTags: List<Pair<String, Int>>,
        val summaryLine: String
    )

    data class EntityHub(
        val rawId: Long,
        val label: String,
        val type: GraphNodeType,
        val connections: Int
    )

    data class GraphSummary(
        val people: List<EntityHub>,
        val places: List<EntityHub>,
        val topics: List<EntityHub>,
        val totalLinks: Int
    )

    fun computeWritingStats(
        entries: List<EntryWithRelations>,
        nowMillis: Long = System.currentTimeMillis()
    ): WritingStats {
        if (entries.isEmpty()) {
            return WritingStats(
                totalNotes = 0,
                notesLast7Days = 0,
                streakDays = 0,
                dominantMood = null,
                moodCounts = emptyList(),
                topTags = emptyList(),
                summaryLine = "Aún no hay notas escritas; crea la primera cuando quieras."
            )
        }

        val weekCutoff = nowMillis - 7L * 24L * 60L * 60L * 1000L
        val notesLast7Days = entries.count { it.entry.journalDate >= weekCutoff }

        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayKeys = entries.map { dayFormat.format(Date(it.entry.journalDate)) }.toSet()

        val streakDays = computeDayStreak(dayKeys, nowMillis, dayFormat)

        val moodCounts = entries
            .mapNotNull { it.entry.mood?.trim()?.takeIf { m -> m.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val topTags = entries
            .flatMap { it.tags }
            .mapNotNull { it.name.trim().takeIf { n -> n.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        val dominantMood = moodCounts.firstOrNull()?.first

        val parts = mutableListOf<String>()
        if (streakDays >= 2) {
            parts.add("Racha de $streakDays días")
        }
        if (notesLast7Days > 0) {
            parts.add(if (notesLast7Days == 1) "1 nota esta semana" else "$notesLast7Days notas esta semana")
        } else {
            parts.add("${entries.size} memorias guardadas")
        }
        dominantMood?.let { parts.add("Ánimo: $it") }

        return WritingStats(
            totalNotes = entries.size,
            notesLast7Days = notesLast7Days,
            streakDays = streakDays,
            dominantMood = dominantMood,
            moodCounts = moodCounts,
            topTags = topTags,
            summaryLine = parts.joinToString(" · ")
        )
    }

    private fun computeDayStreak(
        dayKeys: Set<String>,
        nowMillis: Long,
        dayFormat: SimpleDateFormat
    ): Int {
        if (dayKeys.isEmpty()) return 0
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayKey = dayFormat.format(cal.time)
        // If user hasn't written yet today, allow streak anchored on yesterday.
        if (todayKey !in dayKeys) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (streak < 365) {
            val key = dayFormat.format(cal.time)
            if (key in dayKeys) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    fun summarizeGraph(graphData: KnowledgeGraphData): GraphSummary {
        if (graphData.nodes.isEmpty()) {
            return GraphSummary(emptyList(), emptyList(), emptyList(), 0)
        }

        val degree = HashMap<String, Int>(graphData.nodes.size)
        graphData.edges.forEach { edge ->
            degree[edge.sourceId] = (degree[edge.sourceId] ?: 0) + 1
            degree[edge.targetId] = (degree[edge.targetId] ?: 0) + 1
        }

        val hubs = graphData.nodes
            .filter { it.type != GraphNodeType.ENTRY && it.rawId > 0L }
            .map { node ->
                EntityHub(
                    rawId = node.rawId,
                    label = node.label,
                    type = node.type,
                    connections = degree[node.id] ?: 1
                )
            }
            .sortedWith(compareByDescending<EntityHub> { it.connections }.thenBy { it.label })

        return GraphSummary(
            people = hubs.filter { it.type == GraphNodeType.PERSON }.take(6),
            places = hubs.filter { it.type == GraphNodeType.PLACE || it.type == GraphNodeType.EVENT }.take(6),
            topics = hubs.filter {
                it.type == GraphNodeType.TOPIC ||
                    it.type == GraphNodeType.TAG ||
                    it.type == GraphNodeType.PROJECT ||
                    it.type == GraphNodeType.ORGANIZATION
            }.take(6),
            totalLinks = graphData.edges.size
        )
    }
}

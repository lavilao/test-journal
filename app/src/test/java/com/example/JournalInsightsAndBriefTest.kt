package com.example

import com.example.data.model.EntryWithRelations
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.data.model.JournalEntry
import com.example.data.model.KnowledgeGraphData
import com.example.data.model.Tag
import com.example.habit.HabitMiners
import com.example.semantic.JournalInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalInsightsAndBriefTest {

    @Test
    fun computeWritingStats_emptyListReturnsGracefulFallback() {
        val stats = JournalInsights.computeWritingStats(emptyList(), nowMillis = 1_700_000_000_000L)
        assertEquals(0, stats.totalNotes)
        assertEquals(0, stats.streakDays)
        assertNull(stats.dominantMood)
        assertTrue(stats.summaryLine.contains("primera", ignoreCase = true))
    }

    @Test
    fun computeWritingStats_calculatesStreakDominantMoodAndTags() {
        val dayMs = 24L * 60L * 60L * 1000L
        val now = 1_700_000_000_000L
        val entries = listOf(
            EntryWithRelations(
                entry = JournalEntry(id = 1L, title = "Día 3", body = "Hoy", journalDate = now, mood = "💭 Thoughtful"),
                tags = listOf(Tag(id = 1L, name = "trabajo", normalizedName = "trabajo"))
            ),
            EntryWithRelations(
                entry = JournalEntry(id = 2L, title = "Día 2", body = "Ayer", journalDate = now - dayMs, mood = "💭 Thoughtful"),
                tags = listOf(
                    Tag(id = 1L, name = "trabajo", normalizedName = "trabajo"),
                    Tag(id = 2L, name = "ideas", normalizedName = "ideas")
                )
            ),
            EntryWithRelations(
                entry = JournalEntry(id = 3L, title = "Día 1", body = "Anteayer", journalDate = now - 2 * dayMs, mood = "😊 Joyful"),
                tags = listOf(Tag(id = 2L, name = "ideas", normalizedName = "ideas"))
            )
        )

        val stats = JournalInsights.computeWritingStats(entries, nowMillis = now)

        assertEquals(3, stats.totalNotes)
        assertEquals(3, stats.notesLast7Days)
        assertEquals(3, stats.streakDays)
        assertEquals("💭 Thoughtful", stats.dominantMood)
        assertEquals("Racha de 3 días · 3 notas esta semana · Ánimo: 💭 Thoughtful", stats.summaryLine)
    }

    @Test
    fun summarizeGraph_groupsAndRanksEntitiesByDegree() {
        val nodes = listOf(
            GraphNode(id = "e1", rawId = 1L, label = "Nota 1", type = GraphNodeType.ENTRY),
            GraphNode(id = "e2", rawId = 2L, label = "Nota 2", type = GraphNodeType.ENTRY),
            GraphNode(id = "p1", rawId = 10L, label = "Sofía", type = GraphNodeType.PERSON),
            GraphNode(id = "p2", rawId = 11L, label = "Carlos", type = GraphNodeType.PERSON),
            GraphNode(id = "l1", rawId = 20L, label = "Madrid", type = GraphNodeType.PLACE),
            GraphNode(id = "t1", rawId = 30L, label = "IA local", type = GraphNodeType.TOPIC)
        )
        val edges = listOf(
            GraphEdge("e1", "p1"),
            GraphEdge("e2", "p1"),
            GraphEdge("e1", "p2"),
            GraphEdge("e2", "l1"),
            GraphEdge("e1", "t1")
        )

        val summary = JournalInsights.summarizeGraph(KnowledgeGraphData(nodes, edges))

        assertEquals(5, summary.totalLinks)
        assertEquals("Sofía", summary.people.first().label)
        assertEquals(2, summary.people.first().connections)
        assertEquals("Madrid", summary.places.first().label)
        assertEquals("IA local", summary.topics.first().label)
    }

    @Test
    fun templateBrief_includesWritingSummaryAndAgendaWhenPresent() {
        val digest = HabitMiners.HabitDigest(
            daysObserved = 4,
            eventCount = 120,
            sleep = null,
            apps = null,
            places = null,
            battery = null,
            attention = null,
            notifications = null,
            rhythmChange = null,
            steps = null
        )

        val brief = HabitMiners.templateBrief(
            digest = digest,
            writingSummary = "Racha de 3 días · 4 notas esta semana",
            upcomingEventsCount = 2
        )

        assertTrue(brief.contains("2 eventos próximos"))
        assertTrue(brief.contains("Racha de 3 días"))
    }
}

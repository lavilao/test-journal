package com.example

import com.example.data.model.EntityType
import com.example.data.model.JournalEntry
import com.example.data.model.Tag
import com.example.semantic.MindForgerSemanticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testTokenizationAndStopwords() {
        val text = "Met with Sarah at the coffee shop today and talked about the project"
        val tokens = MindForgerSemanticEngine.tokenize(text)
        assertTrue(tokens.contains("coffee"))
        assertTrue(tokens.contains("project"))
        // Common stop words must be filtered
        assertTrue(!tokens.contains("the"))
        assertTrue(!tokens.contains("with"))
        assertTrue(!tokens.contains("and"))
    }

    @Test
    fun testEntityExtraction() {
        val title = "Meeting with Sarah"
        val body = "Visited Starbucks and talked with Sarah about the new project. We also checked https://android.com"
        val entities = MindForgerSemanticEngine.extractEntities(title, body)

        val people = entities.filter { it.type == EntityType.PERSON }
        val places = entities.filter { it.type == EntityType.PLACE }
        val urls = entities.filter { it.type == EntityType.URL }

        assertTrue("Should detect Sarah as PERSON", people.any { it.displayName.equals("Sarah", ignoreCase = true) })
        assertTrue("Should detect Starbucks as PLACE", places.any { it.displayName.equals("Starbucks", ignoreCase = true) })
        assertTrue("Should detect url", urls.any { it.displayName.contains("android.com") })
    }

    @Test
    fun testContentHash() {
        val hash1 = MindForgerSemanticEngine.computeContentHash("Title", "Body")
        val hash2 = MindForgerSemanticEngine.computeContentHash("Title", "Body")
        val hash3 = MindForgerSemanticEngine.computeContentHash("Different", "Body")

        assertEquals(hash1, hash2)
        assertTrue(hash1 != hash3)
    }

    @Test
    fun testSemanticRelationshipAndExplainability() {
        val entry1 = JournalEntry(
            id = 1L,
            title = "Coffee with Sarah",
            body = "Met with Sarah at Starbucks to discuss on-device ML Kit architecture."
        )
        val entry2 = JournalEntry(
            id = 2L,
            title = "Researching ML Kit",
            body = "Sarah recommended testing offline models for translation and entity extraction."
        )

        val entities1 = MindForgerSemanticEngine.extractEntities(entry1.title, entry1.body)
        val entities2 = MindForgerSemanticEngine.extractEntities(entry2.title, entry2.body)

        val tags1 = listOf(Tag(name = "mlkit", normalizedName = "mlkit"))
        val tags2 = listOf(Tag(name = "mlkit", normalizedName = "mlkit"))

        val rel = MindForgerSemanticEngine.computeRelationship(
            source = entry1,
            target = entry2,
            sourceEntities = entities1,
            targetEntities = entities2,
            sourceTags = tags1,
            targetTags = tags2
        )

        assertNotNull("Should discover semantic connection between entries sharing Sarah and ML Kit", rel)
        assertTrue("Score should be significant", rel!!.score >= 0.28f)
        assertTrue("Explanation should mention Sarah or shared context", rel.explanation.contains("Sarah", ignoreCase = true) || rel.explanation.contains("match", ignoreCase = true))
    }

    @Test
    fun testV2TemplatesExistAndPopulated() {
        val templates = com.example.data.model.JournalTemplate.ALL_TEMPLATES
        assertTrue("Templates count should be at least 10", templates.size >= 10)
        val daily = templates.find { it.id == "daily_reflection" }
        assertNotNull("Daily template should exist", daily)
        assertTrue("Daily template has tags", daily!!.defaultTags.isNotEmpty())
        val travel = templates.find { it.id == "travel_trip" }
        assertNotNull("Travel template should exist", travel)
    }

    @Test
    fun testVoiceTranscriptSemanticParticipation() {
        val transcript = "Met Alex at Tokyo station to review Project Apollo architecture"
        val tokens = MindForgerSemanticEngine.tokenize(transcript)
        assertTrue("Should extract key nouns", tokens.contains("alex") || tokens.contains("tokyo") || tokens.contains("apollo"))
        val entities = MindForgerSemanticEngine.extractEntities("Voice Note", transcript)
        assertTrue("Should detect at least one entity from voice note", entities.isNotEmpty())
    }

    @Test
    fun testMultiPageContentHash() {
        val page1 = "Day 1 in Kyoto exploring temples"
        val page2 = "Day 2 in Arashiyama bamboo forest"
        val unified1 = "$page1\n\n$page2"
        val unified2 = "$page1\n\n$page2"
        val unified3 = "$page1\n\nDay 2 altered content"

        val hash1 = MindForgerSemanticEngine.computeContentHash("Trip to Kyoto", unified1)
        val hash2 = MindForgerSemanticEngine.computeContentHash("Trip to Kyoto", unified2)
        val hash3 = MindForgerSemanticEngine.computeContentHash("Trip to Kyoto", unified3)

        assertEquals("Equal multi-page content should yield equal hashes", hash1, hash2)
        assertTrue("Altered page should yield different hash", hash1 != hash3)
    }
}

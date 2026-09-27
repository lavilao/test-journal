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
}

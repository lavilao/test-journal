package com.example

import com.example.ai.needle.WhistleKeywordBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhistleKeywordBuilderTest {

    @Test
    fun mergeChunkTranscripts_handlesEmptyInputs() {
        assertEquals("hola mundo", WhistleKeywordBuilder.mergeChunkTranscripts("", "hola mundo"))
        assertEquals("hola mundo", WhistleKeywordBuilder.mergeChunkTranscripts("hola mundo", ""))
        assertEquals("", WhistleKeywordBuilder.mergeChunkTranscripts("  ", "   "))
    }

    @Test
    fun mergeChunkTranscripts_deduplicatesSingleAndMultiWordOverlap() {
        val mergedSingle = WhistleKeywordBuilder.mergeChunkTranscripts(
            "Hoy caminé por el parque",
            "parque y luego tomé café."
        )
        assertEquals("Hoy caminé por el parque y luego tomé café.", mergedSingle)

        val mergedMulti = WhistleKeywordBuilder.mergeChunkTranscripts(
            "Hablamos con Sarah sobre el diseño de",
            "Sarah, sobre el diseño de la memoria semántica."
        )
        assertEquals("Hablamos con Sarah sobre el diseño de la memoria semántica.", mergedMulti)
    }

    @Test
    fun mergeChunkTranscripts_handlesCaseAndPunctuationInsensitiveOverlap() {
        val merged = WhistleKeywordBuilder.mergeChunkTranscripts(
            "Visitamos Blue Bottle Café,",
            "blue bottle cafe antes de volver a casa"
        )
        assertEquals("Visitamos Blue Bottle Café, antes de volver a casa", merged)
    }

    @Test
    fun mergeChunkTranscripts_joinsCleanlyWhenNoOverlap() {
        val merged = WhistleKeywordBuilder.mergeChunkTranscripts(
            "Primera parte de la nota.",
            "Segunda parte continua."
        )
        assertEquals("Primera parte de la nota. Segunda parte continua.", merged)
    }

    @Test
    fun formatKeywordList_deduplicatesAndSanitizes() {
        val result = WhistleKeywordBuilder.formatKeywordList(
            listOf(
                "Mnemosyne",
                "mnemosyne",
                "a",
                "linea\ncon salto",
                "Blue Bottle",
                "x".repeat(60)
            ),
            limit = 4
        )
        assertEquals("Mnemosyne\nlinea con salto\nBlue Bottle", result)
    }

    @Test
    fun formatKeywordList_respectsLimitAndReturnsNullWhenEmpty() {
        assertNull(WhistleKeywordBuilder.formatKeywordList(listOf("", "x")))
        val limited = WhistleKeywordBuilder.formatKeywordList(
            listOf("Uno", "Dos", "Tres", "Cuatro"),
            limit = 2
        )
        assertEquals("Uno\nDos", limited)
    }
}

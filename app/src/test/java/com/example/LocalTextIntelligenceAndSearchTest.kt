package com.example

import com.example.data.model.AudioRecordItem
import com.example.data.model.EntryWithRelations
import com.example.data.model.JournalEntry
import com.example.data.model.MediaItem
import com.example.semantic.LocalTextIntelligence
import com.example.semantic.SemanticSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTextIntelligenceAndSearchTest {

    @Test
    fun summarizeArticleExtractive_picksUpToThreeKeyBulletsAndReadingTime() {
        val body = """
            La computación local en dispositivos móviles está creciendo un 45% anual gracias a motores ligeros como Cactus y modelos cuantizados.
            Esta arquitectura permite ejecutar transcripción de voz, visión artificial y búsqueda semántica sin enviar datos privados a servidores externos.
            Además, reduce la latencia por debajo de los 80 milisegundos incluso en modo avión durante viajes largos.
            Muchos usuarios prefieren aplicaciones rápidas con privacidad total desde el primer minuto de uso.
            El consumo energético también mejora porque el procesamiento incremental evita transferir megabytes por red celular cada hora.
        """.trimIndent()

        val summary = LocalTextIntelligence.summarizeArticleExtractive(
            title = "Privacidad y latencia en IA local móvil",
            body = body,
            maxBullets = 3
        )

        assertEquals(3, summary.bullets.size)
        assertTrue(summary.readingMinutes >= 1)
        assertEquals(false, summary.viaNeedle)
        assertTrue(summary.bullets.any { it.contains("Cactus") || it.contains("privacidad") || it.contains("latencia") })
    }

    @Test
    fun extractActionItems_parsesChecklistsProseAndVoiceTranscripts() {
        val body = """
            - [ ] Enviar la factura mensual por correo
            - [x] Revisar copias de seguridad
            Hoy tengo que llamar al dentista antes de las seis.
            PENDIENTE: Comprar café en grano mañana
        """.trimIndent()

        val transcripts = listOf(
            "Recuérdame pagar el seguro del coche en 2 horas y recoger el paquete."
        )

        val items = LocalTextIntelligence.extractActionItems(
            title = "Notas del martes",
            body = body,
            transcripts = transcripts
        )

        assertTrue(items.any { it.title.contains("Enviar la factura", ignoreCase = true) && it.source == "nota" })
        assertTrue(items.none { it.title.contains("copias de seguridad", ignoreCase = true) })
        assertTrue(items.any { it.title.contains("llamar al dentista", ignoreCase = true) })
        assertTrue(items.any { it.title.contains("Comprar café", ignoreCase = true) && it.dueOffsetMinutes == 24 * 60 })
        assertTrue(items.any { it.title.contains("pagar el seguro", ignoreCase = true) && it.source == "voz" && it.dueOffsetMinutes == 120 })
    }

    @Test
    fun suggestTitleIfBlank_returnsConciseFirstClause() {
        val longBody = "# Reunión con el equipo de diseño para revisar el nuevo tema oscuro en Compose.\nDespués veremos los iconos adaptativos."
        val suggestion = LocalTextIntelligence.suggestTitleIfBlank(longBody)
        assertTrue(suggestion.isNotBlank())
        assertTrue(suggestion.length <= 45)
        assertTrue(suggestion.startsWith("Reunión con el equipo"))
    }

    @Test
    fun instantLexicalHits_findsMatchesInsideMirageCaptionsOcrAndVoiceTranscripts() {
        val photoEntry = EntryWithRelations(
            entry = JournalEntry(id = 10L, title = "Paseo de tarde", body = ""),
            mediaItems = listOf(
                MediaItem(
                    id = 1L,
                    entryId = 10L,
                    uri = "file:///photo.jpg",
                    labelsJson = """["bicicleta","casco"]""",
                    ocrText = "PARQUE DEL RETIRO",
                    caption = "Una bicicleta azul apoyada junto a una fuente."
                )
            )
        )
        val voiceEntry = EntryWithRelations(
            entry = JournalEntry(id = 11L, title = "Idea nocturna", body = ""),
            audioRecords = listOf(
                AudioRecordItem(
                    id = 2L,
                    entryId = 11L,
                    filePath = "/tmp/a.m4a",
                    transcript = "Recordar comprar tornillos de acero inoxidable en la ferretería."
                )
            )
        )
        val all = listOf(photoEntry, voiceEntry)

        val visualHits = SemanticSearchEngine.instantLexicalHits("bicicleta", all)
        assertEquals(listOf(10L), visualHits.map { it.entryId })
        assertTrue(visualHits.first().reason.contains("foto"))
        assertTrue(visualHits.first().snippet.contains("bicicleta azul"))

        val ocrHits = SemanticSearchEngine.instantLexicalHits("retiro", all)
        assertEquals(listOf(10L), ocrHits.map { it.entryId })
        assertTrue(ocrHits.first().reason.contains("OCR", ignoreCase = true))

        val voiceHits = SemanticSearchEngine.instantLexicalHits("ferreteria", all)
        assertEquals(listOf(11L), voiceHits.map { it.entryId })
        assertTrue(voiceHits.first().reason.contains("voz"))
    }
}

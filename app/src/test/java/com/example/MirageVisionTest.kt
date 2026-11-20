package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.ai.needle.MirageVision
import com.example.ai.needle.ModelTransferManager
import com.example.ai.needle.NeedleModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MirageVisionTest {

    @Test
    fun resolveModelFromHint_detectsCactTypesByFilenameAndExplicitSlot() {
        assertEquals(
            NeedleModelManager.MIRAGE_FILE,
            ModelTransferManager.resolveModelFromHint(ModelTransferManager.AUTO_MODEL, "mirage (1).cact")
        )
        assertEquals(
            NeedleModelManager.WHISTLE_FILE,
            ModelTransferManager.resolveModelFromHint(ModelTransferManager.AUTO_MODEL, "Whistle_es.cact")
        )
        assertEquals(
            NeedleModelManager.NEEDLE_FILE,
            ModelTransferManager.resolveModelFromHint(ModelTransferManager.AUTO_MODEL, "needle3.cact")
        )
        assertEquals(
            NeedleModelManager.MIRAGE_FILE,
            ModelTransferManager.resolveModelFromHint(NeedleModelManager.MIRAGE_FILE, "custom_name.cact")
        )
        assertNull(
            ModelTransferManager.resolveModelFromHint(ModelTransferManager.AUTO_MODEL, "unknown_file.bin")
        )
    }

    @Test
    fun parseJson_extractsDescriptionObjectsTextAndRelations() {
        val sample = """
            {
              "description": "Una taza roja sobre una mesa de madera junto a una libreta.",
              "objects": [
                {"id": "cup_1", "name": "taza", "score": 0.91, "colour": "roja", "material": "cerámica"},
                {"id": "book_1", "name": "libreta", "score": 0.84, "attributes": ["abierta", "papel"]}
              ],
              "text": [
                {"text": "CAFE CENTRAL", "score": 0.88, "bbox": [10, 20, 200, 60]}
              ],
              "relations": [
                {"subject": "taza", "predicate": "sobre", "object": "mesa"}
              ],
              "ttft_ms": 74.2,
              "decode_tps": 41.5
            }
        """.trimIndent()

        val result = MirageVision.parseJson(sample)
        assertNotNull(result)
        result!!
        assertTrue(result.description.contains("taza roja"))
        assertEquals(2, result.objects.size)
        assertEquals("taza", result.objects[0].name)
        assertEquals("roja · cerámica", result.objects[0].attributes)
        assertEquals("abierta · papel", result.objects[1].attributes)
        assertEquals(listOf("taza", "libreta"), result.mergedLabels())
        assertEquals("CAFE CENTRAL", result.mergedOcrText())
        assertEquals(listOf("taza sobre mesa"), result.relations)
        assertEquals("Una taza roja sobre una mesa de madera junto a una libreta.", result.formattedJournalCaption())
    }

    @Test
    fun parseJson_fallsBackToSceneObjectAndObjectStrings() {
        val sample = """
            {
              "scene": {"setting": "Parque al atardecer", "lighting": "cálida"},
              "objects": ["árbol", "banco", "árbol"],
              "text": "ENTRADA",
              "relations": ["banco junto a árbol"]
            }
        """.trimIndent()

        val result = MirageVision.parseJson(sample)!!
        assertEquals("Parque al atardecer. cálida", result.description)
        assertEquals(listOf("árbol", "banco"), result.mergedLabels())
        assertEquals("ENTRADA", result.mergedOcrText())
        assertEquals(listOf("banco junto a árbol"), result.relations)
    }

    @Test
    fun parseJson_handlesInvalidInputs() {
        assertNull(MirageVision.parseJson(null))
        assertNull(MirageVision.parseJson(""))
        assertNull(MirageVision.parseJson("not json"))
    }

    @Test
    fun bitmapToPackedRgb_packsRgbChannelOrderCorrectly() {
        val bmp = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.rgb(12, 34, 56))
        bmp.setPixel(1, 0, Color.rgb(200, 150, 100))

        val packed = MirageVision.bitmapToPackedRgb(bmp, maxDim = 768)
        assertNotNull(packed)
        val (rgb, w, h) = packed!!
        assertEquals(2, w)
        assertEquals(1, h)
        assertEquals(6, rgb.size)
        assertEquals(12.toByte(), rgb[0])
        assertEquals(34.toByte(), rgb[1])
        assertEquals(56.toByte(), rgb[2])
        assertEquals(200.toByte(), rgb[3])
        assertEquals(150.toByte(), rgb[4])
        assertEquals(100.toByte(), rgb[5])
        bmp.recycle()
    }
}

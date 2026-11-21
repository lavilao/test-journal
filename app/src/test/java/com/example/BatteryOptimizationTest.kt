package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.speech.hotword.WhistleVadGate
import com.example.wallpaper.SunCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryOptimizationTest {

    @Test
    fun whistleVadGate_suppressesSilenceAndPassesSpeech() {
        val silence = ShortArray(1600) { 12 }
        val voice = ShortArray(1600) { if (it % 2 == 0) 1800 else -1800 }

        val quietRms = WhistleVadGate.computeRms(silence)
        val loudRms = WhistleVadGate.computeRms(voice)

        assertTrue("Quiet room RMS should be below threshold", quietRms < WhistleVadGate.DEFAULT_RMS_THRESHOLD)
        assertTrue("Voice RMS should exceed threshold", loudRms > WhistleVadGate.DEFAULT_RMS_THRESHOLD)

        assertFalse(
            WhistleVadGate.shouldProcessChunk(
                rms = quietRms,
                nowMs = 10_000L,
                lastVoiceMs = 0L,
                armed = false
            )
        )
        assertTrue(
            WhistleVadGate.shouldProcessChunk(
                rms = loudRms,
                nowMs = 10_000L,
                lastVoiceMs = 0L,
                armed = false
            )
        )
    }

    @Test
    fun whistleVadGate_respectsHangoverAndArmedOverride() {
        val quietRms = 10f
        val now = 20_000L

        assertTrue(
            "Within hangover window should continue processing",
            WhistleVadGate.shouldProcessChunk(
                rms = quietRms,
                nowMs = now,
                lastVoiceMs = now - 1_500L,
                armed = false
            )
        )
        assertFalse(
            "Expired hangover window must stop inference",
            WhistleVadGate.shouldProcessChunk(
                rms = quietRms,
                nowMs = now,
                lastVoiceMs = now - WhistleVadGate.DEFAULT_HANGOVER_MS - 100L,
                armed = false
            )
        )
        assertTrue(
            "Armed capture mode always processes audio",
            WhistleVadGate.shouldProcessChunk(
                rms = quietRms,
                nowMs = now,
                lastVoiceMs = 0L,
                armed = true
            )
        )
    }

    @Test
    fun sunCycle_defaultsToZeroGpuStaticNotionAndPersistsChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SunCycle.WALLPAPER_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertEquals(SunCycle.AppSurfaceMode.STATIC_NOTION, SunCycle.getSurfaceMode(context))
        assertFalse(SunCycle.isDynamicAtmosphereEnabled(context))

        SunCycle.setSurfaceMode(context, SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER)
        assertEquals(SunCycle.AppSurfaceMode.DYNAMIC_LOW_POWER, SunCycle.getSurfaceMode(context))
        assertTrue(SunCycle.isDynamicAtmosphereEnabled(context))

        SunCycle.setSurfaceMode(context, SunCycle.AppSurfaceMode.CLASSIC_AURORA)
        assertEquals(SunCycle.AppSurfaceMode.CLASSIC_AURORA, SunCycle.getSurfaceMode(context))
        assertFalse(SunCycle.isDynamicAtmosphereEnabled(context))
    }
}

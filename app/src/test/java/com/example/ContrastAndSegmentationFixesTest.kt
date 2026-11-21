package com.example

import com.example.media.SubjectSegmentationManager
import com.example.wallpaper.SunCycle
import com.example.wallpaper.SunCycle.AtmosphereWeather
import com.example.wallpaper.SunCycle.Phase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastAndSegmentationFixesTest {

    @Test
    fun isDarkHeaderBackdrop_darkThemeAlwaysUsesLightInk() {
        for (phase in Phase.entries) {
            for (weather in AtmosphereWeather.entries) {
                assertTrue(
                    "Dark theme ($phase, $weather) should use light header text",
                    SunCycle.isDarkHeaderBackdrop(phase, weather, systemDark = true)
                )
            }
        }
    }

    @Test
    fun isDarkHeaderBackdrop_lightThemeSwitchesToDarkInkOnBrightOvercastFogAndSnow() {
        assertFalse(SunCycle.isDarkHeaderBackdrop(Phase.MORNING, AtmosphereWeather.FOG, systemDark = false))
        assertFalse(SunCycle.isDarkHeaderBackdrop(Phase.MIDDAY, AtmosphereWeather.CLOUDY, systemDark = false))
        assertFalse(SunCycle.isDarkHeaderBackdrop(Phase.AFTERNOON, AtmosphereWeather.SNOW, systemDark = false))

        assertTrue(SunCycle.isDarkHeaderBackdrop(Phase.NIGHT, AtmosphereWeather.CLEAR, systemDark = false))
        assertTrue(SunCycle.isDarkHeaderBackdrop(Phase.MIDDAY, AtmosphereWeather.STORM, systemDark = false))
        assertTrue(SunCycle.isDarkHeaderBackdrop(Phase.MORNING, AtmosphereWeather.CLEAR, systemDark = false))
    }

    @Test
    fun isModelMissingMessage_detectsPlayServicesOptionalModuleMessages() {
        assertTrue(
            SubjectSegmentationManager.isModelMissingMessage(
                "Waiting for the subject segmentation optional module to be downloaded. Please wait."
            )
        )
        assertTrue(
            SubjectSegmentationManager.isModelMissingMessage(
                "Optional module failed to download"
            )
        )
        assertTrue(
            SubjectSegmentationManager.isModelMissingMessage(
                "17: API: SubjectSegmentation.API is not available on this device."
            )
        )
        assertTrue(
            SubjectSegmentationManager.isModelMissingMessage(
                "Model unavailable offline"
            )
        )
        assertFalse(
            SubjectSegmentationManager.isModelMissingMessage(
                "Bitmap decode returned null"
            )
        )
    }
}

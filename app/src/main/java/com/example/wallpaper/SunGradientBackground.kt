package com.example.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * In-app preview/background of the dynamic sun (Samsung mode experiment).
 *
 * Renders the same sky model as [SunWallpaperService] as a Compose Canvas:
 * gradient by time-of-day phase, sun travelling along its arc (moon and
 * stars at night). Re-evaluates every minute while composed — cheap.
 */
@Composable
fun SunGradientBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val coords = remember(now) { SunCycle.lastCoordinates(context) }
    val daylight = remember(now, coords) {
        SunCycle.daylightFor(now, coords?.first, coords?.second)
    }
    val phase = SunCycle.phaseAt(now, daylight)
    val progress = SunCycle.sunProgress(now, daylight)

    val (top, bottom) = when (phase) {
        SunCycle.Phase.NIGHT -> Color(0xFF0A1229) to Color(0xFF1C2B52)
        SunCycle.Phase.DAWN -> Color(0xFF31407A) to Color(0xFFF2A489)
        SunCycle.Phase.MORNING -> Color(0xFF4D7DC9) to Color(0xFFF7D9A8)
        SunCycle.Phase.MIDDAY -> Color(0xFF3E8BE8) to Color(0xFFBEE3F5)
        SunCycle.Phase.AFTERNOON -> Color(0xFF4E8FD6) to Color(0xFFF5C98E)
        SunCycle.Phase.DUSK -> Color(0xFF3A3870) to Color(0xFFE0705F)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(listOf(top, bottom)),
            size = size
        )

        val horizonY = h * 0.78f
        val arcTopY = h * 0.16f
        val angle = Math.PI * progress
        val cx = w * 0.1f + w * 0.8f * progress
        val cy = horizonY - (horizonY - arcTopY) * sin(angle).toFloat()

        if (phase == SunCycle.Phase.NIGHT) {
            drawCircle(color = Color(0xFFE8ECF5), radius = w * 0.045f, center = Offset(cx, cy))
            drawCircle(
                color = Color(0xFFC9D2E3).copy(alpha = 0.9f),
                radius = w * 0.036f,
                center = Offset(cx - w * 0.013f, cy - w * 0.011f)
            )
            val rnd = java.util.Random((now / 3_600_000L))
            repeat(42) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h * 0.62f
                val r = (0.8f + rnd.nextFloat() * 1.6f) * (w / 1080f).coerceIn(0.6f, 1.4f)
                drawCircle(color = Color.White.copy(alpha = 0.8f), radius = r, center = Offset(x, y))
            }
        } else {
            val sunColor = when (phase) {
                SunCycle.Phase.DAWN -> Color(0xFFFFB25E)
                SunCycle.Phase.MORNING -> Color(0xFFFFCE5C)
                SunCycle.Phase.MIDDAY -> Color(0xFFFFF3B0)
                SunCycle.Phase.AFTERNOON -> Color(0xFFFFC24D)
                SunCycle.Phase.DUSK -> Color(0xFFFF8A50)
                SunCycle.Phase.NIGHT -> Color(0xFFE8ECF5)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(sunColor.copy(alpha = 0.45f), sunColor.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = w * 0.16f
                ),
                radius = w * 0.16f,
                center = Offset(cx, cy)
            )
            drawCircle(color = sunColor, radius = w * 0.055f, center = Offset(cx, cy))
        }

        // Horizon haze
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(bottom.copy(alpha = 0f), bottom),
                startY = horizonY - h * 0.06f,
                endY = h
            ),
            topLeft = Offset(0f, horizonY - h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w, h - (horizonY - h * 0.06f))
        )
    }
}

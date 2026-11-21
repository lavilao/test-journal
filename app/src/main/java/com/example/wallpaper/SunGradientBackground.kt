package com.example.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.components.AuroraBackground
import com.example.wallpaper.SunCycle.AppSurfaceMode
import com.example.wallpaper.SunCycle.AtmosphereWeather
import com.example.wallpaper.SunCycle.Phase
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Matte, distraction-free Notion-caliber workspace backdrop: 0% GPU redraw loop.
 */
@Composable
fun StaticNotionWorkspaceBackdrop(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bgTop = if (isDark) Color(0xFF141413) else Color(0xFFFBFAF8)
    val bgBottom = if (isDark) Color(0xFF191918) else Color(0xFFF5F3EF)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    )
}

/**
 * Unified app background: defaults to crisp zero-GPU Notion workspace paper/obsidian
 * (or renders low-power dynamic sky strictly when chosen in Settings and on Home).
 */
@Composable
fun NowBriefAppBackground(
    modifier: Modifier = Modifier,
    weatherCode: Int? = null,
    isHomeScreen: Boolean = true
) {
    val context = LocalContext.current
    val surfaceMode = remember(weatherCode, isHomeScreen) { SunCycle.getSurfaceMode(context) }
    val resolvedCode = weatherCode ?: SunCycle.lastWeatherCode(context)

    when (surfaceMode) {
        AppSurfaceMode.STATIC_NOTION -> StaticNotionWorkspaceBackdrop(modifier = modifier)
        AppSurfaceMode.CLASSIC_AURORA -> AuroraBackground(modifier = modifier)
        AppSurfaceMode.DYNAMIC_LOW_POWER -> {
            if (isHomeScreen) {
                SunGradientBackground(
                    modifier = modifier,
                    weatherCode = resolvedCode,
                    animateParticles = true
                )
            } else {
                // Keep editor / reader / secondary screens calm & 0-FPS idle.
                StaticNotionWorkspaceBackdrop(modifier = modifier)
            }
        }
    }
}

private data class SkyPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glow: Color,
    val cloudTint: Color,
    val sunColor: Color
)

/**
 * Dynamic atmospheric sky rendered with Jetpack Compose Canvas.
 * Energy-optimized: updates clock only while RESUMED every 60s, renders clear/cloudy
 * skies completely statically (0 continuous redraws), and caps active weather particles
 * (rain/snow/storm) to ~12 FPS only while the screen is RESUMED.
 */
@Composable
fun SunGradientBackground(
    modifier: Modifier = Modifier,
    weatherCode: Int? = null,
    overridePhase: Phase? = null,
    overrideWeather: AtmosphereWeather? = null,
    animateParticles: Boolean = true
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = System.currentTimeMillis()
            while (true) {
                delay(60_000L)
                now = System.currentTimeMillis()
            }
        }
    }

    val coords = remember(now) { SunCycle.lastCoordinates(context) }
    val daylight = remember(now, coords) {
        SunCycle.daylightFor(now, coords?.first, coords?.second)
    }
    val phase = overridePhase ?: SunCycle.phaseAt(now, daylight)
    val weather = overrideWeather ?: SunCycle.weatherFromCode(
        weatherCode ?: remember(now) { SunCycle.lastWeatherCode(context) }
    )

    val sunProgress = when (overridePhase) {
        Phase.DAWN -> 0.08f
        Phase.MORNING -> 0.28f
        Phase.MIDDAY -> 0.50f
        Phase.AFTERNOON -> 0.74f
        Phase.DUSK -> 0.92f
        else -> SunCycle.sunProgress(now, daylight)
    }
    val moonProgress = if (overridePhase == Phase.NIGHT) 0.42f else SunCycle.moonProgress(now, daylight)

    val palette = remember(phase, weather, isDark) {
        paletteFor(phase, weather, isDark)
    }

    val needsMotion = animateParticles && (
        weather == AtmosphereWeather.RAIN ||
            weather == AtmosphereWeather.SNOW ||
            weather == AtmosphereWeather.STORM
        )

    // Read only inside Canvas draw scope; static skies incur 0 redraws, precip throttled to ~12fps.
    val timeSec = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycleOwner, needsMotion, phase, weather) {
        if (!needsMotion) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val startMs = System.currentTimeMillis()
            while (true) {
                timeSec.floatValue = (System.currentTimeMillis() - startMs) / 1000f
                delay(80L)
            }
        }
    }

    val stars = remember {
        val rnd = java.util.Random(4207L)
        List(56) {
            StarSpec(
                xFrac = rnd.nextFloat(),
                yFrac = rnd.nextFloat() * 0.56f,
                radiusScale = 0.75f + rnd.nextFloat() * 1.45f,
                twinkleSpeed = 0.9f + rnd.nextFloat() * 2.1f,
                twinkleOffset = rnd.nextFloat() * (2f * PI.toFloat())
            )
        }
    }

    val raindrops = remember {
        val rnd = java.util.Random(8813L)
        List(64) {
            RainDropSpec(
                xFrac = rnd.nextFloat(),
                yOffset = rnd.nextFloat(),
                speed = 0.68f + rnd.nextFloat() * 0.58f,
                lengthFrac = 0.032f + rnd.nextFloat() * 0.036f,
                alpha = 0.16f + rnd.nextFloat() * 0.24f,
                strokeDp = 1.0f + rnd.nextFloat() * 0.9f
            )
        }
    }

    val snowflakes = remember {
        val rnd = java.util.Random(1937L)
        List(48) {
            SnowFlakeSpec(
                xFrac = rnd.nextFloat(),
                yOffset = rnd.nextFloat(),
                speed = 0.06f + rnd.nextFloat() * 0.08f,
                swayAmpFrac = 0.015f + rnd.nextFloat() * 0.025f,
                swayFreq = 0.7f + rnd.nextFloat() * 1.1f,
                radiusDp = 1.6f + rnd.nextFloat() * 2.6f,
                alpha = 0.35f + rnd.nextFloat() * 0.45f,
                phase = rnd.nextFloat() * (2f * PI.toFloat())
            )
        }
    }

    val clouds = remember {
        listOf(
            CloudSpec(baseX = 0.15f, yFrac = 0.13f, widthFrac = 0.56f, heightFrac = 0.15f, speed = 0.012f, alpha = 0.55f),
            CloudSpec(baseX = 0.68f, yFrac = 0.21f, widthFrac = 0.64f, heightFrac = 0.17f, speed = 0.009f, alpha = 0.45f),
            CloudSpec(baseX = 0.42f, yFrac = 0.09f, widthFrac = 0.48f, heightFrac = 0.13f, speed = 0.015f, alpha = 0.40f),
            CloudSpec(baseX = 0.90f, yFrac = 0.28f, widthFrac = 0.52f, heightFrac = 0.14f, speed = 0.007f, alpha = 0.35f)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = timeSec.floatValue

        // 1. Multi-stop vertical sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to palette.top,
                0.48f to palette.mid,
                1.0f to palette.bottom
            ),
            size = size
        )

        // Subtle atmospheric ambient light bloom (keeps liquid-glass depth)
        val bloomX = w * (0.76f + 0.06f * sin(t * 0.18f))
        val bloomY = h * (0.28f + 0.04f * cos(t * 0.14f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.glow, palette.glow.copy(alpha = 0f)),
                center = Offset(bloomX, bloomY),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(bloomX, bloomY)
        )

        // 2. Celestial body along solar / lunar arc
        val horizonY = h * 0.62f
        val arcTopY = h * 0.11f

        val visibilityFactor = when (weather) {
            AtmosphereWeather.CLEAR -> 1.0f
            AtmosphereWeather.PARTLY_CLOUDY -> 0.80f
            AtmosphereWeather.CLOUDY -> 0.35f
            AtmosphereWeather.FOG -> 0.22f
            AtmosphereWeather.RAIN -> 0.20f
            AtmosphereWeather.SNOW -> 0.25f
            AtmosphereWeather.STORM -> 0.10f
        }

        if (phase == Phase.NIGHT) {
            // Twinkling stars
            val scale = (w / 1080f).coerceIn(0.6f, 1.4f)
            val starOpacity = visibilityFactor * if (isDark) 0.92f else 0.82f
            if (starOpacity > 0.05f) {
                stars.forEach { star ->
                    val twinkle = 0.42f + 0.58f * (0.5f + 0.5f * sin(t * star.twinkleSpeed + star.twinkleOffset))
                    drawCircle(
                        color = Color.White.copy(alpha = (starOpacity * twinkle).coerceIn(0f, 1f)),
                        radius = star.radiusScale * scale * 1.4f,
                        center = Offset(star.xFrac * w, star.yFrac * h)
                    )
                }
            }

            // Crescent moon (transparent cutout via offscreen layer so sky shows cleanly through)
            val angle = PI * moonProgress
            val mx = w * 0.14f + w * 0.72f * moonProgress
            val my = horizonY - (horizonY - arcTopY) * sin(angle).toFloat()
            val moonRadius = w * 0.046f
            val moonAlpha = (0.35f + 0.65f * visibilityFactor)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD8E5FF).copy(alpha = 0.28f * moonAlpha),
                        Color.Transparent
                    ),
                    center = Offset(mx, my),
                    radius = moonRadius * 3.4f
                ),
                radius = moonRadius * 3.4f,
                center = Offset(mx, my)
            )
            drawCrescentMoon(
                center = Offset(mx, my),
                radius = moonRadius,
                moonColor = Color(0xFFF0F4FF).copy(alpha = moonAlpha)
            )
        } else {
            val angle = PI * sunProgress
            val sx = w * 0.12f + w * 0.76f * sunProgress
            val sy = horizonY - (horizonY - arcTopY) * sin(angle).toFloat()
            val sunRadius = w * 0.052f
            val haloAlpha = (0.25f + 0.45f * visibilityFactor)
            val breath = 1f + 0.04f * sin(t * 0.6f)

            // Outer warm atmospheric corona
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.sunColor.copy(alpha = haloAlpha),
                        palette.sunColor.copy(alpha = haloAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(sx, sy),
                    radius = w * 0.26f * breath
                ),
                radius = w * 0.26f * breath,
                center = Offset(sx, sy)
            )
            // Core solar disc (attenuated gently when overcast/rainy)
            if (visibilityFactor > 0.15f) {
                drawCircle(
                    color = palette.sunColor.copy(alpha = (0.30f + 0.70f * visibilityFactor).coerceIn(0f, 1f)),
                    radius = sunRadius,
                    center = Offset(sx, sy)
                )
            }
        }

        // 3. Storm subtle cloud lightning flash
        if (weather == AtmosphereWeather.STORM) {
            val cycle = t % 7.6f
            val flashAlpha = when {
                cycle in 1.2f..1.45f -> {
                    val p = (cycle - 1.2f) / 0.25f
                    (sin(p * PI.toFloat()) * 0.24f).coerceAtLeast(0f)
                }
                cycle in 1.52f..1.72f -> {
                    val p = (cycle - 1.52f) / 0.20f
                    (sin(p * PI.toFloat()) * 0.16f).coerceAtLeast(0f)
                }
                else -> 0f
            }
            if (flashAlpha > 0.005f) {
                val fx = w * (0.25f + 0.5f * ((t / 7.6f).toInt() % 3) / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDCEBFF).copy(alpha = flashAlpha), Color.Transparent),
                        center = Offset(fx, h * 0.16f),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(fx, h * 0.16f)
                )
            }
        }

        // 4. Volumetric drifting clouds
        val cloudDensity = when (weather) {
            AtmosphereWeather.CLEAR -> 0f
            AtmosphereWeather.PARTLY_CLOUDY -> 0.55f
            AtmosphereWeather.CLOUDY -> 0.90f
            AtmosphereWeather.FOG -> 0.50f
            AtmosphereWeather.RAIN -> 0.95f
            AtmosphereWeather.SNOW -> 0.85f
            AtmosphereWeather.STORM -> 1.0f
        }
        if (cloudDensity > 0f) {
            clouds.forEach { cloud ->
                val drift = ((cloud.baseX + t * cloud.speed) % 1.4f) - 0.2f
                val cx = drift * w
                val cy = cloud.yFrac * h
                val cw = cloud.widthFrac * w
                val ch = cloud.heightFrac * h
                // Keep clouds behind the top header soft so hero text remains crisp.
                val topDamping = if (cloud.yFrac < 0.28f) 0.72f else 1f
                val alpha = (cloud.alpha * cloudDensity * topDamping).coerceIn(0f, 0.76f)
                drawSoftCloudCluster(
                    center = Offset(cx, cy),
                    width = cw,
                    height = ch,
                    color = palette.cloudTint.copy(alpha = alpha)
                )
            }
        }

        // 5. Weather particle layers
        when (weather) {
            AtmosphereWeather.FOG -> {
                repeat(4) { i ->
                    val drift = 0.06f * sin(t * (0.14f + i * 0.03f) + i * 1.4f)
                    val bandY = h * (0.22f + i * 0.17f)
                    val bandCenter = Offset(w * (0.5f + drift), bandY)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.cloudTint.copy(alpha = if (isDark) 0.20f else 0.34f),
                                Color.Transparent
                            ),
                            center = bandCenter,
                            radius = w * 0.72f
                        ),
                        radius = w * 0.72f,
                        center = bandCenter
                    )
                }
            }
            AtmosphereWeather.RAIN, AtmosphereWeather.STORM -> {
                val count = if (weather == AtmosphereWeather.STORM) raindrops.size else 44
                val speedMult = if (weather == AtmosphereWeather.STORM) 1.35f else 1.0f
                val slant = if (weather == AtmosphereWeather.STORM) -w * 0.045f else -w * 0.022f
                for (i in 0 until count) {
                    val drop = raindrops[i]
                    val yProg = (drop.yOffset + t * drop.speed * speedMult) % 1.15f - 0.10f
                    val startX = drop.xFrac * w + (yProg * slant * 0.6f)
                    val startY = yProg * h
                    val len = drop.lengthFrac * h
                    drawLine(
                        color = Color(0xFFD8EBFF).copy(alpha = drop.alpha),
                        start = Offset(startX, startY),
                        end = Offset(startX + slant * 0.28f, startY + len),
                        strokeWidth = drop.strokeDp.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            AtmosphereWeather.SNOW -> {
                snowflakes.forEach { flake ->
                    val yProg = (flake.yOffset + t * flake.speed) % 1.08f - 0.04f
                    val sway = sin(t * flake.swayFreq + flake.phase) * flake.swayAmpFrac * w
                    val fx = ((flake.xFrac * w + sway) % w + w) % w
                    val fy = yProg * h
                    val r = flake.radiusDp.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = flake.alpha),
                        radius = r,
                        center = Offset(fx, fy)
                    )
                }
            }
            else -> Unit
        }

        // 6. Calm horizon blend so liquid-glass cards & bottom capsule remain crisp
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, palette.bottom.copy(alpha = 0.82f)),
                startY = h * 0.56f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.56f),
            size = Size(w, h * 0.44f)
        )

        // 7. Adaptive header-contrast protective scrim so clock, greeting, and icons stay readable
        val darkHeader = SunCycle.isDarkHeaderBackdrop(phase, weather, isDark)
        val scrimColor = if (darkHeader) Color(0xFF061226) else Color(0xFFF3F6FA)
        val scrimPeak = if (darkHeader) 0.40f else 0.44f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    scrimColor.copy(alpha = scrimPeak),
                    scrimColor.copy(alpha = scrimPeak * 0.48f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.30f
            ),
            topLeft = Offset.Zero,
            size = Size(w, h * 0.30f)
        )
    }
}

private fun DrawScope.drawCrescentMoon(
    center: Offset,
    radius: Float,
    moonColor: Color
) {
    drawIntoCanvas { canvas ->
        val layerBounds = Rect(
            center.x - radius * 1.4f,
            center.y - radius * 1.4f,
            center.x + radius * 1.4f,
            center.y + radius * 1.4f
        )
        canvas.saveLayer(layerBounds, Paint())
        drawCircle(
            color = moonColor,
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.Transparent,
            radius = radius * 0.82f,
            center = Offset(center.x + radius * 0.36f, center.y - radius * 0.22f),
            blendMode = BlendMode.Clear
        )
        canvas.restore()
    }
}

private fun DrawScope.drawSoftCloudCluster(
    center: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    val puffs = listOf(
        Triple(-0.28f, 0.10f, 0.42f),
        Triple(-0.08f, -0.14f, 0.52f),
        Triple(0.16f, -0.06f, 0.46f),
        Triple(0.32f, 0.14f, 0.36f)
    )
    puffs.forEach { (ox, oy, radFrac) ->
        val pCenter = Offset(center.x + ox * width, center.y + oy * height)
        val r = width * radFrac * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.45f), Color.Transparent),
                center = pCenter,
                radius = r
            ),
            radius = r,
            center = pCenter
        )
    }
}

private fun paletteFor(
    phase: Phase,
    weather: AtmosphereWeather,
    isDark: Boolean
): SkyPalette {
    // Base clear-sky palette for time of day + light/dark mode
    val clear = if (isDark) {
        when (phase) {
            Phase.NIGHT -> SkyPalette(
                top = Color(0xFF030816),
                mid = Color(0xFF0A1733),
                bottom = Color(0xFF0E203F),
                glow = Color(0x334B83F2),
                cloudTint = Color(0xFF283C66),
                sunColor = Color(0xFFE8ECF5)
            )
            Phase.DAWN -> SkyPalette(
                top = Color(0xFF151C3D),
                mid = Color(0xFF3E355E),
                bottom = Color(0xFF311F33),
                glow = Color(0x44EB8B6B),
                cloudTint = Color(0xFF6B536E),
                sunColor = Color(0xFFFFB25E)
            )
            Phase.MORNING -> SkyPalette(
                top = Color(0xFF0C2347),
                mid = Color(0xFF1B3F6E),
                bottom = Color(0xFF0E243D),
                glow = Color(0x44F5B873),
                cloudTint = Color(0xFF4D6B94),
                sunColor = Color(0xFFFFCE5C)
            )
            Phase.MIDDAY -> SkyPalette(
                top = Color(0xFF09284E),
                mid = Color(0xFF164778),
                bottom = Color(0xFF0A1F38),
                glow = Color(0x4460B8FF),
                cloudTint = Color(0xFF4B75A1),
                sunColor = Color(0xFFFFF0A8)
            )
            Phase.AFTERNOON -> SkyPalette(
                top = Color(0xFF11254A),
                mid = Color(0xFF2C456E),
                bottom = Color(0xFF1A243B),
                glow = Color(0x44F0A85B),
                cloudTint = Color(0xFF626E8C),
                sunColor = Color(0xFFFFC24D)
            )
            Phase.DUSK -> SkyPalette(
                top = Color(0xFF171638),
                mid = Color(0xFF4A2B52),
                bottom = Color(0xFF26182C),
                glow = Color(0x44E66B55),
                cloudTint = Color(0xFF6E4B69),
                sunColor = Color(0xFFFF8A50)
            )
        }
    } else {
        when (phase) {
            Phase.NIGHT -> SkyPalette(
                top = Color(0xFF09122B),
                mid = Color(0xFF1A2D57),
                bottom = Color(0xFF364970),
                glow = Color(0x445D8EF7),
                cloudTint = Color(0xFF465C8A),
                sunColor = Color(0xFFE8ECF5)
            )
            Phase.DAWN -> SkyPalette(
                top = Color(0xFF2B3A70),
                mid = Color(0xFF9E6B87),
                bottom = Color(0xFFF6C2A8),
                glow = Color(0x55FFB187),
                cloudTint = Color(0xFFF7CBB8),
                sunColor = Color(0xFFFFB25E)
            )
            Phase.MORNING -> SkyPalette(
                top = Color(0xFF235CB0),
                mid = Color(0xFF6BA6DF),
                bottom = Color(0xFFECDBC4),
                glow = Color(0x44FFD699),
                cloudTint = Color(0xFFFFFFFF),
                sunColor = Color(0xFFFFD369)
            )
            Phase.MIDDAY -> SkyPalette(
                top = Color(0xFF1B64C2),
                mid = Color(0xFF60ACE9),
                bottom = Color(0xFFDCEBF7),
                glow = Color(0x389CE0FF),
                cloudTint = Color(0xFFFFFFFF),
                sunColor = Color(0xFFFFF3B0)
            )
            Phase.AFTERNOON -> SkyPalette(
                top = Color(0xFF2460B2),
                mid = Color(0xFF7CA7D4),
                bottom = Color(0xFFEDD3B4),
                glow = Color(0x44FFC178),
                cloudTint = Color(0xFFFFF3E6),
                sunColor = Color(0xFFFFC24D)
            )
            Phase.DUSK -> SkyPalette(
                top = Color(0xFF262354),
                mid = Color(0xFF8A4462),
                bottom = Color(0xFFE7A183),
                glow = Color(0x48FF845E),
                cloudTint = Color(0xFFF2B6A0),
                sunColor = Color(0xFFFF8A50)
            )
        }
    }

    // Weather-driven atmospheric modulation while respecting the time phase
    return when (weather) {
        AtmosphereWeather.CLEAR -> clear
        AtmosphereWeather.PARTLY_CLOUDY -> clear.copy(
            top = lerpColor(clear.top, if (isDark) Color(0xFF1A283B) else Color(0xFF4F79A8), 0.18f),
            mid = lerpColor(clear.mid, if (isDark) Color(0xFF26374D) else Color(0xFF9EBAD6), 0.18f)
        )
        AtmosphereWeather.CLOUDY -> clear.copy(
            top = lerpColor(clear.top, if (isDark) Color(0xFF1B2533) else Color(0xFF5D728A), 0.58f),
            mid = lerpColor(clear.mid, if (isDark) Color(0xFF263345) else Color(0xFF95A8BD), 0.58f),
            bottom = lerpColor(clear.bottom, if (isDark) Color(0xFF19222E) else Color(0xFFDCE4EC), 0.58f),
            glow = clear.glow.copy(alpha = clear.glow.alpha * 0.45f),
            cloudTint = if (isDark) Color(0xFF47586E) else Color(0xFFDCE3EB)
        )
        AtmosphereWeather.FOG -> if (isDark) {
            SkyPalette(
                top = Color(0xFF1C2630),
                mid = Color(0xFF2A3745),
                bottom = Color(0xFF1D2630),
                glow = Color(0x229DB4CC),
                cloudTint = Color(0xFF5C6E80),
                sunColor = clear.sunColor
            )
        } else {
            SkyPalette(
                top = Color(0xFF9BB0C2),
                mid = Color(0xFFC5D2DE),
                bottom = Color(0xFFEBEEF2),
                glow = Color(0x33FFFFFF),
                cloudTint = Color(0xFFF5F7FA),
                sunColor = clear.sunColor
            )
        }
        AtmosphereWeather.RAIN -> clear.copy(
            top = lerpColor(clear.top, if (isDark) Color(0xFF0E1B2B) else Color(0xFF39506B), 0.72f),
            mid = lerpColor(clear.mid, if (isDark) Color(0xFF182C42) else Color(0xFF607C99), 0.72f),
            bottom = lerpColor(clear.bottom, if (isDark) Color(0xFF111F2E) else Color(0xFFC5D3E0), 0.72f),
            glow = Color(0x2252A2FF),
            cloudTint = if (isDark) Color(0xFF324861) else Color(0xFF8FA4BA)
        )
        AtmosphereWeather.SNOW -> clear.copy(
            top = lerpColor(clear.top, if (isDark) Color(0xFF1A2840) else Color(0xFF6482A3), 0.65f),
            mid = lerpColor(clear.mid, if (isDark) Color(0xFF293B59) else Color(0xFFAEC4DB), 0.65f),
            bottom = lerpColor(clear.bottom, if (isDark) Color(0xFF1E2D45) else Color(0xFFE8F0F7), 0.72f),
            glow = Color(0x33C7E2FF),
            cloudTint = if (isDark) Color(0xFF536987) else Color(0xFFEEF4FA)
        )
        AtmosphereWeather.STORM -> SkyPalette(
            top = if (isDark) Color(0xFF090E1A) else Color(0xFF1C273B),
            mid = if (isDark) Color(0xFF161F33) else Color(0xFF354563),
            bottom = if (isDark) Color(0xFF101726) else Color(0xFF7B8AA3),
            glow = Color(0x286C8EFF),
            cloudTint = if (isDark) Color(0xFF28354D) else Color(0xFF475773),
            sunColor = clear.sunColor
        )
    }
}

private fun lerpColor(a: Color, b: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

private data class StarSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radiusScale: Float,
    val twinkleSpeed: Float,
    val twinkleOffset: Float
)

private data class RainDropSpec(
    val xFrac: Float,
    val yOffset: Float,
    val speed: Float,
    val lengthFrac: Float,
    val alpha: Float,
    val strokeDp: Float
)

private data class SnowFlakeSpec(
    val xFrac: Float,
    val yOffset: Float,
    val speed: Float,
    val swayAmpFrac: Float,
    val swayFreq: Float,
    val radiusDp: Float,
    val alpha: Float,
    val phase: Float
)

private data class CloudSpec(
    val baseX: Float,
    val yFrac: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val speed: Float,
    val alpha: Float
)

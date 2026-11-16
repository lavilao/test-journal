package com.example.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.wallpaper.SunCycle.AtmosphereWeather
import kotlin.math.sin

/**
 * Dynamic atmospheric wallpaper matching the in-app NowBrief background:
 * shifts sky tones with the solar phase ([SunCycle]) and layers real local
 * weather conditions (clouds, mist, rain, snow, storm) cached by WeatherService.
 */
class SunWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SunEngine()

    private inner class SunEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                handler.postDelayed(this, 60_000L)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                handler.post(tick)
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            try {
                val canvas: Canvas = holder.lockCanvas() ?: return
                try {
                    render(canvas, canvas.width, canvas.height)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            } catch (_: Exception) {
                // Best effort drawing; never crash the wallpaper process.
            }
        }

        private fun render(canvas: Canvas, w: Int, h: Int) {
            val now = System.currentTimeMillis()
            val coords = SunCycle.lastCoordinates(applicationContext)
            val daylight = SunCycle.daylightFor(now, coords?.first, coords?.second)
            val phase = SunCycle.phaseAt(now, daylight)
            val weather = SunCycle.weatherFromCode(SunCycle.lastWeatherCode(applicationContext))

            val (topColor, bottomColor) = modulateSkyForWeather(skyColors(phase), weather)
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            val isNight = phase == SunCycle.Phase.NIGHT
            val horizonY = h * 0.72f
            val arcTopY = h * 0.14f

            val visibility = when (weather) {
                AtmosphereWeather.CLEAR -> 1.0f
                AtmosphereWeather.PARTLY_CLOUDY -> 0.8f
                AtmosphereWeather.CLOUDY -> 0.35f
                AtmosphereWeather.FOG -> 0.25f
                AtmosphereWeather.RAIN -> 0.22f
                AtmosphereWeather.SNOW -> 0.25f
                AtmosphereWeather.STORM -> 0.12f
            }

            if (isNight) {
                val moonProgress = SunCycle.moonProgress(now, daylight)
                val angle = Math.PI * moonProgress
                val cx = (w * 0.12 + w * 0.76 * moonProgress).toFloat()
                val cy = horizonY - (horizonY - arcTopY) * sin(angle).toFloat()
                drawStars(canvas, w, h, seed = (now / 3_600_000L).toInt(), alphaFactor = visibility)
                paint.color = applyAlpha(Color.parseColor("#EEF3FF"), visibility)
                canvas.drawCircle(cx, cy, w * 0.045f, paint)
                paint.color = topColor
                canvas.drawCircle(cx + w * 0.016f, cy - w * 0.010f, w * 0.037f, paint)
            } else {
                val sunProgress = SunCycle.sunProgress(now, daylight)
                val angle = Math.PI * sunProgress
                val cx = (w * 0.12 + w * 0.76 * sunProgress).toFloat()
                val cy = horizonY - (horizonY - arcTopY) * sin(angle).toFloat()
                val sunColor = sunColorFor(phase)
                val glowRadius = w * 0.22f
                paint.shader = RadialGradient(
                    cx, cy, glowRadius,
                    applyAlpha(sunColor, visibility), transparentVersion(sunColor),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, glowRadius, paint)
                paint.shader = null
                if (visibility > 0.2f) {
                    paint.color = applyAlpha(sunColor, visibility)
                    canvas.drawCircle(cx, cy, w * 0.052f, paint)
                }
            }

            if (weather != AtmosphereWeather.CLEAR) {
                drawWeatherOverlay(canvas, w, h, weather, now)
            }

            paint.shader = android.graphics.LinearGradient(
                0f, horizonY - h * 0.06f, 0f, h.toFloat(),
                transparentVersion(bottomColor), bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, horizonY - h * 0.06f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        private fun drawWeatherOverlay(canvas: Canvas, w: Int, h: Int, weather: AtmosphereWeather, now: Long) {
            val rnd = java.util.Random(now / 60_000L)
            when (weather) {
                AtmosphereWeather.PARTLY_CLOUDY, AtmosphereWeather.CLOUDY, AtmosphereWeather.FOG -> {
                    val count = if (weather == AtmosphereWeather.PARTLY_CLOUDY) 3 else 5
                    val alpha = if (weather == AtmosphereWeather.FOG) 0.28f else 0.22f
                    repeat(count) { i ->
                        val cx = w * (0.15f + i * 0.22f)
                        val cy = h * (0.14f + (i % 3) * 0.08f)
                        val r = w * 0.26f
                        paint.shader = RadialGradient(
                            cx, cy, r,
                            applyAlpha(Color.WHITE, alpha),
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, r, paint)
                        paint.shader = null
                    }
                }
                AtmosphereWeather.RAIN, AtmosphereWeather.STORM -> {
                    paint.color = applyAlpha(Color.parseColor("#D8EBFF"), 0.26f)
                    paint.strokeWidth = 2.5f
                    val drops = if (weather == AtmosphereWeather.STORM) 55 else 38
                    repeat(drops) {
                        val sx = rnd.nextFloat() * w
                        val sy = rnd.nextFloat() * h
                        canvas.drawLine(sx, sy, sx - w * 0.015f, sy + h * 0.038f, paint)
                    }
                }
                AtmosphereWeather.SNOW -> {
                    paint.color = applyAlpha(Color.WHITE, 0.55f)
                    repeat(42) {
                        val sx = rnd.nextFloat() * w
                        val sy = rnd.nextFloat() * h
                        canvas.drawCircle(sx, sy, 3.5f + rnd.nextFloat() * 4f, paint)
                    }
                }
                AtmosphereWeather.CLEAR -> Unit
            }
        }

        private fun skyColors(phase: SunCycle.Phase): Pair<Int, Int> = when (phase) {
            SunCycle.Phase.NIGHT -> Color.parseColor("#081128") to Color.parseColor("#182B52")
            SunCycle.Phase.DAWN -> Color.parseColor("#2B3A70") to Color.parseColor("#F2A489")
            SunCycle.Phase.MORNING -> Color.parseColor("#2E6EC7") to Color.parseColor("#F7DFC2")
            SunCycle.Phase.MIDDAY -> Color.parseColor("#2375D6") to Color.parseColor("#CFE8FA")
            SunCycle.Phase.AFTERNOON -> Color.parseColor("#2F6FC4") to Color.parseColor("#F5C98E")
            SunCycle.Phase.DUSK -> Color.parseColor("#2D2A5E") to Color.parseColor("#E0705F")
        }

        private fun modulateSkyForWeather(base: Pair<Int, Int>, weather: AtmosphereWeather): Pair<Int, Int> {
            val target = when (weather) {
                AtmosphereWeather.CLEAR -> return base
                AtmosphereWeather.PARTLY_CLOUDY -> Color.parseColor("#6A84A1") to Color.parseColor("#B5C8DC")
                AtmosphereWeather.CLOUDY -> Color.parseColor("#4B5F75") to Color.parseColor("#A4B5C7")
                AtmosphereWeather.FOG -> Color.parseColor("#7D8E9E") to Color.parseColor("#CDD6DE")
                AtmosphereWeather.RAIN -> Color.parseColor("#283C52") to Color.parseColor("#637C96")
                AtmosphereWeather.SNOW -> Color.parseColor("#4E6885") to Color.parseColor("#CBD9E8")
                AtmosphereWeather.STORM -> Color.parseColor("#141E30") to Color.parseColor("#354563")
            }
            val factor = when (weather) {
                AtmosphereWeather.PARTLY_CLOUDY -> 0.20f
                AtmosphereWeather.CLOUDY -> 0.58f
                AtmosphereWeather.FOG -> 0.72f
                AtmosphereWeather.RAIN -> 0.72f
                AtmosphereWeather.SNOW -> 0.65f
                AtmosphereWeather.STORM -> 0.85f
                AtmosphereWeather.CLEAR -> 0f
            }
            return blendColor(base.first, target.first, factor) to blendColor(base.second, target.second, factor)
        }

        private fun blendColor(a: Int, b: Int, f: Float): Int {
            val inv = 1f - f
            val r = (Color.red(a) * inv + Color.red(b) * f).toInt().coerceIn(0, 255)
            val g = (Color.green(a) * inv + Color.green(b) * f).toInt().coerceIn(0, 255)
            val bl = (Color.blue(a) * inv + Color.blue(b) * f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, bl)
        }

        private fun applyAlpha(color: Int, factor: Float): Int {
            val a = (255 * factor.coerceIn(0f, 1f)).toInt()
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun sunColorFor(phase: SunCycle.Phase): Int = when (phase) {
            SunCycle.Phase.DAWN -> Color.parseColor("#FFB25E")
            SunCycle.Phase.MORNING -> Color.parseColor("#FFCE5C")
            SunCycle.Phase.MIDDAY -> Color.parseColor("#FFF3B0")
            SunCycle.Phase.AFTERNOON -> Color.parseColor("#FFC24D")
            SunCycle.Phase.DUSK -> Color.parseColor("#FF8A50")
            SunCycle.Phase.NIGHT -> Color.parseColor("#E8ECF5")
        }

        private fun transparentVersion(color: Int): Int = (color and 0x00FFFFFF)

        private fun drawStars(canvas: Canvas, w: Int, h: Int, seed: Int, alphaFactor: Float) {
            val rnd = java.util.Random(seed.toLong())
            paint.color = applyAlpha(Color.WHITE, 0.75f * alphaFactor)
            repeat(42) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h * 0.58f
                val r = 0.8f + rnd.nextFloat() * 1.6f
                canvas.drawCircle(x, y, r, paint)
            }
        }
    }
}

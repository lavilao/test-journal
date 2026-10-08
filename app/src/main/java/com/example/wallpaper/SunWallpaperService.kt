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

/**
 * Dynamic sun wallpaper (Samsung-mode experiment, behind a settings toggle):
 *
 *  - The background gradient shifts tone with the time of day: deep night
 *    blue → dawn peach → warm morning → bright midday → golden afternoon
 *    → dusk violet → night again.
 *  - A sun disc travels along an arc from sunrise to sunset (position from
 *    [SunCycle], which uses the last coordinates the weather layer saved);
 *    at night a small moon takes its place on the same arc.
 *  - Redraws once per minute while visible — no per-frame animation loop,
 *    so the CPU/battery cost is negligible.
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
            val progress = SunCycle.sunProgress(now, daylight)

            // --- Sky gradient by phase ---
            val (topColor, bottomColor) = skyColors(phase)
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            // --- Celestial body along an arc ---
            val isNight = phase == SunCycle.Phase.NIGHT
            val horizonY = h * 0.78f
            val arcTopY = h * 0.16f
            // Parametric semicircle across the screen.
            val angle = Math.PI * progress
            val cx = (w * 0.1 + (w * 0.8) * progress).toFloat()
            val cy = (horizonY - (horizonY - arcTopY) * sin(angle).toFloat())

            if (isNight) {
                // Moon: small pale disc + a couple of stars.
                paint.color = Color.parseColor("#E8ECF5")
                canvas.drawCircle(cx, cy, w * 0.045f, paint)
                paint.color = Color.parseColor("#C9D2E3")
                canvas.drawCircle(cx - w * 0.014f, cy - w * 0.012f, w * 0.038f, paint)
                drawStars(canvas, w, h, seed = (now / 3_600_000L).toInt())
            } else {
                val sunColor = sunColorFor(phase)
                // Soft glow behind the disc.
                val glowRadius = w * 0.16f
                paint.shader = RadialGradient(
                    cx, cy, glowRadius,
                    sunColor, transparentVersion(sunColor),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, glowRadius, paint)
                paint.shader = null
                paint.color = sunColor
                canvas.drawCircle(cx, cy, w * 0.055f, paint)
            }

            // --- Horizon haze ---
            paint.shader = android.graphics.LinearGradient(
                0f, horizonY - h * 0.06f, 0f, h.toFloat(),
                transparentVersion(bottomColor), bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, horizonY - h * 0.06f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        private fun skyColors(phase: SunCycle.Phase): Pair<Int, Int> = when (phase) {
            SunCycle.Phase.NIGHT -> Color.parseColor("#0A1229") to Color.parseColor("#1C2B52")
            SunCycle.Phase.DAWN -> Color.parseColor("#31407A") to Color.parseColor("#F2A489")
            SunCycle.Phase.MORNING -> Color.parseColor("#4D7DC9") to Color.parseColor("#F7D9A8")
            SunCycle.Phase.MIDDAY -> Color.parseColor("#3E8BE8") to Color.parseColor("#BEE3F5")
            SunCycle.Phase.AFTERNOON -> Color.parseColor("#4E8FD6") to Color.parseColor("#F5C98E")
            SunCycle.Phase.DUSK -> Color.parseColor("#3A3870") to Color.parseColor("#E0705F")
        }

        private fun sunColorFor(phase: SunCycle.Phase): Int = when (phase) {
            SunCycle.Phase.DAWN -> Color.parseColor("#FFB25E")
            SunCycle.Phase.MORNING -> Color.parseColor("#FFCE5C")
            SunCycle.Phase.MIDDAY -> Color.parseColor("#FFF3B0")
            SunCycle.Phase.AFTERNOON -> Color.parseColor("#FFC24D")
            SunCycle.Phase.DUSK -> Color.parseColor("#FF8A50")
            SunCycle.Phase.NIGHT -> Color.parseColor("#E8ECF5")
        }

        private fun transparentVersion(color: Int): Int =
            (color and 0x00FFFFFF) // drop alpha -> fully transparent

        private fun drawStars(canvas: Canvas, w: Int, h: Int, seed: Int) {
            val rnd = java.util.Random(seed.toLong())
            paint.color = Color.parseColor("#CCFFFFFF")
            repeat(42) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h * 0.62f
                val r = 0.8f + rnd.nextFloat() * 1.6f
                canvas.drawCircle(x, y, r, paint)
            }
        }
    }
}

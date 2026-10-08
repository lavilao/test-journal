package com.example.health

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.telemetry.DeviceLifeHubManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * One honest, local "digital health" observation.
 */
data class HealthInsight(
    val emoji: String,
    val title: String,
    val detail: String,
    /** good | info | warn — drives the color in the UI. */
    val severity: String
)

/**
 * Builds the daily digital-health report from THIS DEVICE's own signals:
 * screen time, per-app usage, steps, late-night phone use and battery.
 *
 * Everything here is usage data the phone already holds locally — nothing
 * comes from web history or any cloud (the user's explicit design choice:
 * re-implement the useful part of Pixel "At a Glance" insights on top of
 * local data only). When a permission is missing the insight simply says
 * so instead of inventing numbers.
 */
object HealthInsightsManager {

    suspend fun buildTodayReport(context: Context): List<HealthInsight> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val insights = mutableListOf<HealthInsight>()

            val hasUsage = hasUsageStatsPermission(app)
            val usage = if (hasUsage) queryTodayUsage(app) else null

            // --- Screen time ---
            if (usage != null && usage.totalMinutes > 0) {
                val h = usage.totalMinutes / 60
                val m = usage.totalMinutes % 60
                val totalStr = if (h > 0) "$h h $m min" else "$m min"
                insights.add(
                    HealthInsight(
                        emoji = "⏱",
                        title = "Tiempo de pantalla hoy: $totalStr",
                        detail = if (usage.totalMinutes > 300) {
                            "Llevas más de 5 h con la pantalla encendida; una pausa de 20 min cada hora reduce el cansancio visual."
                        } else {
                            "Uso moderado por ahora. El registro es el de UsageStats de Android (local)."
                        },
                        severity = if (usage.totalMinutes > 300) "warn" else "good"
                    )
                )

                // --- Top app ---
                usage.topApps.firstOrNull()?.let { top ->
                    val topH = top.minutes / 60
                    val topM = top.minutes % 60
                    val topStr = if (topH > 0) "$topH h $topM min" else "$topM min"
                    val share = if (usage.totalMinutes > 0) top.minutes * 100 / usage.totalMinutes else 0
                    insights.add(
                        HealthInsight(
                            emoji = "📱",
                            title = "${top.appName}: $topStr ($share% del uso)",
                            detail = if (top.minutes > 120) {
                                "Pasas mucho tiempo en esta app. Si quieres reducirlo, prueba a moverla fuera de la pantalla de inicio."
                            } else {
                                "La app que más has usado hoy."
                            },
                            severity = if (top.minutes > 120) "warn" else "info"
                        )
                    )
                }

                // --- Late night usage → sleep suggestion ---
                usage.lastUseToday?.let { last ->
                    val cal = Calendar.getInstance().apply { timeInMillis = last }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour >= 23 || hour < 4) {
                        val hh = "%02d".format(hour)
                        val mm = "%02d".format(cal.get(Calendar.MINUTE))
                        insights.add(
                            HealthInsight(
                                emoji = "🌙",
                                title = "Anoche el teléfono estuvo activo hasta las $hh:$mm",
                                detail = "Dormir 7-8 h mejora la memoria —literalmente lo que esta app guarda—. " +
                                    "Prueba a dejar el teléfono fuera del dormitorio.",
                                severity = "warn"
                            )
                        )
                    }
                }
            } else {
                insights.add(
                    HealthInsight(
                        emoji = "⏱",
                        title = "Tiempo de pantalla",
                        detail = "Activa el «acceso de uso» para ver cuánto usas cada app (dato 100% local).",
                        severity = "info"
                    )
                )
            }

            // --- Steps ---
            val steps = DeviceLifeHubManager.cachedStepsToday(app)
            val hasStepPerm = ContextCompat.checkSelfPermission(
                app, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            insights.add(
                if (hasStepPerm) {
                    HealthInsight(
                        emoji = "👟",
                        title = "$steps pasos hoy",
                        detail = when {
                            steps >= 8000 -> "Meta de 8.000 superada. El movimiento de hoy ayuda al sueño de esta noche."
                            steps >= 4000 -> "Vas por buen camino hacia los 8.000; un paseo de 20 min te deja ahí."
                            else -> "Poco movimiento hoy. Un paseo corto después de comer suma rápido."
                        },
                        severity = if (steps >= 8000) "good" else "info"
                    )
                } else {
                    HealthInsight(
                        emoji = "👟",
                        title = "Contador de pasos",
                        detail = "Concede el permiso de actividad física para seguir tu movimiento (sensor local).",
                        severity = "info"
                    )
                }
            )

            // --- Battery ---
            val battery = readBattery(app)
            if (battery != null) {
                insights.add(
                    HealthInsight(
                        emoji = "🔋",
                        title = "Batería al $battery%",
                        detail = if (battery <= 20) {
                            "Carga pronto; con menos del 20% Android recorta las sincronizaciones en segundo plano."
                        } else {
                            "Estado normal."
                        },
                        severity = if (battery <= 20) "warn" else "info"
                    )
                )
            }

            insights
        }

    // ------------------------------------------------------------------
    // Usage query
    // ------------------------------------------------------------------

    private data class AppUsage(val appName: String, val packageName: String, val minutes: Int)

    private data class TodayUsage(
        val totalMinutes: Int,
        val topApps: List<AppUsage>,
        /** Wall-clock millis of the LAST phone use today (late-night signal). */
        val lastUseToday: Long?
    )

    private fun hasUsageStatsPermission(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    private fun queryTodayUsage(context: Context): TodayUsage? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val pm = context.packageManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val now = System.currentTimeMillis()

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                ?.filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                ?: return null
            if (stats.isEmpty()) return null

            var totalMs = 0L
            var lastUse: Long? = null
            val perApp = stats.map { s ->
                totalMs += s.totalTimeInForeground
                if (s.lastTimeUsed > start && (lastUse == null || s.lastTimeUsed > lastUse!!)) {
                    lastUse = s.lastTimeUsed
                }
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                } catch (_: Exception) {
                    s.packageName.substringAfterLast('.')
                }
                AppUsage(name, s.packageName, (s.totalTimeInForeground / 60_000L).toInt())
            }

            TodayUsage(
                totalMinutes = (totalMs / 60_000L).toInt(),
                topApps = perApp.sortedByDescending { it.minutes }.take(3),
                lastUseToday = lastUse
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readBattery(context: Context): Int? = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (p in 0..100) p else null
    } catch (_: Exception) {
        null
    }
}

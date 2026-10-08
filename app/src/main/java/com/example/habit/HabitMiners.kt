package com.example.habit

import android.content.Context
import com.example.ai.needle.NeedleModelManager
import com.example.ai.needle.NeedleRuntime
import com.example.ai.needle.NeedleTools
import com.example.data.local.AppDatabase
import com.example.telemetry.DeviceLifeHubManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The learning side of the habit engine: turns the raw [HabitEvent] log into
 * human-readable habits. All miners are pure Kotlin over the event list —
 * no ML model required, every number is derived from real observations, and
 * every fact knows how confident it is.
 *
 * When the local Needle model is downloaded, [generateBriefNarrative] asks
 * it (through its tool-calling interface) to write the daily brief from the
 * computed statistics.
 */
object HabitMiners {

    private const val DAY_MS = 24 * 60 * 60_000L

    // ------------------------------------------------------------------
    // Digest model
    // ------------------------------------------------------------------

    data class SleepNight(val wakeDayKey: String, val startMillis: Long, val endMillis: Long)

    data class SleepFacts(
        val lastNight: SleepNight?,
        val nightsWithData: Int,
        val typicalBedMinuteOfDay: Int?,
        val typicalWakeMinuteOfDay: Int?,
        val medianDurationMinutes: Int?,
        val regularity: Float?
    )

    data class AppUsage(val pkg: String, val opens: Int, val minutes: Long)

    data class AppPrediction(val pkg: String, val score: Float)

    data class AppFacts(
        val signature: List<AppUsage>,
        val predictedNow: List<AppPrediction>,
        val medianDailyOpens: Int
    )

    data class PlaceFacts(
        val homeBssid: String?,
        val homeConfidence: Float?,
        val homeCoords: Pair<Double, Double>?,
        val workBssid: String?,
        val workConfidence: Float?,
        val workCoords: Pair<Double, Double>?,
        val placesSeen: Int
    )

    data class BatteryFacts(
        val levelNow: Int,
        val drainPerHour: Float?,
        val projectedBedtimeLevel: Int?,
        val typicalChargeMinuteOfDay: Int?,
        val chargesPerWeek: Float,
        val overnightCharges: Int,
        val windowDays: Int
    )

    data class DailyValue(val dayKey: String, val value: Float)

    data class AttentionFacts(
        val screenMinutesToday: Int,
        val screenMinutes7dAvg: Float?,
        val unlocksPerDayAvg: Float?,
        val screenByDay: List<DailyValue>,
        val bestDay: String?,
        val worstDay: String?,
        val unlocksAreDerived: Boolean
    )

    data class NotifApp(val pkg: String, val perDay: Float, val ignoreRate: Float?)

    data class NotificationFacts(
        val topInterruptors: List<NotifApp>,
        val totalPerDay: Float,
        val daysWithNotifData: Int
    )

    data class RhythmChangeFacts(val summary: String, val shifts: List<String>)

    data class StepsFacts(val byDay: List<DailyValue>, val avgPerDay: Float, val today: Int)

    data class HabitDigest(
        val daysObserved: Int,
        val eventCount: Int,
        val sleep: SleepFacts?,
        val apps: AppFacts?,
        val places: PlaceFacts?,
        val battery: BatteryFacts?,
        val attention: AttentionFacts?,
        val notifications: NotificationFacts?,
        val rhythmChange: RhythmChangeFacts?,
        val steps: StepsFacts?
    )

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    suspend fun buildDigest(context: Context): HabitDigest = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val retention = HabitEngine.retentionDays(app).coerceAtLeast(7)
        val events = try {
            AppDatabase.getInstance(app).habitEventDao().between(now - retention * DAY_MS, now)
        } catch (_: Exception) {
            emptyList<HabitEvent>()
        }

        val excluded = HabitEngine.excludedPackages(app)
        val appOpen = events.filter {
            it.type == HabitEventType.APP_OPEN && (it.key ?: "") !in excluded
        }
        val awake = events.filter {
            it.type == HabitEventType.APP_OPEN || it.type == HabitEventType.UNLOCK || it.type == HabitEventType.SCREEN_ON
        }
        val daysObserved = awake.map { dayKey(it.timestamp) }.distinct().size
        val windowDays = if (events.isEmpty()) {
            retention
        } else {
            max(1, ((now - events.first().timestamp) / DAY_MS).toInt())
        }

        val sleep = mineSleep(awake, now)
        HabitDigest(
            daysObserved = daysObserved,
            eventCount = events.size,
            sleep = sleep,
            apps = mineApps(events, appOpen, now),
            places = minePlaces(events),
            battery = mineBattery(events, now, sleep, windowDays),
            attention = mineAttention(events, appOpen, daysObserved),
            notifications = mineNotifications(events, appOpen),
            rhythmChange = mineRhythmChange(awake, now),
            steps = mineSteps(app, events)
        )
    }

    /** Cheap query for the At a Glance chip: apps the user typically opens now. */
    suspend fun predictedAppsForNow(context: Context, limit: Int = 1): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                val now = System.currentTimeMillis()
                val events = AppDatabase.getInstance(app).habitEventDao()
                    .between(now - 14 * DAY_MS, now)
                val excluded = HabitEngine.excludedPackages(app)
                val opens = events.filter {
                    it.type == HabitEventType.APP_OPEN && (it.key ?: "") !in excluded
                }
                val predicted = scoreByHour(opens, now)
                predicted.take(limit).map { it.pkg }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ------------------------------------------------------------------
    // Sleep: longest overnight quiet gap in the activity stream
    // ------------------------------------------------------------------

    private fun mineSleep(awakeEvents: List<HabitEvent>, now: Long): SleepFacts? {
        // Dedupe identical timestamps (screen-on + unlock + app-open bursts).
        val stamps = awakeEvents.map { it.timestamp }.distinct().sorted()
        if (stamps.size < 10) return null

        data class Night(val wakeDayKey: String, val start: Long, val end: Long)

        val nights = mutableListOf<Night>()
        for (i in 0 until stamps.size - 1) {
            val t1 = stamps[i]
            val t2 = stamps[i + 1]
            val gapMin = (t2 - t1) / 60_000L
            if (gapMin < 180) continue // below 3 h: not a sleep candidate
            val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
            val h1 = cal1.get(Calendar.HOUR_OF_DAY)
            val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
            val h2 = cal2.get(Calendar.HOUR_OF_DAY)
            val bedOk = h1 in 20..23 || h1 in 0..4
            val wakeOk = h2 in 3..11
            if (bedOk && wakeOk) {
                val wakeDay = dayKey(t2)
                val idx = nights.indexOfFirst { it.wakeDayKey == wakeDay }
                if (idx >= 0) {
                    if (t2 - t1 > nights[idx].end - nights[idx].start) {
                        nights[idx] = Night(wakeDay, t1, t2)
                    }
                } else {
                    nights.add(Night(wakeDay, t1, t2))
                }
            }
        }
        if (nights.isEmpty()) return null

        val lastNight = nights.lastOrNull { it.end <= now } ?: nights.last()

        // Bedtime as "minutes since 18:00" so 23:30 and 00:30 compare linearly.
        fun shiftedBed(ts: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            val minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            return if (minuteOfDay >= 18 * 60) minuteOfDay - 18 * 60 else minuteOfDay + 6 * 60
        }

        fun unshiftBed(shifted: Int): Int = if (shifted >= 6 * 60) shifted - 6 * 60 else shifted + 18 * 60

        val bedShifted = nights.map { shiftedBed(it.start) }
        val wakeMinutes = nights.map {
            val c = Calendar.getInstance().apply { timeInMillis = it.end }
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
        val durations = nights.map { ((it.end - it.start) / 60_000L).toInt() }

        val typicalBed = medianInt(bedShifted)?.let { unshiftBed(it) }
        val typicalWake = medianInt(wakeMinutes)
        val medianDuration = medianInt(durations)
        val regularity = if (nights.size >= 5) {
            val spread = (stdFloat(bedShifted.map { it.toFloat() }) +
                stdFloat(wakeMinutes.map { it.toFloat() }))
            (1f - spread / 180f).coerceIn(0f, 1f)
        } else {
            null
        }

        return SleepFacts(
            lastNight = SleepNight(lastNight.wakeDayKey, lastNight.start, lastNight.end),
            nightsWithData = nights.size,
            typicalBedMinuteOfDay = typicalBed,
            typicalWakeMinuteOfDay = typicalWake,
            medianDurationMinutes = medianDuration,
            regularity = regularity
        )
    }

    // ------------------------------------------------------------------
    // Apps: signature apps, usage minutes, next-app prediction
    // ------------------------------------------------------------------

    private fun mineApps(
        events: List<HabitEvent>,
        appOpen: List<HabitEvent>,
        now: Long
    ): AppFacts? {
        if (appOpen.isEmpty()) return null
        val days = appOpen.map { dayKey(it.timestamp) }.distinct().size.coerceAtLeast(1)

        val minutesByPkg = HashMap<String, Long>()
        val pkgEvents = events.filter {
            it.type == HabitEventType.APP_OPEN || it.type == HabitEventType.APP_CLOSE
        }.groupBy { it.key ?: "" }
        for ((pkg, list) in pkgEvents) {
            val sorted = list.sortedBy { it.timestamp }
            var openTs: Long? = null
            for (e in sorted) {
                if (e.type == HabitEventType.APP_OPEN) {
                    if (openTs == null) openTs = e.timestamp
                } else {
                    val o = openTs ?: continue
                    val dur = (e.timestamp - o).coerceIn(0L, 30 * 60_000L)
                    minutesByPkg[pkg] = (minutesByPkg[pkg] ?: 0L) + dur
                    openTs = null
                }
            }
        }

        val weekAgo = now - 7 * DAY_MS
        val signature = appOpen.filter { it.timestamp >= weekAgo }
            .groupBy { it.key ?: "" }
            .map { (pkg, list) -> AppUsage(pkg, list.size, minutesByPkg[pkg] ?: 0L) }
            .sortedByDescending { it.opens }
            .take(6)

        val predicted = scoreByHour(appOpen, now).filter { it.score >= 2f }.take(3)

        return AppFacts(
            signature = signature,
            predictedNow = predicted,
            medianDailyOpens = appOpen.size / days
        )
    }

    /** Score = opens in the ±1 h window with matching day type (week/weekend). */
    private fun scoreByHour(appOpen: List<HabitEvent>, now: Long): List<AppPrediction> {
        if (appOpen.isEmpty()) return emptyList()
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val hourNow = nowCal.get(Calendar.HOUR_OF_DAY)
        val weekendNow = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val window = setOf((hourNow + 23) % 24, hourNow, (hourNow + 1) % 24)

        val counts = HashMap<String, Int>()
        for (e in appOpen) {
            val c = Calendar.getInstance().apply { timeInMillis = e.timestamp }
            val h = c.get(Calendar.HOUR_OF_DAY)
            if (h !in window) continue
            val weekend = c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            if (weekend != weekendNow) continue
            val pkg = e.key ?: continue
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        return counts.map { (pkg, count) -> AppPrediction(pkg, count.toFloat()) }
            .sortedByDescending { it.score }
    }

    // ------------------------------------------------------------------
    // Places: home/work from Wi-Fi cells + passive location medians
    // ------------------------------------------------------------------

    private fun minePlaces(events: List<HabitEvent>): PlaceFacts? {
        val wifi = events.filter { it.type == HabitEventType.WIFI && !it.key.isNullOrBlank() }
        val locations = events.filter {
            it.type == HabitEventType.LOCATION && it.value != null && it.meta != null
        }
        if (wifi.isEmpty() && locations.isEmpty()) return null

        fun hourOf(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }
            .get(Calendar.HOUR_OF_DAY)

        fun isWeekday(ts: Long): Boolean {
            val d = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)
            return d != Calendar.SATURDAY && d != Calendar.SUNDAY
        }

        val nightWifi = wifi.filter { hourOf(it.timestamp) in 0..5 }
        val dayWifi = wifi.filter { isWeekday(it.timestamp) && hourOf(it.timestamp) in 9..17 }

        val homeEntry = nightWifi.groupBy { it.key!! }
            .map { (bssid, list) -> bssid to list.size }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= 5 }
        val workEntry = dayWifi.groupBy { it.key!! }
            .map { (bssid, list) -> bssid to list.size }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= 5 }

        val nightLoc = locations.filter { hourOf(it.timestamp) in 0..5 }
        val dayLoc = locations.filter { isWeekday(it.timestamp) && hourOf(it.timestamp) in 9..17 }

        fun medianCoords(list: List<HabitEvent>): Pair<Double, Double>? {
            if (list.size < 5) return null
            val lats = list.map { it.value!! }.sorted()
            val lons = list.map { it.meta!!.toDouble() }.sorted()
            return medianDouble(lats) to medianDouble(lons)
        }

        return PlaceFacts(
            homeBssid = homeEntry?.first,
            homeConfidence = homeEntry?.let { it.second.toFloat() / nightWifi.size.coerceAtLeast(1) },
            homeCoords = medianCoords(nightLoc),
            workBssid = workEntry?.first,
            workConfidence = workEntry?.let { it.second.toFloat() / dayWifi.size.coerceAtLeast(1) },
            workCoords = medianCoords(dayLoc),
            placesSeen = wifi.map { it.key!! }.distinct().size
        )
    }

    // ------------------------------------------------------------------
    // Battery coach
    // ------------------------------------------------------------------

    private fun mineBattery(
        events: List<HabitEvent>,
        now: Long,
        sleep: SleepFacts?,
        windowDays: Int
    ): BatteryFacts? {
        val batt = events.filter { it.type == HabitEventType.BATTERY && it.value != null }
            .sortedBy { it.timestamp }
        if (batt.isEmpty()) return null
        val levelNow = batt.last().value!!.toInt()

        val todayKey = dayKey(now)
        val todayDischarging = batt.filter {
            dayKey(it.timestamp) == todayKey && it.meta == "discharging"
        }
        var drainPerHour: Float? = null
        if (todayDischarging.size >= 3) {
            val spanH = (todayDischarging.last().timestamp - todayDischarging.first().timestamp) / 3_600_000.0
            val drop = todayDischarging.first().value!! - todayDischarging.last().value!!
            if (spanH >= 1.5 && drop > 0) drainPerHour = (drop / spanH).toFloat()
        }

        // Project to the typical bedtime (from sleep facts, else 23:30).
        val bedMinuteOfDay = sleep?.typicalBedMinuteOfDay ?: (23 * 60 + 30)
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val nowMinute = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        var minutesToBed = bedMinuteOfDay - nowMinute
        if (minutesToBed < 0) minutesToBed += 24 * 60
        val projected = drainPerHour?.let { dph ->
            (levelNow - dph * (minutesToBed / 60f)).roundToInt().coerceIn(0, 100)
        }

        val starts = events.filter { it.type == HabitEventType.CHARGE_START }
        val startsPerWeek = if (windowDays > 0) starts.size / (windowDays / 7f) else 0f
        val typicalChargeMinute = if (starts.size >= 3) {
            medianInt(starts.map {
                val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            })
        } else {
            null
        }
        val overnight = starts.count {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            m >= 21 * 60 || m < 3 * 60
        }

        return BatteryFacts(
            levelNow = levelNow,
            drainPerHour = drainPerHour,
            projectedBedtimeLevel = projected,
            typicalChargeMinuteOfDay = typicalChargeMinute,
            chargesPerWeek = startsPerWeek,
            overnightCharges = overnight,
            windowDays = windowDays
        )
    }

    // ------------------------------------------------------------------
    // Attention: screen minutes, unlocks, best/worst days
    // ------------------------------------------------------------------

    private fun mineAttention(
        events: List<HabitEvent>,
        appOpen: List<HabitEvent>,
        daysObserved: Int
    ): AttentionFacts? {
        if (daysObserved < 1 || appOpen.isEmpty()) return null

        // Per-day usage minutes from open/close pairing.
        val minutesByDay = HashMap<String, Long>()
        val pkgEvents = events.filter {
            it.type == HabitEventType.APP_OPEN || it.type == HabitEventType.APP_CLOSE
        }.groupBy { it.key ?: "" }
        for ((_, list) in pkgEvents) {
            val sorted = list.sortedBy { it.timestamp }
            var openTs: Long? = null
            for (e in sorted) {
                if (e.type == HabitEventType.APP_OPEN) {
                    if (openTs == null) openTs = e.timestamp
                } else {
                    val o = openTs ?: continue
                    val dur = (e.timestamp - o).coerceIn(0L, 30 * 60_000L)
                    val day = dayKey(o)
                    minutesByDay[day] = (minutesByDay[day] ?: 0L) + dur
                    openTs = null
                }
            }
        }

        val todayKey = dayKey(System.currentTimeMillis())
        val sortedDays = minutesByDay.keys.sorted()
        val last14 = sortedDays.takeLast(14)
        val screenByDay = last14.map { DailyValue(it, (minutesByDay[it] ?: 0L) / 60f) }
        val screenToday = ((minutesByDay[todayKey] ?: 0L) / 60L).toInt()

        val previous7 = sortedDays.filter { it != todayKey }.takeLast(7)
        val screen7Avg = if (previous7.isNotEmpty()) {
            previous7.map { (minutesByDay[it] ?: 0L) / 60f }.average().toFloat()
        } else {
            null
        }

        // Unlocks: real UNLOCK events when available (Android 15+), else
        // derived "pickup sessions" (activity restart after a >= 10 min gap).
        val unlockEvents = events.filter { it.type == HabitEventType.UNLOCK }
        val unlocksAreDerived = unlockEvents.size < 10
        val perDayUnlocks: Map<String, Int> = if (!unlocksAreDerived) {
            unlockEvents.groupBy { dayKey(it.timestamp) }.mapValues { it.value.size }
        } else {
            val stamps = appOpen.map { it.timestamp }.distinct().sorted()
            val derived = HashMap<String, Int>()
            for (i in 0 until stamps.size - 1) {
                if (stamps[i + 1] - stamps[i] >= 10 * 60_000L) {
                    val day = dayKey(stamps[i + 1])
                    derived[day] = (derived[day] ?: 0) + 1
                }
            }
            derived
        }
        val unlockDays = perDayUnlocks.keys.filter { it != todayKey }
        val unlocksAvg = if (unlockDays.isNotEmpty()) {
            unlockDays.map { perDayUnlocks[it] ?: 0 }.average().toFloat()
        } else {
            null
        }

        val completeDays = minutesByDay.filterKeys { it != todayKey }.filterValues { it > 0 }
        val bestDay = completeDays.maxByOrNull { it.value }?.key?.let { formatDayLabel(it) }
        val worstDay = completeDays.minByOrNull { it.value }?.key?.let { formatDayLabel(it) }

        return AttentionFacts(
            screenMinutesToday = screenToday,
            screenMinutes7dAvg = screen7Avg,
            unlocksPerDayAvg = unlocksAvg,
            screenByDay = screenByDay,
            bestDay = bestDay,
            worstDay = worstDay,
            unlocksAreDerived = unlocksAreDerived
        )
    }

    // ------------------------------------------------------------------
    // Notification intelligence: who interrupts, what gets ignored
    // ------------------------------------------------------------------

    private fun mineNotifications(events: List<HabitEvent>, appOpen: List<HabitEvent>): NotificationFacts? {
        val posted = events.filter {
            it.type == HabitEventType.NOTIF_POSTED && !it.key.isNullOrBlank() && !it.meta.orEmpty().contains("ongoing=1")
        }
        if (posted.isEmpty()) return null
        val daysWithNotifData = posted.map { dayKey(it.timestamp) }.distinct().size.coerceAtLeast(1)

        val removed = events.filter { it.type == HabitEventType.NOTIF_REMOVED && !it.key.isNullOrBlank() }
            .groupBy { it.key!! }
            .mapValues { (_, list) -> list.map { it.timestamp }.sorted() }
        val opens = appOpen.groupBy { it.key ?: "" }
            .mapValues { (_, list) -> list.map { it.timestamp }.sorted() }

        /** Number of sorted timestamps inside [from, to]. */
        fun countInWindow(sorted: List<Long>, from: Long, to: Long): Int {
            var count = 0
            for (ts in sorted) {
                when {
                    ts < from -> continue
                    ts > to -> return count
                    else -> count++
                }
            }
            return count
        }

        val perPkg = posted.groupBy { it.key!! }
        val top = perPkg.map { (pkg, list) ->
            val opened = opens[pkg] ?: emptyList()
            val removedTs = removed[pkg] ?: emptyList()
            var engaged = 0
            var ignored = 0
            for (e in list) {
                val openedAfter = countInWindow(opened, e.timestamp, e.timestamp + 10 * 60_000L)
                if (openedAfter > 0) {
                    engaged++
                } else if (countInWindow(removedTs, e.timestamp, e.timestamp + 60 * 60_000L) > 0) {
                    ignored++
                }
            }
            val rate = if (engaged + ignored >= 5) ignored.toFloat() / (engaged + ignored) else null
            NotifApp(pkg, list.size / daysWithNotifData.toFloat(), rate)
        }.sortedByDescending { it.perDay }.take(5)

        return NotificationFacts(
            topInterruptors = top,
            totalPerDay = posted.size / daysWithNotifData.toFloat(),
            daysWithNotifData = daysWithNotifData
        )
    }

    // ------------------------------------------------------------------
    // Rhythm change detection: this week vs the previous three
    // ------------------------------------------------------------------

    private fun mineRhythmChange(awakeEvents: List<HabitEvent>, now: Long): RhythmChangeFacts? {
        if (awakeEvents.isEmpty()) return null
        val dayBuckets = HashMap<String, IntArray>()
        for (e in awakeEvents) {
            val c = Calendar.getInstance().apply { timeInMillis = e.timestamp }
            val bucket = c.get(Calendar.HOUR_OF_DAY) / 3
            val day = dayKey(e.timestamp)
            dayBuckets.getOrPut(day) { IntArray(8) }[bucket]++
        }
        val sortedDays = dayBuckets.keys.sorted().takeLast(28)
        if (sortedDays.size < 10) return null
        val thisWeek = sortedDays.takeLast(7)
        val prior = sortedDays.dropLast(7)
        if (prior.size < 7) return null

        val bucketLabels = listOf(
            "la madrugada (0-3)", "el amanecer (3-6)", "la mañana (6-9)", "el mediodía (9-12)",
            "la tarde (12-15)", " media tarde (15-18)", "el anochecer (18-21)", "la noche (21-24)"
        )

        val deviations = (0 until 8).map { b ->
            val priorMean = prior.map { dayBuckets[it]!![b] }.average()
            val weekMean = thisWeek.map { dayBuckets[it]!![b] }.average()
            Triple(b, priorMean, weekMean - priorMean)
        }
        val meaningful = deviations.filter { it.second >= 3.0 }
        if (meaningful.isEmpty()) return null
        val score = meaningful.map { abs(it.third) / max(it.second, 2.0) }.average()
        if (score < 0.20) return null

        val shifts = meaningful
            .sortedByDescending { abs(it.third) / max(it.second, 2.0) }
            .take(2)
            .map { (b, priorMean, delta) ->
                val label = bucketLabels[b].trim()
                if (delta > 0) "más actividad en $label que antes" else "menos actividad en $label que antes"
            }

        return RhythmChangeFacts(
            summary = "Tu rutina cambió esta semana respecto a las anteriores",
            shifts = shifts
        )
    }

    // ------------------------------------------------------------------
    // Steps trend
    // ------------------------------------------------------------------

    private fun mineSteps(context: Context, events: List<HabitEvent>): StepsFacts? {
        val stepsEvents = events.filter { it.type == HabitEventType.STEPS && it.value != null }
        val byDay = stepsEvents.groupBy { dayKey(it.timestamp) }
            .mapValues { (_, list) -> list.maxOf { it.value!! } }
        if (byDay.isEmpty()) return null

        val todayKey = dayKey(System.currentTimeMillis())
        val sortedDays = byDay.keys.sorted()
        val last14 = sortedDays.takeLast(14).map { DailyValue(it, byDay[it]!!.toFloat()) }
        val previous7 = sortedDays.filter { it != todayKey }.takeLast(7)
        val avg = if (previous7.isNotEmpty()) {
            previous7.map { byDay[it]!! }.average().toFloat()
        } else {
            byDay.values.sorted()[byDay.size / 2].toFloat()
        }
        val today = try {
            DeviceLifeHubManager.cachedStepsToday(context).coerceAtLeast(byDay[todayKey]?.toInt() ?: 0)
        } catch (_: Exception) {
            (byDay[todayKey] ?: 0.0).toInt()
        }
        return StepsFacts(byDay = last14, avgPerDay = avg, today = today)
    }

    // ------------------------------------------------------------------
    // Daily brief: Needle narrative + honest template fallback
    // ------------------------------------------------------------------

    /**
     * Asks the local Needle model to write the daily brief. The model is a
     * tool-calling engine, so the brief arrives as a `resumen_habitos`
     * function call carrying the narrative — never trust random free text.
     * Returns null when Needle is unusable (caller shows the template).
     */
    suspend fun generateBriefNarrative(context: Context, digest: HabitDigest): String? {
        val app = context.applicationContext
        val ready = try {
            NeedleRuntime.isReady() || (
                NeedleModelManager.isNeedleDownloaded(app) &&
                    NeedleRuntime.loadFromDisk(app) > 0 &&
                    NeedleRuntime.isReady()
                )
        } catch (_: Exception) {
            false
        }
        if (!ready) return null
        return try {
            val raw = withTimeoutOrNull(30_000L) {
                NeedleRuntime.completeText(buildBriefPrompt(digest), 420)
            } ?: return null
            val parsed = NeedleTools.parseResponse(raw) ?: return null
            val call = parsed.calls.firstOrNull { it.name == "resumen_habitos" } ?: return null
            call.args.optString("texto").takeIf { it.isNotBlank() }?.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBriefPrompt(digest: HabitDigest): String {
        val lines = mutableListOf<String>()
        lines.add("Estadísticas reales de los últimos días (calculadas por la app, todo local):")
        lines.add("- Días observados: ${digest.daysObserved}")
        digest.sleep?.let { s ->
            s.lastNight?.let { n ->
                lines.add("- Anoche durmió ${formatDuration((n.endMillis - n.startMillis) / 60_000L)} (de ${formatMinuteOfDay(minuteOfDay(n.startMillis))} a ${formatMinuteOfDay(minuteOfDay(n.endMillis))})")
            }
            if (s.typicalBedMinuteOfDay != null && s.typicalWakeMinuteOfDay != null) {
                lines.add("- Sueño típico: se acuesta ~${formatMinuteOfDay(s.typicalBedMinuteOfDay)}, despierta ~${formatMinuteOfDay(s.typicalWakeMinuteOfDay)}")
            }
            s.regularity?.let { lines.add("- Regularidad del sueño: ${(it * 100).roundToInt()}%") }
        }
        digest.apps?.let { a ->
            a.signature.take(3).forEachIndexed { i, u ->
                lines.add("- App #${i + 1}: ${u.pkg} (${u.opens} aperturas/semana, ${formatDuration(u.minutes / 60_000L)})")
            }
            a.predictedNow.firstOrNull()?.let { lines.add("- A esta hora suele abrir ${it.pkg}") }
        }
        digest.battery?.let { b ->
            b.drainPerHour?.let { lines.add("- Batería: pierde ~${"%.1f".format(it)}%/h hoy") }
            b.projectedBedtimeLevel?.let { lines.add("- Proyección: llegará a la hora de dormir con ~$it%") }
        }
        digest.attention?.let { at ->
            lines.add("- Pantalla hoy: ${formatDuration(at.screenMinutesToday.toLong())}")
            at.unlocksPerDayAvg?.let { lines.add("- Desbloqueos: ~${it.roundToInt()} al día") }
        }
        digest.notifications?.let { n ->
            lines.add("- Notificaciones: ~${n.totalPerDay.roundToInt()} al día")
            n.topInterruptors.firstOrNull()?.let { t ->
                t.ignoreRate?.let { r -> lines.add("- ${t.pkg} se ignora el ${(r * 100).roundToInt()}% de las veces") }
            }
        }
        digest.steps?.let { lines.add("- Pasos: media de ${it.avgPerDay.roundToInt()} al día, hoy ${it.today}") }
        digest.rhythmChange?.let { lines.add("- Cambio detectado: ${it.shifts.joinToString("; ")}") }

        lines.add("")
        lines.add(
            "Tarea: llama a la herramienta resumen_habitos con un resumen breve (3 a 6 frases) en español, " +
                "útil y concreto para empezar el día, basado SOLO en estas estadísticas (no inventes datos), " +
                "con un máximo de un consejo práctico."
        )
        return lines.joinToString("\n")
    }

    /** Honest fallback brief built directly from the facts (no AI needed). */
    fun templateBrief(digest: HabitDigest): String {
        val parts = mutableListOf<String>()
        digest.sleep?.let { s ->
            val bed = s.typicalBedMinuteOfDay?.let { "Sueles acostarte hacia las ${formatMinuteOfDay(it)}" }
            val wake = s.typicalWakeMinuteOfDay?.let { "y despertar hacia las ${formatMinuteOfDay(it)}" }
            if (bed != null) parts.add(listOfNotNull(bed, wake).joinToString(" ") + ".")
            s.lastNight?.let { n ->
                parts.add("Anoche dormiste ${formatDuration((n.endMillis - n.startMillis) / 60_000L)}.")
            }
        }
        digest.apps?.predictedNow?.firstOrNull()?.let {
            parts.add("A esta hora sueles abrir ${it.pkg}.")
        }
        digest.battery?.let { b ->
            b.projectedBedtimeLevel?.let { lvl ->
                parts.add("Con el ritmo de hoy, llegarás a la hora de dormir con ~$lvl% de batería.")
            }
        }
        digest.attention?.let { at ->
            at.screenMinutes7dAvg?.let { avg ->
                parts.add("Tu media de pantalla es ${formatDuration(avg.roundToInt().toLong())} al día.")
            }
        }
        digest.notifications?.topInterruptors?.firstOrNull()?.let { t ->
            t.ignoreRate?.let { r ->
                parts.add("${t.pkg} te interrumpe mucho y ignoras el ${(r * 100).roundToInt()}% de sus avisos.")
            }
        }
        if (parts.isEmpty()) parts.add("Aún estoy aprendiendo tus patrones; abre la app mañana para ver el primer resumen.")
        return parts.joinToString(" ")
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    fun dayKey(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    fun minuteOfDay(ts: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun formatMinuteOfDay(minute: Int): String =
        String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h} h ${m} min"
            h > 0 -> "${h} h"
            else -> "${m} min"
        }
    }

    fun formatDayLabel(dayKey: String): String = try {
        val parsed = SimpleDateFormat("yyyyMMdd", Locale.US).parse(dayKey)
        SimpleDateFormat("EEE d", Locale("es")).format(parsed ?: Date())
    } catch (_: Exception) {
        dayKey
    }

    fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.US) }
    }

    private fun medianInt(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun medianDouble(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun stdFloat(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }
}

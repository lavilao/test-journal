package com.example.routine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.repository.JournalRepository
import com.example.data.model.JournalEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Bixby-Routines-style personal automation, 100% on-device.
 *
 *   SI  <disparador>  →  ENTONCES  <acción>
 *
 * Triggers (detected without any new permission):
 *  - TIME         a fixed daily time (checked on the 5-minute sync tick)
 *  - CHARGING_ON  charger plugged (exempted system broadcast)
 *  - CHARGING_OFF charger unplugged (exempted system broadcast)
 *  - BATTERY_LOW  battery at or below a level (checked on the tick)
 *  - APP_OPEN     app cold start (deduped to once per 10 minutes)
 *
 * Actions (app-contained: the app never writes outside itself):
 *  - NOTIFICATION  a notification with custom text
 *  - NOTE          creates a journal note from a template
 *  - OPEN_NEWS     notification that deep-links to the news browser
 *  - OPEN_ASSISTANT notification that deep-links to the local assistant
 *
 * Storage: one JSON array in SharedPreferences — no schema migration risk.
 * Every fired action surfaces as a notification (the only sanctioned way
 * for a background receiver to reach the user); taps deep-link INTO the app.
 */
object RoutineEngine {

    private const val PREFS = "user_routines_prefs"
    private const val KEY_ROUTINES = "routines_json"
    private const val CHANNEL_ID = "mnemosyne_routines"
    private const val APP_OPEN_MIN_INTERVAL_MS = 10 * 60 * 1000L
    private const val BATTERY_MIN_INTERVAL_MS = 3 * 60 * 60 * 1000L
    private const val TIME_GRACE_MINUTES = 10L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class RoutineEvent { TICK, POWER_CONNECTED, POWER_DISCONNECTED, APP_OPEN }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    sealed class Trigger {
        abstract val type: String

        data class Time(val hour: Int, val minute: Int) : Trigger() {
            override val type get() = "time"
        }

        data object ChargingOn : Trigger() {
            override val type get() = "charging_on"
        }

        data object ChargingOff : Trigger() {
            override val type get() = "charging_off"
        }

        data class BatteryBelow(val level: Int) : Trigger() {
            override val type get() = "battery_below"
        }

        data object AppOpen : Trigger() {
            override val type get() = "app_open"
        }
    }

    sealed class Action {
        abstract val type: String

        data class Notify(val title: String, val text: String) : Action() {
            override val type get() = "notification"
        }

        data class CreateNote(val title: String, val body: String) : Action() {
            override val type get() = "note"
        }

        data object OpenNews : Action() {
            override val type get() = "open_news"
        }

        data object OpenAssistant : Action() {
            override val type get() = "open_assistant"
        }
    }

    data class UserRoutine(
        val id: String,
        val name: String,
        val enabled: Boolean = true,
        val trigger: Trigger,
        val action: Action,
        val lastFiredAt: Long = 0L
    )

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    fun load(context: Context): MutableList<UserRoutine> {
        val app = context.applicationContext
        return try {
            val json = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROUTINES, null) ?: return mutableListOf()
            val arr = JSONArray(json)
            val out = mutableListOf<UserRoutine>()
            for (i in 0 until arr.length()) {
                parseRoutine(arr.getJSONObject(i))?.let { out.add(it) }
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, routines: List<UserRoutine>) {
        try {
            val arr = JSONArray()
            routines.forEach { arr.put(routineJson(it)) }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ROUTINES, arr.toString())
                .commit()
        } catch (_: Exception) {
        }
    }

    fun upsert(context: Context, routine: UserRoutine) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == routine.id }
        if (idx >= 0) list[idx] = routine else list.add(routine)
        save(context, list)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }

    private fun parseRoutine(o: JSONObject): UserRoutine? = try {
        val trigger: Trigger = when (val t = o.getString("trigger_type")) {
            "time" -> Trigger.Time(o.getInt("hour"), o.getInt("minute"))
            "charging_on" -> Trigger.ChargingOn
            "charging_off" -> Trigger.ChargingOff
            "battery_below" -> Trigger.BatteryBelow(o.optInt("level", 20))
            "app_open" -> Trigger.AppOpen
            else -> return null
        }
        val action: Action = when (val a = o.getString("action_type")) {
            "notification" -> Action.Notify(
                o.optString("action_title", "Rutina"),
                o.optString("action_text", "")
            )
            "note" -> Action.CreateNote(
                o.optString("action_title", "Nota de rutina"),
                o.optString("action_body", "")
            )
            "open_news" -> Action.OpenNews
            "open_assistant" -> Action.OpenAssistant
            else -> return null
        }
        UserRoutine(
            id = o.getString("id"),
            name = o.optString("name", "Rutina"),
            enabled = o.optBoolean("enabled", true),
            trigger = trigger,
            action = action,
            lastFiredAt = o.optLong("last_fired_at", 0L)
        )
    } catch (_: Exception) {
        null
    }

    private fun routineJson(r: UserRoutine): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("enabled", r.enabled)
        put("trigger_type", r.trigger.type)
        when (val t = r.trigger) {
            is Trigger.Time -> {
                put("hour", t.hour)
                put("minute", t.minute)
            }
            is Trigger.BatteryBelow -> put("level", t.level)
            else -> {}
        }
        put("action_type", r.action.type)
        when (val a = r.action) {
            is Action.Notify -> {
                put("action_title", a.title)
                put("action_text", a.text)
            }
            is Action.CreateNote -> {
                put("action_title", a.title)
                put("action_body", a.body)
            }
            else -> {}
        }
        put("last_fired_at", r.lastFiredAt)
    }

    // ------------------------------------------------------------------
    // Evaluation
    // ------------------------------------------------------------------

    /** Async entry point for receivers (fire-and-forget on IO). */
    fun evaluateAsync(context: Context, event: RoutineEvent) {
        val app = context.applicationContext
        scope.launch {
            try {
                evaluate(app, event)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun evaluate(context: Context, event: RoutineEvent) {
        withContext(Dispatchers.IO) {
            val routines = load(context)
            if (routines.none { it.enabled }) return@withContext
            var dirty = false
            val now = System.currentTimeMillis()

            for (i in routines.indices) {
                val r = routines[i]
                if (!r.enabled) continue
                val shouldFire = when (event) {
                    RoutineEvent.POWER_CONNECTED -> r.trigger is Trigger.ChargingOn
                    RoutineEvent.POWER_DISCONNECTED -> r.trigger is Trigger.ChargingOff
                    RoutineEvent.APP_OPEN ->
                        r.trigger is Trigger.AppOpen &&
                            (r.lastFiredAt == 0L || now - r.lastFiredAt > APP_OPEN_MIN_INTERVAL_MS)
                    RoutineEvent.TICK -> when (val t = r.trigger) {
                        is Trigger.Time -> isTimeDue(t, r.lastFiredAt, now)
                        is Trigger.BatteryBelow ->
                            batteryPercent(context)?.let { it <= t.level } == true &&
                                (r.lastFiredAt == 0L || now - r.lastFiredAt > BATTERY_MIN_INTERVAL_MS)
                        else -> false
                    }
                }
                if (shouldFire) {
                    fire(context, r)
                    routines[i] = r.copy(lastFiredAt = now)
                    dirty = true
                }
            }
            if (dirty) save(context, routines)
        }
    }

    /** True when the daily slot arrived and this routine hasn't run for it. */
    private fun isTimeDue(t: Trigger.Time, lastFiredAt: Long, now: Long): Boolean {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, t.hour)
            set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val scheduled = cal.timeInMillis
        val grace = scheduled + TIME_GRACE_MINUTES * 60 * 1000L
        // Due only inside [scheduled, scheduled+grace) — the 5-minute tick
        // always lands inside that window at least once.
        return now >= scheduled && now < grace && lastFiredAt < scheduled
    }

    private fun batteryPercent(context: Context): Int? = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct in 0..100) pct else null
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private suspend fun fire(context: Context, routine: UserRoutine) {
        when (val a = routine.action) {
            is Action.Notify -> postNotification(
                context, routine, a.title.ifBlank { routine.name }, a.text, openScreen = null
            )
            is Action.CreateNote -> {
                val id = try {
                    val repo = JournalRepository(context)
                    repo.saveEntry(
                        JournalEntry(
                            title = a.title.ifBlank { routine.name },
                            body = a.body,
                            journalDate = System.currentTimeMillis()
                        ),
                        manualTags = listOf("rutina")
                    )
                } catch (_: Exception) {
                    null
                }
                postNotification(
                    context, routine,
                    "Nota creada: ${a.title.ifBlank { routine.name }}",
                    if (a.body.isBlank()) "Toca para verla" else a.body.take(90),
                    openDetailId = id
                )
            }
            is Action.OpenNews -> postNotification(
                context, routine, "Tus noticias están listas",
                "Toca para leer el resumen de tus fuentes",
                openScreen = MainActivity.SCREEN_NEWS
            )
            is Action.OpenAssistant -> postNotification(
                context, routine, "Asistente local",
                "Toca para hablar con Needle sin salir del teléfono",
                openScreen = MainActivity.SCREEN_ASSISTANT
            )
        }
    }

    private fun postNotification(
        context: Context,
        routine: UserRoutine,
        title: String,
        text: String,
        openScreen: String? = null,
        openDetailId: Long? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val app = context.applicationContext
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when {
                openDetailId != null -> putExtra(MainActivity.EXTRA_OPEN_DETAIL, openDetailId)
                openScreen != null -> putExtra(MainActivity.EXTRA_OPEN_SCREEN, openScreen)
            }
        }
        val requestCode = (routine.id.hashCode() and 0x7fffffff)
        val pending = PendingIntent.getActivity(
            app, requestCode, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nm.notify(3000 + (requestCode % 100000), notification)
        } catch (_: Exception) {
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rutinas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Automatizaciones creadas por ti (si X entonces Y)"
        }
        nm.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------
    // Display helpers (used by the UI)
    // ------------------------------------------------------------------

    fun triggerLabel(r: UserRoutine): String = when (val t = r.trigger) {
        is Trigger.Time -> String.format(Locale.getDefault(), "a las %02d:%02d", t.hour, t.minute)
        Trigger.ChargingOn -> "al conectar el cargador"
        Trigger.ChargingOff -> "al desconectar el cargador"
        is Trigger.BatteryBelow -> "cuando la batería baje de ${t.level}%"
        Trigger.AppOpen -> "al abrir la app"
    }

    fun actionLabel(r: UserRoutine): String = when (val a = r.action) {
        is Action.Notify -> "aviso «${a.title.ifBlank { r.name }}»"
        is Action.CreateNote -> "crear nota «${a.title.ifBlank { r.name }}»"
        Action.OpenNews -> "abrir las noticias"
        Action.OpenAssistant -> "abrir el asistente"
    }

    fun lastFiredLabel(r: UserRoutine): String? =
        if (r.lastFiredAt == 0L) null
        else "última vez: " + SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
            .format(Date(r.lastFiredAt))

    fun newId(): String = UUID.randomUUID().toString()
}

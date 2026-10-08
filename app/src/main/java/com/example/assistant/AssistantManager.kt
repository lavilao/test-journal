package com.example.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import com.example.contacts.ContactsHelper
import com.example.data.DeviceSearchManager
import com.example.data.model.AudioRecordItem
import com.example.data.model.JournalEntry
import com.example.data.model.LocalReminder
import com.example.data.local.AppDatabase
import com.example.location.SmartPlaces
import com.example.repository.JournalRepository
import com.example.telemetry.RealWeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** What the assistant decided to do with an utterance. */
sealed class AssistantIntent {
    /** Open an installed app: "abre whatsapp". */
    data class OpenApp(val appName: String) : AssistantIntent()

    /** Call a contact: "llama a maría". */
    data class CallContact(val contactName: String) : AssistantIntent()

    /** Device/phone search: "busca informe 2023". */
    data class SearchDevice(val query: String) : AssistantIntent()

    /** Web search: "busca en internet recetas veganas". */
    data class SearchWeb(val query: String) : AssistantIntent()

    /** Weather question: "qué tiempo hace". */
    data object Weather : AssistantIntent()

    /** Create a note: "apunta comprar leche". */
    data class CreateNote(val text: String) : AssistantIntent()

    /** Math question: "cuánto es 12 por 7". */
    data class Calculate(val expression: String) : AssistantIntent()

    /** Timer: "temporizador 10 minutos". */
    data class SetTimer(val minutes: Int) : AssistantIntent()

    /** Alarm: "alarma a las 7". */
    data class SetAlarm(val hour: Int, val minute: Int) : AssistantIntent()

    /** Flashlight: "enciende la linterna". */
    data class Flashlight(val on: Boolean) : AssistantIntent()

    /** Battery: "cuánta batería queda". */
    data object Battery : AssistantIntent()

    /** Open the camera lens (OCR/translate/scan): "escanea esto". */
    data object Lens : AssistantIntent()

    /** Translate text: "traduce hola al inglés". */
    data class Translate(val text: String, val targetLangHint: String?) : AssistantIntent()

    /** Close a background app: "cierra whatsapp". */
    data class CloseApp(val appName: String) : AssistantIntent()

    /** Open a pre-filled SMS: "mensaje a maría: ya voy". */
    data class SendMessage(val contactName: String, val message: String) : AssistantIntent()

    /** Create a task/reminder: "recuérdame llamar al dentista". */
    data class CreateTask(val title: String, val dueInMinutes: Int?) : AssistantIntent()

    /** Create a calendar event. */
    data class CreateEvent(
        val title: String,
        val startInMinutes: Int?,
        val durationMinutes: Int?,
        val location: String?
    ) : AssistantIntent()

    /** Read the next calendar event: "qué tengo hoy". */
    data object NextEvent : AssistantIntent()

    /** Steps today: "cuántos pasos llevo". */
    data object Steps : AssistantIntent()

    /** Volume control: "volumen al 50", "silencio". */
    data class Volume(val level: Int?, val mode: String?) : AssistantIntent()

    /** Record a voice memo attached to the journal. */
    data class RecordVoiceNote(val seconds: Int, val title: String) : AssistantIntent()

    /** Save the current location as a named place. */
    data class SaveCurrentPlace(val name: String) : AssistantIntent()

    /** Not understood — offer help. */
    data object Unknown : AssistantIntent()
}

/**
 * A LOCAL, on-device replacement for the retired Google Assistant: a
 * rule-based Spanish command parser that maps natural utterances to real
 * device actions. No cloud, no GenAI — every intent is executed with the
 * phone's own APIs (apps, contacts, calendar, weather cache, torch,
 * calculator, notes).
 */
object AssistantParser {

    fun parse(raw: String): AssistantIntent {
        val text = raw.trim().lowercase(Locale.getDefault())
        if (text.isBlank()) return AssistantIntent.Unknown

        // --- Flashlight ---
        if (matchesAny(text, listOf("enciende la linterna", "linterna on", "activa la linterna", "prende la linterna"))) {
            return AssistantIntent.Flashlight(true)
        }
        if (matchesAny(text, listOf("apaga la linterna", "linterna off", "desactiva la linterna"))) {
            return AssistantIntent.Flashlight(false)
        }

        // --- Battery ---
        if (containsAny(text, listOf("cuánta batería", "cuanta bateria", "nivel de batería", "cuánto de batería", "cuanto de bateria"))) {
            return AssistantIntent.Battery
        }

        // --- Weather ---
        if (containsAny(text, listOf("qué tiempo", "que tiempo", "clima", "temperatura", "va a llover", "hace frío", "hace calor"))) {
            return AssistantIntent.Weather
        }

        // --- Lens / scan ---
        if (containsAny(text, listOf("escanea", "escanear", "abre lens", "google lens", "ocr"))) {
            return AssistantIntent.Lens
        }

        // --- Timer: "temporizador 10 minutos" / "cronómetro 5 min" ---
        Regex("(?:temporizador|cronómetro|cronometro|timer)\\s+(\\d+)\\s*(?:min|minutos|minuto|segundos|seg|horas|hora)").find(text)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: return@let
            val unit = m.value.substringAfter(amount.toString(), "minuto").trim()
            val minutes = when {
                unit.startsWith("seg") -> (amount / 60.0).let { if (it < 1) 1 else it.toInt() }
                unit.startsWith("hora") -> amount * 60
                else -> amount
            }
            return AssistantIntent.SetTimer(minutes)
        }

        // --- Alarm: "alarma a las 7" / "alarma a las 7:30" ---
        Regex("alarma\\s+(?:a las|para las|las)?\\s*(\\d{1,2})(?::(\\d{2}))?").find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) return AssistantIntent.SetAlarm(hour, minute)
        }

        // --- Calculator: "cuánto es 12 por 7" / "calcula 4+4" ---
        val mathQuery = text.removePrefix("cuánto es ").removePrefix("cuanto es ")
            .removePrefix("calcula ").removePrefix("calcule ")
            .removePrefix("cuánto son ").removePrefix("cuanto son ")
        if (mathQuery != text) {
            val expr = normalizeSpanishMath(mathQuery)
            if (expr.matches(MATH_ALLOWED)) return AssistantIntent.Calculate(expr)
        }

        // --- Translate: "traduce hola al inglés" ---
        if (text.startsWith("traduce ") || text.startsWith("traducir ")) {
            val payload = text.removePrefix("traduce ").removePrefix("traducir ").trim()
            val targetLang = LANG_HINTS.firstOrNull { payload.endsWith("al ${it.first}") }?.second
            val cleaned = payload.substringBefore(" al ").substringBefore(" a ").trim()
            if (cleaned.isNotBlank()) return AssistantIntent.Translate(cleaned, targetLang)
        }

        // --- Call: "llama a maría" / "llamar a papa" ---
        Regex("^(?:llama|llamar|llamada|marca|marcar)\\s+(?:a\\s+)?(.+)$").find(text)?.let { m ->
            val who = m.groupValues[1].trim()
            if (who.isNotBlank()) return AssistantIntent.CallContact(who)
        }

        // --- Open app: "abre whatsapp" / "abrir cámara" ---
        Regex("^(?:abre|abrir|abrime|lanza|inicia|iniciar|ejecuta)\\s+(?:la\\s+|el\\s+)?(.+)$").find(text)?.let { m ->
            val what = m.groupValues[1].trim()
            if (what.isNotBlank()) return AssistantIntent.OpenApp(what)
        }

        // --- Create note: "apunta comprar leche" / "crea una nota ..." ---
        listOf(
            "apunta ", "apuntar ", "anota ", "anotar ", "crea una nota ",
            "crear una nota ", "nueva nota ", "toma nota ", "tomar nota "
        ).forEach { prefix ->
            if (text.startsWith(prefix)) {
                val body = text.removePrefix(prefix).trim()
                if (body.isNotBlank()) return AssistantIntent.CreateNote(body)
            }
        }

        // --- Close app: "cierra whatsapp" ---
        Regex("^(?:cierra|cerrar|kill|quita|quitar)\\s+(?:la\\s+|el\\s+)?(.+)$").find(text)?.let { m ->
            val what = m.groupValues[1].trim()
            if (what.isNotBlank()) return AssistantIntent.CloseApp(what)
        }

        // --- Volume: "volumen al 50" / "silencio" / "vibración" ---
        if (matchesAny(text, listOf("silencio", "silenciar", "modo silencio", "mute"))) {
            return AssistantIntent.Volume(null, "silencio")
        }
        if (matchesAny(text, listOf("vibración", "vibracion", "modo vibración", "modo vibracion"))) {
            return AssistantIntent.Volume(null, "vibracion")
        }
        Regex("(?:volumen|pon el volumen)(?:\\s+al)?\\s+(\\d{1,3})").find(text)?.let { m ->
            val level = m.groupValues[1].toIntOrNull() ?: return@let
            if (level in 0..100) return AssistantIntent.Volume(level, null)
        }

        // --- Steps ---
        if (containsAny(text, listOf("cuántos pasos", "cuantos pasos", "pasos de hoy", "pasos hoy", "cuánto he caminado", "cuanto he caminado"))) {
            return AssistantIntent.Steps
        }

        // --- Next event ---
        if (containsAny(text, listOf("próximo evento", "proximo evento", "siguiente evento", "qué tengo hoy", "que tengo hoy", "agenda de hoy", "qué tengo mañana", "que tengo mañana"))) {
            return AssistantIntent.NextEvent
        }

        // --- SMS: "mensaje a maría: texto" ---
        Regex("^(?:mensaje|mensajito|manda|mandar|envía|enviar)\\s+(?:un\\s+)?(?:mensaje|sms|mensajito)?\\s*(?:a\\s+)?(.+)$").find(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            val parts = rest.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) {
                return AssistantIntent.SendMessage(parts[0].trim(), parts[1].trim())
            }
            if (rest.isNotBlank() && (text.startsWith("mensaje a") || text.startsWith("manda un mensaje"))) {
                return AssistantIntent.SendMessage(rest, "")
            }
        }

        // --- Task: "recuérdame X" / "tarea X" ---
        listOf("recuérdame ", "recuerdame ", "recuérdame que ", "recuerdame que ", "tarea ", "nueva tarea ", "apúntame ").forEach { prefix ->
            if (text.startsWith(prefix)) {
                val body = text.removePrefix(prefix).trim()
                if (body.isNotBlank()) return AssistantIntent.CreateTask(body, null)
            }
        }

        // --- Event: "evento X a las HH:MM" / "evento X en N minutos" ---
        Regex("^evento\\s+(.+)$").find(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            val inMin = Regex("(?:en|dentro de)\\s+(\\d+)\\s*(min|minutos|hora|horas)").find(rest)
            val at = Regex("(?:a las|para las|las)\\s+(\\d{1,2})(?::(\\d{2}))?").find(rest)
            val title = rest.substringBefore(" en ").substringBefore(" a las ").substringBefore(" para las ").trim()
            if (title.isNotBlank()) {
                when {
                    inMin != null -> {
                        val amount = inMin.groupValues[1].toIntOrNull() ?: return@let
                        val unit = inMin.value.substringAfter(amount.toString(), "minuto").trim()
                        val minutes = if (unit.startsWith("hora")) amount * 60 else amount
                        return AssistantIntent.CreateEvent(title, minutes, 60, null)
                    }
                    at != null -> {
                        val hour = at.groupValues[1].toIntOrNull() ?: return@let
                        val minute = at.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                        if (hour in 0..23 && minute in 0..59) {
                            val now = java.util.Calendar.getInstance()
                            val target = (now.clone() as java.util.Calendar).apply {
                                set(java.util.Calendar.HOUR_OF_DAY, hour)
                                set(java.util.Calendar.MINUTE, minute)
                                set(java.util.Calendar.SECOND, 0)
                                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                            val inMinutes = ((target.timeInMillis - now.timeInMillis) / 60_000L).toInt()
                            return AssistantIntent.CreateEvent(title, inMinutes, 60, null)
                        }
                    }
                }
            }
        }

        // --- Save current place: "guarda este lugar como gym" ---
        Regex("^guarda(?:r)?\\s+(?:este|el|esta)?\\s*lugar(?:\\s+como)?\\s+(.+)$").find(text)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotBlank()) return AssistantIntent.SaveCurrentPlace(name)
        }

        // --- Web search: "busca en internet ..." / "busca en google ..." ---
        Regex("^(?:busca|buscar|consulta)\\s+(?:en\\s+)?(?:internet|google|la web|el navegador|online)\\s+(.+)$").find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return AssistantIntent.SearchWeb(q)
        }

        // --- Device search: "busca ..." / "dónde está ..." ---
        Regex("^(?:busca|buscar|encuentra|encontrar|dónde está|donde esta)\\s+(?:el\\s+|la\\s+|los\\s+|las\\s+)?(.+)$").find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return AssistantIntent.SearchDevice(q)
        }

        return AssistantIntent.Unknown
    }

    private fun matchesAny(text: String, phrases: List<String>): Boolean =
        phrases.any { text == it || text.startsWith("$it ") }

    private fun containsAny(text: String, tokens: List<String>): Boolean =
        tokens.any { text.contains(it) }

    private val MATH_ALLOWED = Regex("^[0-9+\\-*/().^ ]+$")

    private val LANG_HINTS = listOf(
        "inglés" to "en", "ingles" to "en", "english" to "en",
        "español" to "es", "espanol" to "es", "spanish" to "es",
        "francés" to "fr", "frances" to "fr", "french" to "fr",
        "alemán" to "de", "aleman" to "de", "german" to "de",
        "italiano" to "it", "portugués" to "pt", "portugues" to "pt",
        "japonés" to "ja", "japones" to "ja", "chino" to "zh",
        "coreano" to "ko", "ruso" to "ru"
    )

    /** "12 por 7 más 3" -> "12 * 7 + 3" (safe, digits/operators only). */
    private fun normalizeSpanishMath(input: String): String {
        return input
            .replace(" multiplicado por ", " * ")
            .replace(" por ", " * ")
            .replace(" dividido por ", " / ")
            .replace(" dividido ", " / ")
            .replace(" más ", " + ")
            .replace(" mas ", " + ")
            .replace(" menos ", " - ")
            .replace(" elevado a ", " ^ ")
            .replace(',', '.')
            .replace('×', '*')
            .replace('÷', '/')
            .replace('x', '*')
            .trim()
    }

    /** Evaluates a sanitized arithmetic expression. Null when unsafe/invalid. */
    fun safeEvaluate(expression: String): Double? {
        if (!expression.matches(Regex("^[0-9+\\-*/().^ ]+$"))) return null
        return try {
            evaluateArithmetic(expression)
        } catch (_: Exception) {
            null
        }
    }

    /** Tiny shunting-yard arithmetic evaluator (no scripting engine needed). */
    private fun evaluateArithmetic(expr: String): Double? {
        val tokens = Regex("\\d+\\.?\\d*|[+\\-*/^()]").findAll(expr.replace(" ", "")).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val output = ArrayDeque<Double>()
        val ops = ArrayDeque<Char>()
        var expectOperand = true

        fun applyOp(op: Char) {
            if (output.size < 2) throw IllegalArgumentException("bad expr")
            val b = output.removeLast()
            val a = output.removeLast()
            output.addLast(
                when (op) {
                    '+' -> a + b
                    '-' -> a - b
                    '*' -> a * b
                    '/' -> if (b == 0.0) throw IllegalArgumentException("div0") else a / b
                    '^' -> Math.pow(a, b)
                    else -> throw IllegalArgumentException("bad op")
                }
            )
        }

        fun precedence(op: Char): Int = when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            '^' -> 3
            else -> 0
        }

        for (token in tokens) {
            when {
                token.first().isDigit() || token.first() == '.' -> {
                    output.addLast(token.toDouble())
                    expectOperand = false
                }
                token == "(" -> {
                    ops.addLast('(')
                    expectOperand = true
                }
                token == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != '(') applyOp(ops.removeLast())
                    if (ops.isEmpty()) return null
                    ops.removeLast()
                    expectOperand = false
                }
                token.length == 1 && "+-*/^".contains(token.first()) -> {
                    if (expectOperand && token.first() == '-') {
                        // unary minus -> 0 - x
                        output.addLast(0.0)
                    }
                    while (ops.isNotEmpty() && ops.last() != '(' && precedence(ops.last()) >= precedence(token.first()) && token.first() != '^') {
                        applyOp(ops.removeLast())
                    }
                    ops.addLast(token.first())
                    expectOperand = true
                }
                else -> return null
            }
        }
        while (ops.isNotEmpty()) {
            val op = ops.removeLast()
            if (op == '(') return null
            applyOp(op)
        }
        return if (output.size == 1) output.last() else null
    }
}

/**
 * Executes parsed assistant intents against the real device APIs.
 */
class AssistantManager(private val context: Context) {

    private val searchManager = DeviceSearchManager(context)

    suspend fun findApp(name: String) = searchManager.findAppByName(name)

    suspend fun searchContact(name: String) = searchManager.searchContacts(name).firstOrNull { c ->
        c.displayName.lowercase(Locale.getDefault()).contains(name.lowercase(Locale.getDefault()))
    }

    fun launchApp(packageName: String) = searchManager.launchApp(packageName)

    fun dialContact(phone: String) = ContactsHelper.dialContact(context, phone)

    fun webSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun startTimer(minutes: Int) {
        try {
            // android.provider.AlarmClock constants (NOT Intent.* — those do
            // not exist, which broke the previous build).
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Mnemosyne")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Clock apps without SET_TIMER support still accept SHOW_ALARMS
            try {
                val fallback = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun setAlarm(hour: Int, minute: Int) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /** Torch control via CameraManager — works on any Android 6+ device. */
    fun setFlashlight(on: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cameraManager.setTorchMode(cameraId, on)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readBatteryPercent(): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
            val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (percent in 0..100) percent else null
        } catch (_: Exception) {
            null
        }
    }

    fun describeWeather(weather: RealWeatherData): String {
        val temp = weather.temperature ?: return "Todavía no tengo una lectura del clima. Elige tu ciudad en la app."
        val place = weather.locationName ?: ""
        val condition = weather.conditionText ?: ""
        val ageMinutes = weather.lastUpdated?.let { ((System.currentTimeMillis() - it) / 60_000L).toInt() } ?: -1
        val freshness = when {
            ageMinutes < 0 -> ""
            ageMinutes == 0 -> " (ahora)"
            ageMinutes < 60 -> " (hace $ageMinutes min)"
            else -> " (hace ${ageMinutes / 60} h)"
        }
        return buildString {
            append("$temp grados")
            if (condition.isNotBlank()) append(", $condition")
            if (place.isNotBlank()) append(" en $place")
            append("$freshness.")
        }
    }

    suspend fun formatBattery(): String = withContext(Dispatchers.Default) {
        val percent = readBatteryPercent() ?: return@withContext "No pude leer la batería."
        "La batería está al $percent%."
    }

    // ------------------------------------------------------------------
    // New tool executors (events/tasks/messages/apps/places/volume…)
    // ------------------------------------------------------------------

    /** Kills an app's background processes (best-effort, no root). */
    suspend fun closeApp(name: String): String = withContext(Dispatchers.IO) {
        val app = searchManager.findAppByName(name)
        if (app == null) {
            return@withContext "No encontré una app llamada \"$name\"."
        }
        return@withContext try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.killBackgroundProcesses(app.packageName)
            "Cerré ${app.appName} en segundo plano."
        } catch (e: Exception) {
            "No pude cerrar ${app.appName}: ${e.message}"
        }
    }

    /** Opens the SMS app with the message pre-filled (no SMS permission needed). */
    suspend fun sendMessage(contactName: String, message: String): String =
        withContext(Dispatchers.IO) {
            val contact = searchManager.searchContacts(contactName).firstOrNull { c ->
                c.displayName.lowercase(Locale.getDefault()).contains(contactName.lowercase(Locale.getDefault()))
            }
            if (contact?.phoneNumber.isNullOrBlank()) {
                return@withContext "No encontré a \"$contactName\" en tus contactos (revisa el permiso)."
            }
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact!!.phoneNumber}")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Mensaje para ${contact.displayName} listo para enviar${if (message.isBlank()) "" else ": \"$message\""}."
            } catch (e: Exception) {
                "No pude abrir la app de mensajes: ${e.message}"
            }
        }

    /** Creates a real task in the app's reminder database. */
    suspend fun createTask(title: String, dueInMinutes: Int?): String =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val due = System.currentTimeMillis() + (dueInMinutes?.times(60_000L) ?: 24 * 3600_000L)
                AppDatabase.getInstance(context).localReminderDao().insertReminder(
                    LocalReminder(title = title.take(80), dueTimestamp = due)
                )
                if (dueInMinutes != null) {
                    "Tarea \"$title\" creada, vence en $dueInMinutes min."
                } else {
                    "Tarea \"$title\" creada para mañana."
                }
            } catch (e: Exception) {
                "No pude guardar la tarea: ${e.message}"
            }
        }

    /** Creates a calendar event (with location + 10-min reminder). */
    suspend fun createEvent(
        title: String,
        startInMinutes: Int?,
        durationMinutes: Int?,
        location: String?
    ): String = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis() + (startInMinutes?.times(60_000L) ?: 3600_000L)
        val cal = com.example.data.CalendarSyncManager(context)
        val uri = cal.addEventToCalendar(
            title = title.take(80),
            description = "Creado por el asistente de Mnemosyne",
            startMillis = start,
            durationMinutes = durationMinutes ?: 60,
            location = location
        )
        if (uri != null) {
            val whenText = relativeWhen(start)
            "Evento \"$title\" creado $whenText${if (location.isNullOrBlank()) "" else " en $location"}."
        } else {
            "No pude crear el evento (falta el permiso de calendario)."
        }
    }

    /** Reads the next upcoming calendar event, with location if any. */
    suspend fun describeNextEvent(): String = withContext(Dispatchers.IO) {
        val cal = com.example.data.CalendarSyncManager(context)
        if (!cal.hasCalendarPermission()) {
            return@withContext "Necesito el permiso de calendario para leer tu agenda."
        }
        val next = cal.getUpcomingEvents(limit = 1).firstOrNull()
            ?: return@withContext "No tienes eventos en los próximos 3 días."
        val whenText = relativeWhen(next.startMillis)
        val place = next.location?.takeIf { it.isNotBlank() }?.let { " en $it" } ?: ""
        "Tu próximo evento: \"${next.title}\" $whenText$place."
    }

    /** Steps today, preferring Health Connect when available. */
    suspend fun stepsToday(): String = withContext(Dispatchers.IO) {
        val hc = try {
            com.example.health.HealthConnectManager.todaySteps(context)
        } catch (_: Exception) {
            null
        }
        val steps = hc?.toInt() ?: com.example.telemetry.DeviceLifeHubManager.cachedStepsToday(context)
        if (steps > 0) {
            "Hoy llevas $steps pasos."
        } else {
            "Aún no tengo pasos registrados hoy (camina un poco con el teléfono encima)."
        }
    }

    /** Volume / ringer control. */
    fun setVolume(level: Int?, mode: String?): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "No pude acceder al audio del dispositivo."
        return try {
            when (mode) {
                "silencio" -> {
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "Teléfono en silencio."
                }
                "vibracion" -> {
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "Teléfono en vibración."
                }
                "normal" -> {
                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Sonido normal."
                }
                else -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val target = ((level ?: 50) * max / 100).coerceIn(0, max)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    "Volumen al ${(target * 100 / max)}%."
                }
            }
        } catch (e: Exception) {
            "No pude cambiar el volumen: ${e.message}"
        }
    }

    /** Records [seconds] of audio and attaches it to the journal as a note. */
    suspend fun recordVoiceNote(seconds: Int, title: String): String =
        withContext(Dispatchers.IO) {
            val safeSeconds = seconds.coerceIn(5, 120)
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            ) {
                return@withContext "Necesito el permiso de micrófono para grabar."
            }
            val sampleRate = 16_000
            val total = sampleRate * safeSeconds
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@withContext "Este dispositivo no permite grabar ahora."
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, sampleRate * 2)
                )
            } catch (e: Exception) {
                return@withContext "No pude abrir el micrófono: ${e.message}"
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@withContext "El micrófono está ocupado."
            }
            val pcm = ShortArray(total)
            var filled = 0
            try {
                record.startRecording()
                while (filled < total) {
                    val n = record.read(pcm, filled, total - filled)
                    if (n <= 0) break
                    filled += n
                }
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
            }
            if (filled < sampleRate) return@withContext "Grabé demasiado poco audio."

            val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
            val file = File(dir, "memo_${System.currentTimeMillis()}.wav")
            writeWav(file, pcm.copyOf(filled), sampleRate)

            val entry = JournalEntry(
                title = title,
                body = "Nota de voz grabada por el asistente ($safeSeconds s).",
                journalDate = System.currentTimeMillis()
            )
            val audioItem = AudioRecordItem(
                entryId = 0,
                title = title,
                filePath = file.absolutePath,
                durationMs = (filled.toLong() * 1000L) / sampleRate,
                transcriptionStatus = "PENDING"
            )
            return@withContext try {
                JournalRepository(context).saveEntry(entry, audioRecords = listOf(audioItem))
                "Nota de voz \"$title\" guardada (${safeSeconds} s) en el diario."
            } catch (e: Exception) {
                "Grabé el audio pero no pude adjuntarlo: ${e.message}"
            }
        }

    /** Saves the current location as a named place (SmartPlaces). */
    suspend fun saveCurrentPlace(name: String): String = withContext(Dispatchers.IO) {
        if (!SmartPlaces.hasLocationPermission(context)) {
            return@withContext "Necesito el permiso de ubicación para guardar lugares."
        }
        val loc = SmartPlaces.freshLocation(context)
            ?: return@withContext "No pude obtener una ubicación ahora."
        SmartPlaces.addPlace(context, name, loc.latitude, loc.longitude, "Guardado por el asistente")
        "Lugar \"$name\" guardado en (${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)})."
    }

    /** Minimal WAV writer: 44-byte header + little-endian PCM16. */
    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        FileOutputStream(file).use { out ->
            val dataLen = pcm.size * 2
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(1) // mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataLen)
            out.write(header.array())
            val bytes = java.nio.ByteBuffer.allocate(dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { bytes.putShort(it) }
            out.write(bytes.array())
        }
    }

    /** "en 25 min" / "hoy a las 18:30" / "mañana a las 9:00". */
    private fun relativeWhen(target: Long): String {
        val diffMin = (target - System.currentTimeMillis()) / 60_000L
        return when {
            diffMin < 1 -> "ahora"
            diffMin < 90 -> "en $diffMin min"
            else -> {
                val fmt = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                val cal = java.util.Calendar.getInstance()
                val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = target }
                val sameDay = cal.get(java.util.Calendar.DAY_OF_YEAR) ==
                    targetCal.get(java.util.Calendar.DAY_OF_YEAR)
                val prefix = if (sameDay) "hoy" else "mañana"
                "$prefix a las ${fmt.format(java.util.Date(target))}"
            }
        }
    }
}

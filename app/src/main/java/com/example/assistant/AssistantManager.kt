package com.example.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import com.example.contacts.ContactsHelper
import com.example.data.DeviceSearchManager
import com.example.telemetry.RealWeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}

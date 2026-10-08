package com.example.ai.needle

import android.content.Context
import com.example.assistant.AssistantIntent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One tool call chosen by Needle: name + typed arguments. */
data class NeedleCall(
    val name: String,
    val args: JSONObject
)

/** Parsed Needle response. */
data class NeedleParsed(
    val calls: List<NeedleCall>,
    val confidence: Float,
    val reasoning: String?,
    val audioText: String?,
    val peakRamMb: Double?,
    val decodeTps: Double?
)

/**
 * One entry of the assistant's tool catalogue, as shown in the tools menu.
 *
 * CONTEXT BLOAT, researched: Needle 3 is a tiny model (8–29 MB) with a small
 * context window — every declared tool consumes tokens of that window in the
 * static prefix, and small models degrade measurably as the prompt grows
 * (needle_init itself fails and reports the token count when the catalogue
 * exceeds the window; the engine docs recommend "tool retrieval" to avoid
 * declaring everything at once). That is exactly what per-tool gating gives
 * us here: disabled tools never enter the prompt, so the router stays sharp
 * and the weekly-report / journal tools only cost tokens while enabled.
 */
data class ToolSpec(
    val id: String,
    val label: String,
    val hint: String,
    val defaultEnabled: Boolean = true
)

/**
 * Bridges the local Cactus Needle 3 model with the app's assistant:
 * declares the tools the assistant can execute, parses the model's
 * function-call JSON back into intents, applies an honest confidence policy,
 * and gates individual tools on/off to keep the model's context window lean.
 */
object NeedleTools {

    /** Minimum calibrated confidence to act on a Needle routing decision. */
    const val MIN_CONFIDENCE = 0.5f

    private const val GATE_PREFS = "needle_tool_prefs"

    // ------------------------------------------------------------------
    // Tool catalogue (what we declare to the engine + what the menu shows)
    // ------------------------------------------------------------------

    /**
     * The full catalogue. Default-enabled tools mirror the previous
     * behaviour plus the new high-value ones; a few power tools start off
     * (they can be switched on in the menu) to keep the static prefix small.
     */
    val TOOL_CATALOG: List<ToolSpec> = listOf(
        ToolSpec("abrir_app", "Abrir apps", "«abre whatsapp»"),
        ToolSpec("cerrar_app", "Cerrar apps", "«cierra whatsapp»", defaultEnabled = false),
        ToolSpec("llamar_contacto", "Llamar", "«llama a maría»"),
        ToolSpec("mandar_mensaje", "Mensajes SMS", "«mensaje a papa: ya salgo»", defaultEnabled = false),
        ToolSpec("buscar_en_telefono", "Buscar en el teléfono", "«busca la factura»"),
        ToolSpec("buscar_en_web", "Buscar en internet", "«busca en internet…»"),
        ToolSpec("buscar_en_notas", "Búsqueda semántica del diario", "«busca en mis notas…»"),
        ToolSpec("consultar_diario", "Preguntar al diario (memoria RAG)", "«¿qué escribí sobre…?»"),
        ToolSpec("clima_hoy", "Clima", "«qué tiempo hace»"),
        ToolSpec("crear_nota", "Crear notas", "«apunta comprar leche»"),
        ToolSpec("crear_tarea", "Crear tareas", "«recuérdame llamar al dentista»"),
        ToolSpec("crear_evento", "Crear eventos", "«evento dentista el jueves a las 10»", defaultEnabled = false),
        ToolSpec("siguiente_evento", "Próximo evento", "«qué tengo hoy»"),
        ToolSpec("calcular", "Calculadora", "«cuánto es 12 por 7»"),
        ToolSpec("crear_temporizador", "Temporizadores", "«temporizador 10 minutos»"),
        ToolSpec("crear_alarma", "Alarmas", "«alarma a las 7»"),
        ToolSpec("linterna", "Linterna", "«enciende la linterna»"),
        ToolSpec("nivel_bateria", "Batería", "«cuánta batería queda»"),
        ToolSpec("pasos_hoy", "Pasos de hoy", "«cuántos pasos llevo»"),
        ToolSpec("volumen", "Volumen y silencio", "«volumen al 50», «silencio»"),
        ToolSpec("grabar_audio", "Nota de voz", "«graba mi voz 30 segundos»", defaultEnabled = false),
        ToolSpec("abrir_escaner_lens", "Escáner Lens", "«escanea esto»"),
        ToolSpec("traducir_texto", "Traducción", "«traduce hola al inglés»"),
        ToolSpec("guardar_lugar", "Guardar lugar actual", "«guarda este lugar como gym»", defaultEnabled = false),
        ToolSpec("resumen_habitos", "Brief del día", "usado por la app al redactar el brief"),
        ToolSpec("resumen_semanal", "Informe semanal", "usado por la app el domingo")
    )

    /** Compact fallback set used when the full catalogue overflows context. */
    private val CORE_TOOLS = setOf(
        "abrir_app", "llamar_contacto", "crear_nota", "crear_tarea",
        "crear_alarma", "crear_temporizador", "linterna", "buscar_en_notas"
    )

    // ------------------------------------------------------------------
    // Gating (the tools menu reads/writes these)
    // ------------------------------------------------------------------

    fun isToolEnabled(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE)
        val spec = TOOL_CATALOG.firstOrNull { it.id == id }
        return prefs.getBoolean("tool_$id", spec?.defaultEnabled ?: false)
    }

    fun setToolEnabled(context: Context, id: String, enabled: Boolean) {
        context.getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("tool_$id", enabled).apply()
    }

    fun resetGating(context: Context) {
        context.getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun enabledIds(context: Context): Set<String> =
        TOOL_CATALOG.filter { isToolEnabled(context, it.id) }.map { it.id }.toSet()

    // ------------------------------------------------------------------
    // Tool catalogue (what we declare to the engine)
    // ------------------------------------------------------------------

    private fun prop(type: String, description: String) =
        JSONObject().put("type", type).put("description", description)

    private fun tool(name: String, description: String, props: Map<String, JSONObject>, required: List<String> = props.keys.toList()) =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject(props))
                    .put("required", JSONArray(required))
            )

    /** Builds the JSON declaration for ONE tool by id (null when unknown). */
    private fun buildTool(id: String): JSONObject? {
        fun prop(type: String, description: String) =
            JSONObject().put("type", type).put("description", description)
        return when (id) {
            "abrir_app" -> tool(
                "abrir_app",
                "Abre una aplicación instalada en el teléfono por su nombre habitual.",
                mapOf("app" to prop("string", "Nombre de la app, p. ej. whatsapp, cámara, calendario, youtube"))
            )

            "cerrar_app" -> tool(
                "cerrar_app",
                "Cierra una aplicación que está en segundo plano.",
                mapOf("app" to prop("string", "Nombre de la app a cerrar"))
            )

            "llamar_contacto" -> tool(
                "llamar_contacto",
                "Inicia una llamada telefónica a un contacto de la agenda.",
                mapOf("nombre" to prop("string", "Nombre de la persona tal como la llama el usuario"))
            )

            "mandar_mensaje" -> tool(
                "mandar_mensaje",
                "Abre un SMS ya redactado para un contacto, listo para enviar.",
                mapOf(
                    "nombre" to prop("string", "Nombre del contacto destinatario"),
                    "texto" to prop("string", "Texto completo del mensaje")
                )
            )

            "buscar_en_telefono" -> tool(
                "buscar_en_telefono",
                "Busca notas, archivos y contactos guardados en el propio teléfono.",
                mapOf("consulta" to prop("string", "Texto a buscar"))
            )

            "buscar_en_web" -> tool(
                "buscar_en_web",
                "Busca en internet y abre el navegador con el resultado.",
                mapOf("consulta" to prop("string", "Consulta para el buscador web"))
            )

            "buscar_en_notas" -> tool(
                "buscar_en_notas",
                "Busca en las NOTAS DEL DIARIO del usuario y devuelve las relevantes para una consulta semántica (no literal).",
                mapOf(
                    "consulta" to prop("string", "La intención de búsqueda reformulada con claridad"),
                    "ids_relevantes" to JSONObject()
                        .put("type", "array")
                        .put("description", "Ids de los candidatos que de verdad responden a la consulta, del MÁS al MENOS relevante")
                        .put("items", JSONObject().put("type", "integer"))
                )
            )

            "consultar_diario" -> tool(
                "consultar_diario",
                "Responde una pregunta sobre el diario personal usando SOLO los fragmentos entregados en el mensaje.",
                mapOf(
                    "texto" to prop(
                        "string",
                        "Respuesta breve en español basada únicamente en los fragmentos; si no la saben, dilo"
                    )
                )
            )

            "clima_hoy" -> tool(
                "clima_hoy",
                "Lee el clima actual de la ciudad configurada en la app.",
                emptyMap()
            )

            "crear_nota" -> tool(
                "crear_nota",
                "Guarda una nota de texto en el diario.",
                mapOf("texto" to prop("string", "Contenido completo de la nota"))
            )

            "crear_tarea" -> tool(
                "crear_tarea",
                "Crea una tarea pendiente con título y, si se dice, un plazo.",
                mapOf(
                    "titulo" to prop("string", "Título corto de la tarea"),
                    "en_minutos" to prop(
                        "integer",
                        "Minutos desde AHORA hasta el vencimiento; omítelo si no hay plazo"
                    )
                )
            )

            "crear_evento" -> tool(
                "crear_evento",
                "Crea un evento en el calendario del teléfono.",
                mapOf(
                    "titulo" to prop("string", "Título del evento"),
                    "en_minutos" to prop(
                        "integer",
                        "Minutos desde AHORA hasta el inicio del evento; usa esto si el usuario dice «en una hora», «mañana a las 9»"
                    ),
                    "duracion_minutos" to prop("integer", "Duración del evento en minutos; si no se dice, 60"),
                    "lugar" to prop("string", "Lugar del evento si el usuario lo menciona")
                )
            )

            "siguiente_evento" -> tool(
                "siguiente_evento",
                "Dice cuál es el próximo evento del calendario, con su hora y lugar.",
                emptyMap()
            )

            "calcular" -> tool(
                "calcular",
                "Evalúa una expresión aritmética.",
                mapOf(
                    "expresion" to prop(
                        "string",
                        "Expresión con solo números y los operadores + - * / ( ) . ^ — por ejemplo 12*7+3"
                    )
                )
            )

            "crear_temporizador" -> tool(
                "crear_temporizador",
                "Crea un temporizador en la app de reloj.",
                mapOf(
                    "minutos" to prop(
                        "integer",
                        "Duración TOTAL del temporizador en MINUTOS ya convertida: «30 segundos» = 1, «5 minutos» = 5, «2 horas» = 120"
                    )
                )
            )

            "crear_alarma" -> tool(
                "crear_alarma",
                "Crea una alarma para una hora concreta del día.",
                mapOf(
                    "hora" to prop("integer", "Hora del día 0-23"),
                    "minuto" to prop("integer", "Minuto 0-59; si no se dice, 0")
                )
            )

            "linterna" -> tool(
                "linterna",
                "Enciende o apaga la linterna del teléfono.",
                mapOf("encender" to prop("boolean", "true para encender, false para apagar"))
            )

            "nivel_bateria" -> tool(
                "nivel_bateria",
                "Lee el porcentaje de batería restante.",
                emptyMap()
            )

            "pasos_hoy" -> tool(
                "pasos_hoy",
                "Lee los pasos caminados hoy según el teléfono.",
                emptyMap()
            )

            "volumen" -> tool(
                "volumen",
                "Cambia el volumen del teléfono o el modo de sonido.",
                mapOf(
                    "nivel" to prop("integer", "Nivel de volumen 0-100; omítelo si el usuario pide un modo"),
                    "modo" to prop("string", "«silencio», «vibracion» o «normal» si el usuario pide un modo")
                )
            )

            "grabar_audio" -> tool(
                "grabar_audio",
                "Graba una nota de voz del entorno y la adjunta al diario.",
                mapOf(
                    "segundos" to prop("integer", "Duración de la grabación en segundos (5-120)"),
                    "titulo" to prop("string", "Título de la nota de voz; si no se dice, «Nota de voz»")
                )
            )

            "abrir_escaner_lens" -> tool(
                "abrir_escaner_lens",
                "Abre la cámara inteligente (Lens) para escanear texto, traducir con la cámara o leer códigos.",
                emptyMap()
            )

            "traducir_texto" -> tool(
                "traducir_texto",
                "Traduce un texto a otro idioma con el traductor local.",
                mapOf(
                    "texto" to prop("string", "Texto original a traducir"),
                    "idioma_destino" to prop(
                        "string",
                        "Código del idioma destino: en, es, fr, de, it, pt, ja, zh, ko o ru"
                    )
                )
            )

            "guardar_lugar" -> tool(
                "guardar_lugar",
                "Guarda la ubicación ACTUAL como un lugar con nombre (casa, gym, trabajo…).",
                mapOf("nombre" to prop("string", "Nombre para el lugar"))
            )

            "resumen_habitos" -> tool(
                "resumen_habitos",
                "Redacta el resumen diario de rutinas del usuario a partir de estadísticas ya calculadas por la app.",
                mapOf(
                    "texto" to prop(
                        "string",
                        "Resumen breve (3-6 frases) en español, útil y concreto, basado SOLO en las estadísticas entregadas"
                    )
                )
            )

            "resumen_semanal" -> tool(
                "resumen_semanal",
                "Redacta el informe semanal del usuario a partir de estadísticas ya calculadas por la app.",
                mapOf(
                    "texto" to prop(
                        "string",
                        "Informe breve (4-8 frases) en español, con tono cercano, basado SOLO en las estadísticas entregadas"
                    )
                )
            )

            else -> null
        }
    }

    /** JSON array with ONLY the enabled tools (context-bloat gating). */
    fun buildToolsJson(context: Context): String {
        val tools = JSONArray()
        enabledIds(context).forEach { id ->
            buildTool(id)?.let { tools.put(it) }
        }
        return tools.toString()
    }

    /** Compact fallback catalogue for tiny context windows. */
    fun buildCoreToolsJson(): String {
        val tools = JSONArray()
        CORE_TOOLS.forEach { id ->
            buildTool(id)?.let { tools.put(it) }
        }
        return tools.toString()
    }

    /** Session facts given to the engine alongside the tools. */
    fun buildSystemPrompt(): String {
        val now = java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es"))
            .format(java.util.Date())
        return "Eres el enrutador de comandos del asistente local de Mnemosyne en un teléfono " +
            "Android. Hoy es $now. El usuario habla en español. Escucha lo que pide y elige la " +
            "herramienta correcta rellenando todos los argumentos. Si nada encaja con lo pedido, " +
            "devuelve una lista de llamadas vacía en lugar de adivinar. Para buscar o preguntar " +
            "por el diario del usuario usa buscar_en_notas (entendiendo sinónimos e intención, no " +
            "solo palabras exactas); para buscar en archivos/contactos del teléfono usa " +
            "buscar_en_telefono. Cuando el mensaje traiga estadísticas y pida un resumen, llama a " +
            "resumen_habitos o resumen_semanal con el texto redactado."
    }

    // ------------------------------------------------------------------
    // Response parsing
    // ------------------------------------------------------------------

    fun parseResponse(raw: String): NeedleParsed? {
        return try {
            val root = JSONObject(raw)
            val calls = mutableListOf<NeedleCall>()
            val arr = root.optJSONArray("function_calls") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val call = arr.optJSONObject(i) ?: continue
                val name = call.optString("name")
                if (name.isNotBlank()) {
                    calls.add(NeedleCall(name, call.optJSONObject("arguments") ?: JSONObject()))
                }
            }
            NeedleParsed(
                calls = calls,
                confidence = root.optDouble("confidence", 0.0).toFloat(),
                reasoning = root.optString("reasoning").takeIf { it.isNotBlank() },
                audioText = root.optString("audio_text").takeIf { it.isNotBlank() },
                peakRamMb = root.optDouble("peak_ram_mb"),
                decodeTps = root.optDouble("decode_tps")
            )
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Intent mapping
    // ------------------------------------------------------------------

    fun callToIntent(call: NeedleCall): AssistantIntent? {
        val a = call.args
        return when (call.name) {
            "abrir_app" -> a.optString("app").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.OpenApp(it) }

            "cerrar_app" -> a.optString("app").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.CloseApp(it) }

            "llamar_contacto" -> a.optString("nombre").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.CallContact(it) }

            "mandar_mensaje" -> {
                val who = a.optString("nombre").takeIf { it.isNotBlank() } ?: return null
                AssistantIntent.SendMessage(who, a.optString("texto").ifBlank { "" })
            }

            "buscar_en_telefono" -> a.optString("consulta").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.SearchDevice(it) }

            "buscar_en_web" -> a.optString("consulta").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.SearchWeb(it) }

            "clima_hoy" -> AssistantIntent.Weather

            "crear_nota" -> a.optString("texto").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.CreateNote(it) }

            "crear_tarea" -> {
                val title = a.optString("titulo").takeIf { it.isNotBlank() } ?: return null
                val inMinutes = a.optInt("en_minutos", 0)
                AssistantIntent.CreateTask(title, if (inMinutes > 0) inMinutes else null)
            }

            "crear_evento" -> {
                val title = a.optString("titulo").takeIf { it.isNotBlank() } ?: return null
                val inMinutes = a.optInt("en_minutos", 0)
                val duration = a.optInt("duracion_minutos", 60).coerceIn(5, 24 * 60)
                AssistantIntent.CreateEvent(
                    title = title,
                    startInMinutes = if (inMinutes > 0) inMinutes else null,
                    durationMinutes = duration,
                    location = a.optString("lugar").takeIf { it.isNotBlank() }
                )
            }

            "siguiente_evento" -> AssistantIntent.NextEvent

            "pasos_hoy" -> AssistantIntent.Steps

            "volumen" -> {
                val level = a.optInt("nivel", -1)
                val mode = a.optString("modo").takeIf { it.isNotBlank() }
                if (level in 0..100 || mode != null) {
                    AssistantIntent.Volume(if (level in 0..100) level else null, mode)
                } else {
                    null
                }
            }

            "grabar_audio" -> {
                val seconds = a.optInt("segundos", 15).coerceIn(5, 120)
                AssistantIntent.RecordVoiceNote(
                    seconds,
                    a.optString("titulo").takeIf { it.isNotBlank() } ?: "Nota de voz"
                )
            }

            "guardar_lugar" -> a.optString("nombre").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.SaveCurrentPlace(it) }

            "calcular" -> a.optString("expresion").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.Calculate(it) }

            "crear_temporizador" -> {
                val minutes = a.optInt("minutos", -1)
                if (minutes in 1..24 * 60) AssistantIntent.SetTimer(minutes) else null
            }

            "crear_alarma" -> {
                val hour = a.optInt("hora", -1)
                val minute = a.optInt("minuto", 0)
                if (hour in 0..23 && minute in 0..59) {
                    AssistantIntent.SetAlarm(hour, minute)
                } else {
                    null
                }
            }

            "linterna" -> AssistantIntent.Flashlight(a.optBoolean("encender", true))

            "nivel_bateria" -> AssistantIntent.Battery

            "abrir_escaner_lens" -> AssistantIntent.Lens

            "traducir_texto" -> {
                val text = a.optString("texto").takeIf { it.isNotBlank() } ?: return null
                val lang = a.optString("idioma_destino").takeIf {
                    it.isNotBlank() && it.matches(Regex("^[a-z]{2}$"))
                }
                AssistantIntent.Translate(text, lang)
            }

            else -> null // unknown tool: never guess
        }
    }

    /**
     * Full routing pipeline for a text utterance. Returns the intents to
     * execute, or null when Needle is not usable / produced nothing we trust
     * (the caller then falls back to the deterministic rule parser).
     */
    suspend fun route(utterance: String): List<AssistantIntent>? {
        val raw = NeedleRuntime.completeText(utterance) ?: return null
        val parsed = parseResponse(raw) ?: return null
        if (parsed.calls.isEmpty()) return null
        if (parsed.confidence < MIN_CONFIDENCE) return null
        val intents = parsed.calls.mapNotNull { callToIntent(it) }
        return intents.ifEmpty { null }
    }

    /**
     * Semantic note ranking: given a search turn (query + numbered
     * candidate excerpts) the model must call buscar_en_notas with the ids
     * that truly answer the query, best first. Returns null when the model
     * is unusable or did not cooperate (caller keeps the lexical ranking).
     */
    suspend fun rankNotes(searchTurn: String): List<Long>? {
        val raw = NeedleRuntime.completeText(searchTurn, 220) ?: return null
        val parsed = parseResponse(raw) ?: return null
        val call = parsed.calls.firstOrNull { it.name == "buscar_en_notas" } ?: return null
        if (parsed.confidence < MIN_CONFIDENCE) return null
        val ids = call.args.optJSONArray("ids_relevantes") ?: return null
        val ranked = mutableListOf<Long>()
        for (i in 0 until ids.length()) {
            val id = ids.optLong(i, -1L)
            if (id >= 0 && !ranked.contains(id)) ranked.add(id)
        }
        return ranked
    }

    /**
     * RAG answer over the journal: the app retrieves the most relevant
     * fragments (embedding search done by the caller) and Needle writes the
     * answer through the consultar_diario tool. Returns null when the model
     * is unusable or did not cooperate.
     */
    suspend fun answerFromJournal(query: String, fragments: List<String>): String? {
        if (fragments.isEmpty()) return null
        val turn = buildString {
            append("Fragmentos del diario del usuario:\n")
            fragments.forEachIndexed { i, f ->
                append("#").append(i + 1).append(" ").append(f.replace(Regex("\\s+"), " ").trim()).append('\n')
            }
            append("Pregunta: «").append(query.trim()).append("».\n")
            append("Llama a consultar_diario con texto= la respuesta, usando SOLO esos fragmentos. ")
            append("Si no aparecen en los fragmentos, dilo en la respuesta.")
        }
        val raw = NeedleRuntime.completeText(turn, 260) ?: return null
        val parsed = parseResponse(raw) ?: return null
        val call = parsed.calls.firstOrNull { it.name == "consultar_diario" } ?: return null
        if (parsed.confidence < 0.35f) return null
        return call.args.optString("texto").takeIf { it.isNotBlank() }
    }

    /**
     * Weekly narrative: the app hands over the week's statistics and Needle
     * writes the report through the resumen_semanal tool.
     */
    suspend fun weeklyNarrative(statsTurn: String): String? {
        val raw = NeedleRuntime.completeText(statsTurn, 420) ?: return null
        val parsed = parseResponse(raw) ?: return null
        val call = parsed.calls.firstOrNull { it.name == "resumen_semanal" } ?: return null
        return call.args.optString("texto").takeIf { it.isNotBlank() }
    }
}

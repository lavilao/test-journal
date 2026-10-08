package com.example.ai.needle

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
 * Bridges the local Cactus Needle 3 model with the app's assistant:
 * declares the tools the assistant can execute (mirroring every
 * [AssistantIntent]), parses the model's function-call JSON back into
 * intents, and applies an honest confidence policy.
 */
object NeedleTools {

    /** Minimum calibrated confidence to act on a Needle routing decision. */
    const val MIN_CONFIDENCE = 0.5f

    // ------------------------------------------------------------------
    // Tool catalogue (what we declare to the engine)
    // ------------------------------------------------------------------

    fun buildToolsJson(): String {
        fun prop(type: String, description: String) =
            JSONObject().put("type", type).put("description", description)

        fun tool(name: String, description: String, props: Map<String, JSONObject>, required: List<String> = props.keys.toList()) =
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

        val tools = JSONArray()
        tools.put(
            tool(
                "abrir_app",
                "Abre una aplicación instalada en el teléfono por su nombre habitual.",
                mapOf("app" to prop("string", "Nombre de la app, p. ej. whatsapp, cámara, calendario, youtube"))
            )
        )
        tools.put(
            tool(
                "llamar_contacto",
                "Inicia una llamada telefónica a un contacto de la agenda.",
                mapOf("nombre" to prop("string", "Nombre de la persona tal como la llama el usuario"))
            )
        )
        tools.put(
            tool(
                "buscar_en_telefono",
                "Busca notas, archivos y contactos guardados en el propio teléfono.",
                mapOf("consulta" to prop("string", "Texto a buscar"))
            )
        )
        tools.put(
            tool(
                "buscar_en_web",
                "Busca en internet y abre el navegador con el resultado.",
                mapOf("consulta" to prop("string", "Consulta para el buscador web"))
            )
        )
        tools.put(
            tool(
                "clima_hoy",
                "Lee el clima actual de la ciudad configurada en la app.",
                emptyMap()
            )
        )
        tools.put(
            tool(
                "crear_nota",
                "Guarda una nota de texto en el diario.",
                mapOf("texto" to prop("string", "Contenido completo de la nota"))
            )
        )
        tools.put(
            tool(
                "calcular",
                "Evalúa una expresión aritmética.",
                mapOf(
                    "expresion" to prop(
                        "string",
                        "Expresión con solo números y los operadores + - * / ( ) . ^ — por ejemplo 12*7+3"
                    )
                )
            )
        )
        tools.put(
            tool(
                "crear_temporizador",
                "Crea un temporizador en la app de reloj.",
                mapOf(
                    "minutos" to prop(
                        "integer",
                        "Duración TOTAL del temporizador en MINUTOS ya convertida: «30 segundos» = 1, «5 minutos» = 5, «2 horas» = 120"
                    )
                )
            )
        )
        tools.put(
            tool(
                "crear_alarma",
                "Crea una alarma para una hora concreta del día.",
                mapOf(
                    "hora" to prop("integer", "Hora del día 0-23"),
                    "minuto" to prop("integer", "Minuto 0-59; si no se dice, 0")
                )
            )
        )
        tools.put(
            tool(
                "linterna",
                "Enciende o apaga la linterna del teléfono.",
                mapOf("encender" to prop("boolean", "true para encender, false para apagar"))
            )
        )
        tools.put(
            tool(
                "nivel_bateria",
                "Lee el porcentaje de batería restante.",
                emptyMap()
            )
        )
        tools.put(
            tool(
                "abrir_escaner_lens",
                "Abre la cámara inteligente (Lens) para escanear texto, traducir con la cámara o leer códigos.",
                emptyMap()
            )
        )
        tools.put(
            tool(
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
        )
        tools.put(
            tool(
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
        )
        return tools.toString()
    }

    /** Session facts given to the engine alongside the tools. */
    fun buildSystemPrompt(): String {
        val now = java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es"))
            .format(java.util.Date())
        return "Eres el enrutador de comandos del asistente local de Mnemosyne en un teléfono " +
            "Android. Hoy es $now. El usuario habla en español. Escucha lo que pide y elige la " +
            "herramienta correcta rellenando todos los argumentos. Si nada encaja con lo pedido, " +
            "devuelve una lista de llamadas vacía en lugar de adivinar. Para buscar en el diario " +
            "del usuario usa SIEMPRE buscar_en_notas (entendiendo sinónimos e intención, no solo " +
            "palabras exactas); para buscar en archivos/contactos del teléfono usa " +
            "buscar_en_telefono."
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

            "llamar_contacto" -> a.optString("nombre").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.CallContact(it) }

            "buscar_en_telefono" -> a.optString("consulta").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.SearchDevice(it) }

            "buscar_en_web" -> a.optString("consulta").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.SearchWeb(it) }

            "clima_hoy" -> AssistantIntent.Weather

            "crear_nota" -> a.optString("texto").takeIf { it.isNotBlank() }
                ?.let { AssistantIntent.CreateNote(it) }

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
}

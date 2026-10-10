package com.example.ai.needle

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.CustomTool
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory mirror of the user's custom tools (Room `custom_tools`).
 *
 * NeedleTools.buildToolsJson() is a SYNCHRONOUS function called inside the
 * engine's single-thread dispatcher, so it cannot query Room directly — it
 * reads this registry instead. Refresh points: app start, after every
 * create/edit/delete/toggle, and before applyToolGating.
 */
object CustomToolRegistry {

    data class Param(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = true,
        val default: String = ""
    )

    @Volatile
    private var tools: List<CustomTool> = emptyList()

    @Volatile
    var lastRefreshAt: Long = 0
        private set

    /** Enabled custom tools. */
    fun snapshot(): List<CustomTool> = tools.filter { it.enabled }

    /** All custom tools (for the manager UI). */
    fun all(): List<CustomTool> = tools

    suspend fun refresh(context: Context) {
        try {
            tools = AppDatabase.getInstance(context).customToolDao().getAllOnce()
            lastRefreshAt = System.currentTimeMillis()
        } catch (_: Exception) {
            // DB not ready yet — keeps the last snapshot.
        }
    }

    /** Finds a custom tool by the name the model called. */
    fun findByName(name: String): CustomTool? =
        tools.firstOrNull { it.enabled && it.name == name }

    /**
     * Builds the JSON-schema declaration for ONE custom tool, following the
     * Cactus "designing tools for Needle" rules: the description states the
     * ACTIONS covered, params say WHERE in the sentence the value comes
     * from, and optional params are omitted from `required`.
     */
    fun buildToolJson(tool: CustomTool): JSONObject {
        val params = parseParams(tool.paramsJson)
        val props = JSONObject()
        val required = JSONArray()
        params.forEach { p ->
            val prop = JSONObject()
                .put("type", p.type)
                .put("description", p.description)
            if (p.type == "boolean") prop.put("enum", JSONArray(listOf(true, false)))
            if (p.default.isNotBlank()) prop.put("default", p.default)
            props.put(p.name, prop)
            if (p.required) required.put(p.name)
        }
        return JSONObject()
            .put("name", tool.name)
            .put("description", tool.description)
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", props)
                    .put("required", required)
            )
    }

    /** Renders a {{param}} template with the arguments the model filled. */
    fun renderTemplate(template: String, args: Map<String, String>): String {
        var out = template
        args.forEach { (k, v) ->
            out = out.replace("{{$k}}", v)
            out = out.replace("{{ $k }}", v)
        }
        // Unfilled placeholders become honest empties, never leaked braces.
        out = out.replace(Regex("\\{\\{\\s*[a-zA-Z0-9_ñÑ]+\\s*\\}\\}"), "")
        return out.trim()
    }

    fun parseParams(paramsJson: String): List<Param> {
        val out = mutableListOf<Param>()
        try {
            val arr = JSONArray(paramsJson.ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                out.add(
                    Param(
                        name = name,
                        type = o.optString("type", "string").ifBlank { "string" },
                        description = o.optString("description"),
                        required = o.optBoolean("required", true),
                        default = o.optString("default")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun encodeParams(params: List<Param>): String {
        val arr = JSONArray()
        params.filter { it.name.isNotBlank() }.forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name.trim())
                    .put("type", p.type)
                    .put("description", p.description)
                    .put("required", p.required)
                    .put("default", p.default)
            )
        }
        return arr.toString()
    }

    /** Names the built-in catalogue already owns. */
    private val BUILT_IN_NAMES = setOf(
        "abrir_app", "cerrar_app", "llamar_contacto", "mandar_mensaje",
        "buscar_en_telefono", "buscar_en_web", "buscar_en_notas",
        "consultar_diario", "clima_hoy", "crear_nota", "crear_tarea",
        "crear_evento", "siguiente_evento", "calcular", "crear_temporizador",
        "crear_alarma", "linterna", "nivel_bateria", "pasos_hoy", "volumen",
        "grabar_audio", "abrir_escaner_lens", "traducir_texto", "guardar_lugar",
        "resumen_habitos", "resumen_semanal"
    )

    /** Validates a tool name for the model: snake_case, unique, free. */
    fun normalizeName(raw: String, existing: List<CustomTool>, editingId: Long): String? {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9_ñ]+"), "_")
            .trim('_')
        if (cleaned.isBlank() || cleaned.length > 48) return null
        if (cleaned in BUILT_IN_NAMES) return null
        if (existing.any { it.id != editingId && it.name == cleaned }) return null
        return cleaned
    }
}

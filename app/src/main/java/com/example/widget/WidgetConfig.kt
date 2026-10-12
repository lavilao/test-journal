package com.example.widget

import android.content.Context
import org.json.JSONArray

/**
 * v1.7: the user chooses WHAT the At a Glance widget shows (Samsung Now
 * Brief-style configurable content cards). Order is fixed and sane; each
 * module can be toggled on/off. Stored as a JSON array of module ids —
 * an empty stored list means "all defaults" so the widget never renders
 * with zero pages.
 */
object WidgetConfig {

    data class Module(
        val id: String,
        val label: String,
        val description: String
    )

    /** Display order of the widget pages. */
    val MODULES: List<Module> = listOf(
        Module("event", "Calendario", "Próximo evento con cuenta atrás"),
        Module("steps", "Pasos", "Pasos de hoy y progreso a la meta"),
        Module("task", "Tareas", "Próxima tarea pendiente"),
        Module("system", "Batería y alarma", "Nivel de batería y próxima alarma"),
        Module("news", "Noticias", "Titular más reciente de tus fuentes")
    )

    private const val PREFS = "widget_config_prefs"
    private const val KEY_ENABLED = "enabled_modules"
    private const val KEY_WEATHER_HEADER = "weather_header"

    /** The enabled module ids in display order; never empty. */
    fun enabledModules(context: Context): List<String> {
        val all = MODULES.map { it.id }
        return try {
            val json = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENABLED, null) ?: return all
            val stored = JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }
            val filtered = all.filter { it in stored }
            if (filtered.isEmpty()) all else filtered
        } catch (_: Exception) {
            all
        }
    }

    fun setEnabledModules(context: Context, ids: Collection<String>) {
        // Always keep at least one module so the widget has a page to show.
        val valid = MODULES.map { it.id }.filter { it in ids }
        val toStore = if (valid.isEmpty()) listOf("event") else valid
        try {
            val arr = JSONArray()
            toStore.forEach { arr.put(it) }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENABLED, arr.toString())
                .commit()
        } catch (_: Exception) {
        }
    }

    fun isEnabled(context: Context, moduleId: String): Boolean =
        moduleId in enabledModules(context)

    fun setEnabled(context: Context, moduleId: String, enabled: Boolean) {
        val current = enabledModules(context).toMutableList()
        if (enabled && moduleId !in current) current.add(moduleId)
        else if (!enabled && current.size > 1) current.remove(moduleId)
        setEnabledModules(context, current)
    }

    /** Weather next to the date in the widget header (default on). */
    fun weatherHeaderEnabled(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEATHER_HEADER, true)
    } catch (_: Exception) {
        true
    }

    fun setWeatherHeaderEnabled(context: Context, enabled: Boolean) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WEATHER_HEADER, enabled)
                .commit()
        } catch (_: Exception) {
        }
    }
}

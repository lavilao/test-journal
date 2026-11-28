package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Artefact desk is offered behind a switch in Settings, never as a second
 * home sitting next to the classic one: when it is on it replaces the home
 * interface everywhere, and when it is off the desk is gone.
 *
 * Defaults to on for this build so the desk the user asked for is what opens.
 */
object DeskModePreferences {

    private const val PREFS = "desk_mode_prefs"
    private const val KEY_ARTEFACT_DESK = "artefact_desk_enabled"

    @Volatile
    private var loaded = false

    private val _artefactDesk = MutableStateFlow(true)
    val artefactDesk: StateFlow<Boolean> = _artefactDesk.asStateFlow()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _artefactDesk.value = prefs.getBoolean(KEY_ARTEFACT_DESK, true)
            loaded = true
        }
    }

    fun isEnabled(context: Context): Boolean {
        ensureLoaded(context)
        return _artefactDesk.value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARTEFACT_DESK, enabled)
            .apply()
        _artefactDesk.value = enabled
        loaded = true
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppInterfaceMode {
    GOOGLE,   // Modo Google: Widget estilo Pixel At a Glance transparente y sin bordes, buscador limpio y feed de noticias
    SAMSUNG   // Modo Samsung: NowBrief dinámico con resúmenes adaptativos según la hora del día
}

class AppModePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_interface_mode_prefs", Context.MODE_PRIVATE)

    private val _interfaceMode = MutableStateFlow(loadMode())
    val interfaceMode: StateFlow<AppInterfaceMode> = _interfaceMode.asStateFlow()

    private fun loadMode(): AppInterfaceMode {
        val saved = prefs.getString("interface_mode", AppInterfaceMode.GOOGLE.name)
        return try {
            AppInterfaceMode.valueOf(saved ?: AppInterfaceMode.GOOGLE.name)
        } catch (_: Exception) {
            AppInterfaceMode.GOOGLE
        }
    }

    fun setMode(mode: AppInterfaceMode) {
        prefs.edit().putString("interface_mode", mode.name).apply()
        _interfaceMode.value = mode
    }
}

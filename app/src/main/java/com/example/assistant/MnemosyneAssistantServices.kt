package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.MainActivity

/**
 * Makes the app eligible to be the SYSTEM assistant (the one launched by the
 * home-button long-press / power-swipe / "Asistente" gesture).
 *
 * When the user picks Mnemosyne as their assistant, Android binds
 * [MnemosyneVoiceInteractionService]. Any assistant invocation then creates a
 * [MnemosyneVoiceSession] through [MnemosyneSessionService]; that session
 * immediately hands off to MainActivity routed to the local assistant
 * screen — no Google Assistant, no network, everything stays on-device.
 *
 * ANDROID 11 NOTES (verified against the platform requirements):
 *  - The service must be exported, hold BIND_VOICE_INTERACTION and declare
 *    the VoiceInteractionService intent-filter (all in the manifest).
 *  - The meta-data XML must declare the sessionService AND
 *    supportsAssist="true" — without the latter some ROMs never show the
 *    service inside "App de asistencia".
 *  - The assistant picker lives at Ajustes → Apps y notificaciones →
 *    Avanzado → Apps predeterminadas → App de asistencia (deep-linked from
 *    the app via ACTION_VOICE_INPUT_SETTINGS).
 *
 * NOTE ON HOTWORD: becoming the assistant does NOT grant the privileged
 * always-on hotword APIs (those were removed from the public SDK); the
 * in-app hotword is the software listener in HotwordService.
 */
class MnemosyneVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        // Nothing to pre-bind: sessions are created on demand.
    }
}

/** Factory for assistant sessions (declared in the service metadata XML). */
class MnemosyneSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession =
        MnemosyneVoiceSession(this)
}

/**
 * Invisible session that instantly routes the assistant gesture into the
 * app's own assistant screen (instead of a blank system overlay).
 */
class MnemosyneVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        try {
            hide()
        } catch (_: Exception) {}

        // VoiceInteractionSession exposes getContext(); the constructor
        // parameter is intentionally not stored to avoid shadowing it.
        val intent = Intent(getContext(), MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_ASSISTANT, true)
        try {
            getContext().startActivity(intent)
        } catch (_: Exception) {}
    }
}

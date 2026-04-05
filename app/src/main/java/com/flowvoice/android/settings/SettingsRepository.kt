package com.flowvoice.android.settings

import android.content.Context

data class AppSettings(
    val host: String,
    val port: Int,
    val language: String,
    val preprocess: Boolean
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() = AppSettings(
        host = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST,
        port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
        language = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE,
        preprocess = prefs.getBoolean(KEY_PREPROCESS, DEFAULT_PREPROCESS)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_HOST, settings.host)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_LANGUAGE, settings.language)
            .putBoolean(KEY_PREPROCESS, settings.preprocess)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "flowvoice_settings"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_PREPROCESS = "preprocess"

        const val DEFAULT_HOST = "192.168.1.1"
        const val DEFAULT_PORT = 5000
        const val DEFAULT_LANGUAGE = "auto"
        const val DEFAULT_PREPROCESS = false

        // Ordered list of (code, display name) pairs used as local fallback
        // when the server is not reachable.
        val FALLBACK_LANGUAGES: List<Pair<String, String>> = listOf(
            "auto" to "Auto-detect",
            "fr" to "French",
            "en" to "English",
            "de" to "German",
            "es" to "Spanish",
            "it" to "Italian",
            "pt" to "Portuguese",
            "nl" to "Dutch",
            "pl" to "Polish",
            "ru" to "Russian",
            "zh" to "Chinese",
            "ja" to "Japanese",
            "ko" to "Korean",
            "ar" to "Arabic"
        )
    }
}

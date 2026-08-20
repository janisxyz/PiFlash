package ch.leftclick.piflash.domain.prefs

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { DYNAMIC, RASPBERRY, TEAL, INDIGO, AMBER, FOREST }

data class AppSettings(
    val languageTag: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.RASPBERRY
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("piflash_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        languageTag = prefs.getString(KEY_LANGUAGE, "") ?: "",
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        accent = runCatching {
            Accent.valueOf(prefs.getString(KEY_ACCENT, Accent.RASPBERRY.name) ?: Accent.RASPBERRY.name)
        }.getOrDefault(Accent.RASPBERRY)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_LANGUAGE, settings.languageTag)
            .putString(KEY_THEME, settings.themeMode.name)
            .putString(KEY_ACCENT, settings.accent.name)
            .apply()
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
    }
}

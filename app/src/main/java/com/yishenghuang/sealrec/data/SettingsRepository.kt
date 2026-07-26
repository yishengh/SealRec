package com.yishenghuang.sealrec.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yishenghuang.sealrec.core.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore("sealrec_settings")

enum class AppLanguage(val tag: String, val nativeLabel: String) {
    System(tag = "", nativeLabel = ""),
    English(tag = "en", nativeLabel = "English"),
    ChineseSimplified(tag = "zh-CN", nativeLabel = "简体中文"),
    ChineseTraditional(tag = "zh-TW", nativeLabel = "繁體中文"),
    Japanese(tag = "ja", nativeLabel = "日本語"),
    Korean(tag = "ko", nativeLabel = "한국어"),
    Spanish(tag = "es", nativeLabel = "Español"),
    French(tag = "fr", nativeLabel = "Français"),
    German(tag = "de", nativeLabel = "Deutsch"),
    PortugueseBrazil(tag = "pt-BR", nativeLabel = "Português"),
}

enum class NightModeOption(val mode: Int) {
    FollowSystem(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light(AppCompatDelegate.MODE_NIGHT_NO),
    Dark(AppCompatDelegate.MODE_NIGHT_YES),
}

enum class AudioQuality(val sampleRate: Int) {
    Voice16k(16_000),
    Cd44k(44_100),
    Studio48k(48_000),
}

data class UserSettings(
    val language: AppLanguage = AppLanguage.System,
    val nightMode: NightModeOption = NightModeOption.FollowSystem,
    val quality: AudioQuality = AudioQuality.Cd44k,
    val allowNotificationSoundsWhileRecording: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private val keyLang = stringPreferencesKey("language")
    private val keyNight = stringPreferencesKey("night_mode")
    private val keyQuality = stringPreferencesKey("quality")
    private val keyNotif = booleanPreferencesKey("allow_notif_sounds")

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            language = AppLanguage.entries.find { it.name == prefs[keyLang] } ?: AppLanguage.System,
            nightMode = NightModeOption.entries.find { it.name == prefs[keyNight] }
                ?: NightModeOption.FollowSystem,
            quality = AudioQuality.entries.find { it.name == prefs[keyQuality] } ?: AudioQuality.Cd44k,
            allowNotificationSoundsWhileRecording = prefs[keyNotif] ?: true,
        )
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.dataStore.edit { it[keyLang] = value.name }
        withContext(Dispatchers.Main.immediate) {
            applyLanguage(value)
        }
    }

    suspend fun setNightMode(value: NightModeOption) {
        context.dataStore.edit { it[keyNight] = value.name }
        withContext(Dispatchers.Main.immediate) {
            AppCompatDelegate.setDefaultNightMode(value.mode)
        }
    }

    suspend fun setQuality(value: AudioQuality) {
        context.dataStore.edit { it[keyQuality] = value.name }
    }

    suspend fun setAllowNotificationSounds(value: Boolean) {
        context.dataStore.edit { it[keyNotif] = value }
    }

    fun audioConfig(quality: AudioQuality): AudioConfig =
        AudioConfig(sampleRate = quality.sampleRate)

    companion object {
        fun applyLanguage(language: AppLanguage) {
            val locales = if (language.tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }

        fun applyNightMode(option: NightModeOption) {
            AppCompatDelegate.setDefaultNightMode(option.mode)
        }
    }
}

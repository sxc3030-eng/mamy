package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Stores non-sensitive user preferences in DataStore. Sensitive values (BYOK API keys,
 * OAuth tokens) live in [com.mamy.android.data.secrets.SecretsVault] under keystore-wrapped AES.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    enum class Language { SYSTEM, EN, FR }
    enum class LlmProvider { CLAUDE, OPENAI, GEMINI }
    enum class PrivacyMode { STANDARD, STRICT, HYBRID_REDACTION }

    val languageFlow: Flow<Language> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE]?.let(::safeLanguage) ?: Language.SYSTEM
    }

    val dailyBriefingHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_BRIEFING_HOUR] ?: DEFAULT_BRIEFING_HOUR
    }

    val selectedLlmProviderFlow: Flow<LlmProvider> = dataStore.data.map { prefs ->
        prefs[KEY_LLM_PROVIDER]?.let(::safeProvider) ?: LlmProvider.CLAUDE
    }

    val privacyModeFlow: Flow<PrivacyMode> = dataStore.data.map { prefs ->
        prefs[KEY_PRIVACY_MODE]?.let(::safePrivacy) ?: PrivacyMode.STANDARD
    }

    val wakeWordSensitivityFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_WAKEWORD_SENSITIVITY] ?: DEFAULT_WAKEWORD_SENSITIVITY
    }

    /**
     * Combined snapshot used by the LLM/structurer layer (P3+). Returns lowercase
     * strings rather than typed enums so call-sites that compare against
     * [com.mamy.android.data.llm.LlmProviderId] constants stay simple.
     */
    fun stream(): Flow<Settings> = combine(
        selectedLlmProviderFlow,
        languageFlow,
    ) { provider, lang ->
        Settings(
            llmProvider = provider.name.lowercase(),
            uiLanguage = when (lang) {
                Language.FR -> "fr"
                Language.EN -> "en"
                Language.SYSTEM -> "en"
            },
        )
    }

    suspend fun setLanguage(value: Language) {
        dataStore.edit { it[KEY_LANGUAGE] = value.name }
    }

    suspend fun setDailyBriefingHour(hour: Int) {
        require(hour in 0..23) { "hour must be 0..23" }
        dataStore.edit { it[KEY_DAILY_BRIEFING_HOUR] = hour }
    }

    suspend fun setSelectedLlmProvider(value: LlmProvider) {
        dataStore.edit { it[KEY_LLM_PROVIDER] = value.name }
    }

    suspend fun setPrivacyMode(value: PrivacyMode) {
        dataStore.edit { it[KEY_PRIVACY_MODE] = value.name }
    }

    suspend fun setWakeWordSensitivity(level: Int) {
        require(level in 0..2) { "level must be 0..2 (low|medium|high)" }
        dataStore.edit { it[KEY_WAKEWORD_SENSITIVITY] = level }
    }

    private fun safeLanguage(raw: String): Language =
        runCatching { Language.valueOf(raw) }.getOrDefault(Language.SYSTEM)

    private fun safeProvider(raw: String): LlmProvider =
        runCatching { LlmProvider.valueOf(raw) }.getOrDefault(LlmProvider.CLAUDE)

    private fun safePrivacy(raw: String): PrivacyMode =
        runCatching { PrivacyMode.valueOf(raw) }.getOrDefault(PrivacyMode.STANDARD)

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_DAILY_BRIEFING_HOUR = intPreferencesKey("daily_briefing_hour")
        private val KEY_LLM_PROVIDER = stringPreferencesKey("llm_provider")
        private val KEY_PRIVACY_MODE = stringPreferencesKey("privacy_mode")
        private val KEY_WAKEWORD_SENSITIVITY = intPreferencesKey("wakeword_sensitivity")

        const val DEFAULT_BRIEFING_HOUR = 8
        const val DEFAULT_WAKEWORD_SENSITIVITY = 1
    }
}

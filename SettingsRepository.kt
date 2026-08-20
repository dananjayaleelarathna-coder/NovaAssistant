package com.nova.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nova_settings")

data class NovaSettings(
    val assistantName: String = "Nova",
    val wakeWordEnabled: Boolean = true,
    val wakeSensitivity: Float = 0.5f,
    val backgroundListening: Boolean = true,
    val batterySavingMode: Boolean = false,
    val language: String = "en-US", // "en-US", "si-LK", or "mixed"
    val personality: String = "Friendly",
    val aiProviderId: String = "anthropic",
    val offlineModeOnly: Boolean = false,
    val theme: String = "System", // "Light", "Dark", "AMOLED", "System"
    val accentColorHex: String = "#7C4DFF",
    val memoryEnabled: Boolean = true,
    val cloudAiEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val BACKGROUND_LISTENING = booleanPreferencesKey("background_listening")
        val BATTERY_SAVING = booleanPreferencesKey("battery_saving")
        val LANGUAGE = stringPreferencesKey("language")
        val PERSONALITY = stringPreferencesKey("personality")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val CLOUD_AI_ENABLED = booleanPreferencesKey("cloud_ai_enabled")
    }

    val settingsFlow: Flow<NovaSettings> = context.dataStore.data.map { prefs ->
        NovaSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME] ?: "Nova",
            wakeWordEnabled = prefs[Keys.WAKE_ENABLED] ?: true,
            wakeSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.5f,
            backgroundListening = prefs[Keys.BACKGROUND_LISTENING] ?: true,
            batterySavingMode = prefs[Keys.BATTERY_SAVING] ?: false,
            language = prefs[Keys.LANGUAGE] ?: "en-US",
            personality = prefs[Keys.PERSONALITY] ?: "Friendly",
            aiProviderId = prefs[Keys.AI_PROVIDER] ?: "anthropic",
            offlineModeOnly = prefs[Keys.OFFLINE_ONLY] ?: false,
            theme = prefs[Keys.THEME] ?: "System",
            accentColorHex = prefs[Keys.ACCENT] ?: "#7C4DFF",
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            cloudAiEnabled = prefs[Keys.CLOUD_AI_ENABLED] ?: true
        )
    }

    suspend fun setAssistantName(name: String) = context.dataStore.edit { it[Keys.ASSISTANT_NAME] = name }
    suspend fun setWakeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.WAKE_ENABLED] = enabled }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    suspend fun setPersonality(p: String) = context.dataStore.edit { it[Keys.PERSONALITY] = p }
    suspend fun setOfflineOnly(v: Boolean) = context.dataStore.edit { it[Keys.OFFLINE_ONLY] = v }
    suspend fun setMemoryEnabled(v: Boolean) = context.dataStore.edit { it[Keys.MEMORY_ENABLED] = v }
    suspend fun setCloudAiEnabled(v: Boolean) = context.dataStore.edit { it[Keys.CLOUD_AI_ENABLED] = v }
    suspend fun setWakeSensitivity(v: Float) = context.dataStore.edit { it[Keys.WAKE_SENSITIVITY] = v }
    suspend fun setBackgroundListening(v: Boolean) = context.dataStore.edit { it[Keys.BACKGROUND_LISTENING] = v }
    suspend fun setBatterySavingMode(v: Boolean) = context.dataStore.edit { it[Keys.BATTERY_SAVING] = v }
    suspend fun setAiProvider(id: String) = context.dataStore.edit { it[Keys.AI_PROVIDER] = id }
    suspend fun setAccentColor(hex: String) = context.dataStore.edit { it[Keys.ACCENT] = hex }
}

/**
 * API key storage, kept fully separate from DataStore/plain prefs and encrypted at rest.
 * Requires the `androidx.security:security-crypto` dependency in app/build.gradle.kts.
 */
class SecureKeyStore(private val context: Context) {

    private val masterKey by lazy {
        androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "nova_secure_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setApiKey(providerId: String, key: String) = prefs.edit().putString("key_$providerId", key).apply()
    fun getApiKey(providerId: String): String? = prefs.getString("key_$providerId", null)
    fun clearApiKey(providerId: String) = prefs.edit().remove("key_$providerId").apply()
    fun clearAllKeys() = prefs.edit().clear().apply()
}

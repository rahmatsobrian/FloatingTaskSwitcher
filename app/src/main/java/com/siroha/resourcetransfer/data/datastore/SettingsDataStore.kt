package com.siroha.resourcetransfer.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val language: String = "id",
    val autoRetryEnabled: Boolean = true,
    val chunkSizeKb: Int = 512,
    val maxTransferSpeedKbps: Int = 0, // 0 = unlimited
    val encryptionEnabled: Boolean = true,
    val compressionEnabled: Boolean = false,
    val developerModeEnabled: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val CHUNK_SIZE_KB = intPreferencesKey("chunk_size_kb")
        val MAX_SPEED_KBPS = intPreferencesKey("max_speed_kbps")
        val ENCRYPTION = booleanPreferencesKey("encryption")
        val COMPRESSION = booleanPreferencesKey("compression")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            language = prefs[Keys.LANGUAGE] ?: "id",
            autoRetryEnabled = prefs[Keys.AUTO_RETRY] ?: true,
            chunkSizeKb = prefs[Keys.CHUNK_SIZE_KB] ?: 512,
            maxTransferSpeedKbps = prefs[Keys.MAX_SPEED_KBPS] ?: 0,
            encryptionEnabled = prefs[Keys.ENCRYPTION] ?: true,
            compressionEnabled = prefs[Keys.COMPRESSION] ?: false,
            developerModeEnabled = prefs[Keys.DEVELOPER_MODE] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    suspend fun setAutoRetry(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_RETRY] = enabled }
    suspend fun setChunkSizeKb(kb: Int) = context.dataStore.edit { it[Keys.CHUNK_SIZE_KB] = kb }
    suspend fun setMaxSpeedKbps(kbps: Int) = context.dataStore.edit { it[Keys.MAX_SPEED_KBPS] = kbps }
    suspend fun setEncryption(enabled: Boolean) = context.dataStore.edit { it[Keys.ENCRYPTION] = enabled }
    suspend fun setCompression(enabled: Boolean) = context.dataStore.edit { it[Keys.COMPRESSION] = enabled }
    suspend fun setDeveloperMode(enabled: Boolean) = context.dataStore.edit { it[Keys.DEVELOPER_MODE] = enabled }
}

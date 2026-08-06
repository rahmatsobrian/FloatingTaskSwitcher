package com.rahmatsobrian.floatingtaskswitcher.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "floating_task_switcher_settings")

data class FloatingSettings(
    val panelStyle: PanelStyle = PanelStyle.HORIZONTAL_DOCK,
    val opacity: Float = 1f,
    val cornerRadiusDp: Int = 24,
    val autoHideEnabled: Boolean = false,
    val positionLocked: Boolean = false,
    val sortMode: SortMode = SortMode.RECENTLY_USED,
    val darkModeOption: DarkModeOption = DarkModeOption.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val rootPreviouslyGranted: Boolean = false,
    val gamingModeEnabled: Boolean = true,
    val floatingServiceEnabled: Boolean = false,
)

enum class PanelStyle { HORIZONTAL_DOCK, VERTICAL_DOCK, GRID, COMPACT, MINI_BUBBLE, EXPAND_PANEL }
enum class DarkModeOption { LIGHT, DARK, AMOLED, SYSTEM }

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val PANEL_STYLE = stringPreferencesKey("panel_style")
        val OPACITY = floatPreferencesKey("opacity")
        val CORNER_RADIUS = intPreferencesKey("corner_radius_dp")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide_enabled")
        val POSITION_LOCKED = booleanPreferencesKey("position_locked")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val DARK_MODE = stringPreferencesKey("dark_mode_option")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val ROOT_GRANTED = booleanPreferencesKey("root_previously_granted")
        val GAMING_MODE = booleanPreferencesKey("gaming_mode_enabled")
        val FLOATING_ENABLED = booleanPreferencesKey("floating_service_enabled")
    }

    val settings: Flow<FloatingSettings> = context.dataStore.data.map { prefs ->
        FloatingSettings(
            panelStyle = prefs[Keys.PANEL_STYLE]?.let { runCatching { PanelStyle.valueOf(it) }.getOrNull() }
                ?: PanelStyle.HORIZONTAL_DOCK,
            opacity = prefs[Keys.OPACITY] ?: 1f,
            cornerRadiusDp = prefs[Keys.CORNER_RADIUS] ?: 24,
            autoHideEnabled = prefs[Keys.AUTO_HIDE] ?: false,
            positionLocked = prefs[Keys.POSITION_LOCKED] ?: false,
            sortMode = prefs[Keys.SORT_MODE]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
                ?: SortMode.RECENTLY_USED,
            darkModeOption = prefs[Keys.DARK_MODE]?.let { runCatching { DarkModeOption.valueOf(it) }.getOrNull() }
                ?: DarkModeOption.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            rootPreviouslyGranted = prefs[Keys.ROOT_GRANTED] ?: false,
            gamingModeEnabled = prefs[Keys.GAMING_MODE] ?: true,
            floatingServiceEnabled = prefs[Keys.FLOATING_ENABLED] ?: false,
        )
    }

    suspend fun updatePanelStyle(style: PanelStyle) = context.dataStore.edit { it[Keys.PANEL_STYLE] = style.name }
    suspend fun updateOpacity(value: Float) = context.dataStore.edit { it[Keys.OPACITY] = value.coerceIn(0.2f, 1f) }
    suspend fun updateCornerRadius(dp: Int) = context.dataStore.edit { it[Keys.CORNER_RADIUS] = dp.coerceIn(0, 48) }
    suspend fun updateAutoHide(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_HIDE] = enabled }
    suspend fun updatePositionLocked(locked: Boolean) = context.dataStore.edit { it[Keys.POSITION_LOCKED] = locked }
    suspend fun updateSortMode(mode: SortMode) = context.dataStore.edit { it[Keys.SORT_MODE] = mode.name }
    suspend fun updateDarkMode(option: DarkModeOption) = context.dataStore.edit { it[Keys.DARK_MODE] = option.name }
    suspend fun updateDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun updateRootPreviouslyGranted(granted: Boolean) =
        context.dataStore.edit { it[Keys.ROOT_GRANTED] = granted }
    suspend fun updateGamingMode(enabled: Boolean) = context.dataStore.edit { it[Keys.GAMING_MODE] = enabled }
    suspend fun updateFloatingServiceEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FLOATING_ENABLED] = enabled }
}

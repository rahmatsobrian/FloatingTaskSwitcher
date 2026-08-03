package com.rahmatsobrian.floatingtaskswitcher.core.permission

import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import com.rahmatsobrian.floatingtaskswitcher.domain.model.OperatingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for which [OperatingMode] the app should currently
 * use. Priority is Root > Shizuku > Standard, matching the product spec.
 *
 * Root is never auto-requested - the su grant prompt only appears after an
 * explicit tap in the Permission Manager screen. On subsequent launches we
 * only re-check silently (via RootController.isRootAvailable, which does
 * not prompt) and restore the previously-granted flag from DataStore so the
 * user isn't asked again every time the process restarts.
 */
@Singleton
class OperatingModeManager @Inject constructor(
    private val rootController: RootController,
    private val shizukuController: ShizukuController,
    private val settingsDataStore: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val rootGranted = MutableStateFlow(false)

    val currentMode: StateFlow<OperatingMode> = combine(
        rootGranted,
        shizukuController.state,
    ) { hasRoot, shizukuState ->
        when {
            hasRoot -> OperatingMode.ROOT
            shizukuState == ShizukuState.RUNNING_GRANTED -> OperatingMode.SHIZUKU
            else -> OperatingMode.STANDARD
        }
    }.stateIn(scope, SharingStarted.Eagerly, OperatingMode.STANDARD)

    fun initialize() {
        shizukuController.start()
        scope.launch {
            val previouslyGranted = settingsDataStore.settings.first().rootPreviouslyGranted
            rootGranted.value = previouslyGranted && rootController.isRootAvailable()
        }
    }

    /** Called only from the Permission Manager screen after an explicit user tap. */
    suspend fun userRequestsRoot(): Boolean {
        val result = rootController.requestRoot()
        rootGranted.value = result.isRootGranted
        settingsDataStore.updateRootPreviouslyGranted(result.isRootGranted)
        return result.isRootGranted
    }

    fun userRequestsShizukuPermission() {
        shizukuController.requestPermission()
    }

    fun dispose() {
        shizukuController.stop()
    }
}

package com.rahmatsobrian.floatingtaskswitcher.domain.repository

import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionSnapshot
import kotlinx.coroutines.flow.Flow

interface PermissionRepository {
    /** Emits a fresh snapshot whenever the app resumes or a relevant permission changes. */
    fun observeSnapshot(): Flow<PermissionSnapshot>

    suspend fun refreshSnapshot(): PermissionSnapshot

    fun isOverlayGranted(): Boolean
    fun isAccessibilityServiceEnabled(): Boolean
    fun isUsageAccessGranted(): Boolean
    fun isNotificationGranted(): Boolean
    fun isBatteryOptimizationExempt(): Boolean
}

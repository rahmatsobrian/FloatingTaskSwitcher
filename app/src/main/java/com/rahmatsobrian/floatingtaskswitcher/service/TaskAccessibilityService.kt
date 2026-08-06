package com.rahmatsobrian.floatingtaskswitcher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports the current foreground package so the floating panel can highlight
 * which app is active. This complements (not replaces) UsageStatsManager,
 * which has a reporting delay of several seconds and is unsuitable for
 * "which app is active right now" state.
 */
class TaskAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            _foregroundPackage.value = packageName
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }

    companion object {
        private val _foregroundPackage = MutableStateFlow<String?>(null)
        val foregroundPackage = _foregroundPackage.asStateFlow()

        var isConnected: Boolean = false
            private set
    }
}

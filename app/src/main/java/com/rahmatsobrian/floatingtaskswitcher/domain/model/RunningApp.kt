package com.rahmatsobrian.floatingtaskswitcher.domain.model

import android.graphics.drawable.Drawable

/**
 * Represents a single app entry shown in the floating panel.
 *
 * [lastUsedAtMillis] drives the "most recently used first" ordering.
 * [icon] is resolved lazily by the repository from [PackageManager] and is
 * safe to be null while it is still loading.
 */
data class RunningApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val lastUsedAtMillis: Long,
    val totalTimeInForegroundMillis: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isCurrentForeground: Boolean = false,
)

enum class SortMode {
    RECENTLY_USED,
    MOST_USED,
    ALPHABETICAL,
    MANUAL,
    PINNED_FIRST,
}

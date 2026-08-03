package com.rahmatsobrian.floatingtaskswitcher.domain.repository

import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode

interface RecentAppsRepository {
    /**
     * Returns recently-used, launchable apps ordered per [sortMode]. Uses
     * UsageStatsManager under Standard Mode; when Root/Shizuku mode is
     * active the implementation additionally queries `dumpsys activity
     * recents` for a more complete and instantaneous task list.
     */
    suspend fun getRecentApps(sortMode: SortMode, limit: Int = 30): List<RunningApp>

    suspend fun switchToApp(packageName: String): Result<Unit>

    suspend fun forceStop(packageName: String): Result<Unit>
}

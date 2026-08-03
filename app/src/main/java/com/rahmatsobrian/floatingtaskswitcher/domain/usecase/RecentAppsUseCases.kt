package com.rahmatsobrian.floatingtaskswitcher.domain.usecase

import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.RecentAppsRepository
import javax.inject.Inject

class GetRecentAppsUseCase @Inject constructor(
    private val repository: RecentAppsRepository,
) {
    suspend operator fun invoke(sortMode: SortMode, limit: Int = 30): List<RunningApp> =
        repository.getRecentApps(sortMode, limit)
}

class SwitchToAppUseCase @Inject constructor(
    private val repository: RecentAppsRepository,
) {
    suspend operator fun invoke(packageName: String): Result<Unit> = repository.switchToApp(packageName)
}

class ForceStopAppUseCase @Inject constructor(
    private val repository: RecentAppsRepository,
) {
    suspend operator fun invoke(packageName: String): Result<Unit> = repository.forceStop(packageName)
}

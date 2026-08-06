package com.rahmatsobrian.floatingtaskswitcher.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import com.rahmatsobrian.floatingtaskswitcher.core.permission.RootController
import com.rahmatsobrian.floatingtaskswitcher.domain.model.OperatingMode
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.RecentAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootController: RootController,
    private val operatingModeManager: OperatingModeManager,
) : RecentAppsRepository {

    private val packageManager: PackageManager get() = context.packageManager
    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override suspend fun getRecentApps(sortMode: SortMode, limit: Int): List<RunningApp> =
        withContext(Dispatchers.Default) {
            val fromUsageStats = queryFromUsageStats()
            val enriched = when (operatingModeManager.currentMode.value) {
                OperatingMode.ROOT -> mergeWithDumpsysRecents(fromUsageStats)
                else -> fromUsageStats
            }
            sort(enriched, sortMode).take(limit)
        }

    override suspend fun switchToApp(packageName: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            when (operatingModeManager.currentMode.value) {
                OperatingMode.ROOT -> switchViaRoot(packageName)
                // Shizuku-native binder-level task switching (ActivityTaskManager AIDL via
                // Shizuku's UserService) is planned for a follow-up iteration; until then
                // Shizuku Mode uses the same reliable standard-launch path as Standard Mode.
                OperatingMode.SHIZUKU, OperatingMode.STANDARD -> switchViaLaunchIntent(packageName)
            }
        }
    }

    override suspend fun forceStop(packageName: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            when (operatingModeManager.currentMode.value) {
                OperatingMode.ROOT -> {
                    rootController.runCommand("am force-stop $packageName").getOrThrow()
                    Unit
                }
                else -> error("Force stop requires Root Mode; not available in this mode.")
            }
        }
    }

    private fun switchViaLaunchIntent(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launchable activity found for $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(launchIntent)
    }

    private suspend fun switchViaRoot(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = launchIntent?.component
        if (componentName == null) {
            switchViaLaunchIntent(packageName)
            return
        }
        val command = "am start -n ${componentName.flattenToShortString()}"
        val result = rootController.runCommand(command)
        if (result.isFailure) {
            // Root command failed (e.g. denied mid-session) - fall back to the standard path
            // rather than silently doing nothing.
            switchViaLaunchIntent(packageName)
        }
    }

    private fun queryFromUsageStats(): List<RunningApp> {
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(3)
        val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            .orEmpty()
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }

        val launchablePackages = getLaunchablePackages()

        return statsList
            .filter { it.packageName in launchablePackages }
            .groupBy { it.packageName }
            .mapNotNull { (packageName, statsForPkg) ->
                val lastUsed = statsForPkg.maxOf { it.lastTimeUsed }
                val totalForeground = statsForPkg.sumOf { it.totalTimeInForeground }
                toRunningApp(packageName, lastUsed, totalForeground)
            }
    }

    /**
     * Best-effort enrichment for Root Mode: `dumpsys activity recents` exposes the live
     * recent-tasks list (including apps opened seconds ago that UsageStats hasn't flushed
     * yet). Output format is not a stable public API, so parsing is defensive and any
     * failure silently falls back to the UsageStats-only list rather than crashing.
     */
    private suspend fun mergeWithDumpsysRecents(base: List<RunningApp>): List<RunningApp> {
        val output = rootController.runCommand("dumpsys activity recents").getOrNull() ?: return base
        val launchablePackages = getLaunchablePackages()
        val discovered = Regex("""realActivity=([\w.]+)/""")
            .findAll(output.joinToString("\n"))
            .map { it.groupValues[1] }
            .filter { it in launchablePackages && it != context.packageName }
            .distinct()
            .toList()

        val alreadyKnown = base.map { it.packageName }.toSet()
        val now = System.currentTimeMillis()
        val extras = discovered
            .filterNot { it in alreadyKnown }
            .mapNotNull { pkg -> toRunningApp(pkg, lastUsed = now, totalForeground = 0L) }

        return base + extras
    }

    private fun toRunningApp(packageName: String, lastUsed: Long, totalForeground: Long): RunningApp? {
        val appInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName)
        val icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull()
        return RunningApp(
            packageName = packageName,
            label = label,
            icon = icon,
            lastUsedAtMillis = lastUsed,
            totalTimeInForegroundMillis = totalForeground,
        )
    }

    private fun getLaunchablePackages(): Set<String> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(mainIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun sort(apps: List<RunningApp>, sortMode: SortMode): List<RunningApp> = when (sortMode) {
        SortMode.RECENTLY_USED -> apps.sortedByDescending { it.lastUsedAtMillis }
        SortMode.MOST_USED -> apps.sortedByDescending { it.totalTimeInForegroundMillis }
        SortMode.ALPHABETICAL -> apps.sortedBy { it.label.lowercase() }
        SortMode.PINNED_FIRST -> apps.sortedWith(
            compareByDescending<RunningApp> { it.isPinned }.thenByDescending { it.lastUsedAtMillis },
        )
        SortMode.MANUAL -> apps
    }
}

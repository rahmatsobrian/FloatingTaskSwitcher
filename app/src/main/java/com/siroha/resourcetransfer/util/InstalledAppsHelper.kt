package com.siroha.resourcetransfer.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val hasSplits: Boolean
)

/**
 * Backs the "Kirim Aplikasi" send mode. Only enumerates apps with a
 * launcher entry (see the `<queries>` block in AndroidManifest.xml) rather
 * than requesting the broad QUERY_ALL_PACKAGES permission.
 */
object InstalledAppsHelper {

    fun listInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())

        return resolved.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            runCatching {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val packageInfo = pm.getPackageInfo(pkg, 0)
                InstalledAppInfo(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    versionName = packageInfo.versionName,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    hasSplits = !appInfo.splitSourceDirs.isNullOrEmpty()
                )
            }.getOrNull()
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    /**
     * Copies the app's base APK into [destDir]. If it was installed as an
     * App Bundle (has split APKs — common for apps from Play Store), bundles
     * base + every split into a single `<AppName>.apks` zip instead, which
     * is the plain-zip format tools like SAI (Split APKs Installer) expect
     * — the receiver gets one installable file, not a folder of loose parts.
     */
    fun exportApk(context: Context, packageName: String, destDir: File): File? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val baseApk = File(appInfo.sourceDir)
            val splitApks = appInfo.splitSourceDirs?.map { File(it) }.orEmpty()
            val safeLabel = pm.getApplicationLabel(appInfo).toString()
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { packageName }

            if (splitApks.isEmpty()) {
                val target = File(destDir, "$safeLabel.apk")
                baseApk.copyTo(target, overwrite = true)
                target
            } else {
                val target = File(destDir, "$safeLabel.apks")
                ZipOutputStream(target.outputStream()).use { zip ->
                    (listOf(baseApk) + splitApks).forEach { part ->
                        zip.putNextEntry(ZipEntry(part.name))
                        part.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                target
            }
        } catch (e: Exception) {
            null
        }
    }
}

package com.siroha.resourcetransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.siroha.resourcetransfer.data.datastore.SettingsDataStore
import com.siroha.resourcetransfer.data.datastore.ThemeMode
import com.siroha.resourcetransfer.ui.navigation.ResourceTransferNavGraph
import com.siroha.resourcetransfer.ui.theme.ResourceTransferTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.collectAsState
import javax.inject.Inject

/**
 * Single Activity hosting the whole Compose navigation graph. Works across
 * phone, tablet, foldable, and Chromebook window sizes since all layout
 * adaptation happens inside each screen via WindowSizeClass, not here.
 *
 * Shizuku's binder/permission listeners are registered once in
 * ResourceTransferApp.onCreate (via ShizukuHelper.registerListeners()),
 * not here — that keeps ShizukuHelper.state accurate app-wide instead of
 * only while this specific Activity instance is alive, which is what
 * previously made the "Aktifkan Shizuku" button require a screen
 * navigate-away-and-back to refresh.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(initial = null)
            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ResourceTransferTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColorEnabled ?: true,
                amoled = settings?.themeMode == ThemeMode.AMOLED
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    ResourceTransferNavGraph(navController = navController)
                }
            }
        }
    }
}

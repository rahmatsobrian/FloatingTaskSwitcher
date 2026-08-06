package com.rahmatsobrian.floatingtaskswitcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption

private val SeedPrimary = Color(0xFF3E6E4C)

private val LightColors = lightColorScheme(primary = SeedPrimary)
private val DarkColors = darkColorScheme(primary = Color(0xFF9CD6A9))
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
)

@Composable
fun FloatingTaskSwitcherTheme(
    darkModeOption: DarkModeOption = DarkModeOption.SYSTEM,
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkModeOption) {
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK, DarkModeOption.AMOLED -> true
        DarkModeOption.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        darkModeOption == DarkModeOption.AMOLED && dynamicColorEnabled && dynamicSupported ->
            dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)
        darkModeOption == DarkModeOption.AMOLED -> AmoledColors
        dynamicColorEnabled && dynamicSupported && useDark -> dynamicDarkColorScheme(context)
        dynamicColorEnabled && dynamicSupported && !useDark -> dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FloatingTypography,
        content = content,
    )
}

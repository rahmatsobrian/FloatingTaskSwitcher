package com.rahmatsobrian.floatingtaskswitcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption

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
        darkModeOption == DarkModeOption.AMOLED -> AppAmoledColors
        dynamicColorEnabled && dynamicSupported && useDark -> dynamicDarkColorScheme(context)
        dynamicColorEnabled && dynamicSupported && !useDark -> dynamicLightColorScheme(context)
        useDark -> AppDarkColors
        else -> AppLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FloatingTypography,
        content = content,
    )
}

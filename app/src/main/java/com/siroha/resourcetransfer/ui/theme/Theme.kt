package com.siroha.resourcetransfer.ui.theme

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

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    background = SurfaceLight,
    surface = CardLight,
    outline = OutlineLight,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    background = SurfaceDark,
    surface = CardDark,
    outline = OutlineDark,
    error = ErrorRed
)

/** Card corner radius per the KernelSU Clean Light spec used across Siroha's other projects. */
val CardRadiusDp = 8

/**
 * @param amoled When true (only meaningful alongside darkTheme = true), forces every
 * background/surface tone to true black (#000000) for OLED power savings and that
 * "AMOLED theme" look — while keeping whichever accent color scheme (dynamic or
 * static) supplies primary/secondary/etc, so it doesn't fight with Material You.
 */
@Composable
fun ResourceTransferTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    if (amoled && darkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF060606),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF121212),
            surfaceContainerHighest = Color(0xFF1A1A1A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

package com.rahmatsobrian.floatingtaskswitcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Full M3 tonal-palette-style role sets built around the app's green seed color (#3E6E4C,
// matching the launcher icon), instead of only overriding `primary` and letting every other
// role fall back to Compose's default purple baseline.

val AppLightColors = lightColorScheme(
    primary = Color(0xFF3E6E4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFF0C9),
    onPrimaryContainer = Color(0xFF002109),
    secondary = Color(0xFF51634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8CE),
    onSecondaryContainer = Color(0xFF0F1F10),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF001F22),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFDF6),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFDF6),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BC),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF1F1EA),
    inversePrimary = Color(0xFFA3D3AD),
    surfaceTint = Color(0xFF3E6E4C),
)

val AppDarkColors = darkColorScheme(
    primary = Color(0xFFA3D3AD),
    onPrimary = Color(0xFF0A3919),
    primaryContainer = Color(0xFF235128),
    onPrimaryContainer = Color(0xFFBFF0C9),
    secondary = Color(0xFFB8CCB3),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD3E8CE),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF12140F),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BC),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    inverseSurface = Color(0xFFE2E3DD),
    inverseOnSurface = Color(0xFF2F312D),
    inversePrimary = Color(0xFF3E6E4C),
    surfaceTint = Color(0xFFA3D3AD),
)

/** Same role set as [AppDarkColors], but every near-black surface role is pushed to pure black. */
val AppAmoledColors = AppDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF161816),
    inverseOnSurface = Color(0xFF000000),
)

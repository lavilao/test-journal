package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkForestPrimary,
    onPrimary = DarkLeatherBackground,
    primaryContainer = DarkLeatherSurfaceVariant,
    onPrimaryContainer = DarkInkPrimary,
    secondary = DarkTerracottaAccent,
    onSecondary = DarkLeatherBackground,
    tertiary = DarkAmberNode,
    background = DarkLeatherBackground,
    surface = DarkLeatherSurface,
    surfaceVariant = DarkLeatherSurfaceVariant,
    onBackground = DarkInkPrimary,
    onSurface = DarkInkPrimary,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    primaryContainer = WarmPaperSurfaceVariant,
    onPrimaryContainer = InkPrimary,
    secondary = TerracottaAccent,
    onSecondary = ForestOnPrimary,
    tertiary = AmberNode,
    background = WarmPaperBackground,
    surface = WarmPaperSurface,
    surfaceVariant = WarmPaperSurfaceVariant,
    onBackground = InkPrimary,
    onSurface = InkPrimary,
    onSurfaceVariant = InkSecondary,
    outline = WarmBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve our custom warm editorial notebook branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ---------------------------------------------------------------
// NowBrief theme (Samsung mode)
// ---------------------------------------------------------------

private val NowBriefDarkColorScheme = darkColorScheme(
    primary = NowBriefPrimaryDark,
    onPrimary = NowBriefOnPrimaryDark,
    primaryContainer = NowBriefPrimaryContainerDark,
    onPrimaryContainer = NowBriefOnSurfaceDark,
    secondary = NowBriefTertiaryDark,
    onSecondary = NowBriefOnPrimaryDark,
    tertiary = NowBriefTertiaryDark,
    background = NowBriefBackgroundDark,
    onBackground = NowBriefOnSurfaceDark,
    surface = NowBriefSurfaceDark,
    onSurface = NowBriefOnSurfaceDark,
    surfaceVariant = NowBriefSurfaceVariantDark,
    onSurfaceVariant = NowBriefOnSurfaceVariantDark,
    outline = NowBriefOutlineDark
)

private val NowBriefLightColorScheme = lightColorScheme(
    primary = NowBriefPrimaryLight,
    onPrimary = NowBriefOnPrimaryLight,
    primaryContainer = NowBriefPrimaryContainerLight,
    onPrimaryContainer = NowBriefOnSurfaceLight,
    secondary = NowBriefTertiaryLight,
    onSecondary = NowBriefOnPrimaryLight,
    tertiary = NowBriefTertiaryLight,
    background = NowBriefBackgroundLight,
    onBackground = NowBriefOnSurfaceLight,
    surface = NowBriefSurfaceLight,
    onSurface = NowBriefOnSurfaceLight,
    surfaceVariant = NowBriefSurfaceVariantLight,
    onSurfaceVariant = NowBriefOnSurfaceVariantLight,
    outline = NowBriefOutlineLight
)

@Composable
fun NowBriefTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NowBriefDarkColorScheme else NowBriefLightColorScheme,
        typography = Typography,
        content = content
    )
}

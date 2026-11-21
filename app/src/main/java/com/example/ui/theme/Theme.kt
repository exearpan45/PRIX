package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.ThemeMode

@Immutable
data class PrixExtendedColors(
    val success: Color,
    val warning: Color,
    val accentBlue: Color,
    val deepPurple: Color,
    val elevatedSurface: Color,
    val secondaryText: Color,
    val cardBorder: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassHighlightBorder: Color,
    val glassOuterBorder: Color,
    val ambientPurple: Color,
    val ambientBlue: Color,
    val isDark: Boolean
)

val LocalPrixColors = staticCompositionLocalOf {
    PrixExtendedColors(
        success = PrixSuccess,
        warning = PrixWarning,
        accentBlue = PrixAccentBlue,
        deepPurple = PrixPurpleDeep,
        elevatedSurface = PrixSurfaceLight,
        secondaryText = PrixTextSecondaryLight,
        cardBorder = PrixOutlineLight,
        glassSurface = PrixGlassSurfaceLight,
        glassElevated = PrixGlassElevatedLight,
        glassHighlightBorder = PrixGlassBorderLight,
        glassOuterBorder = PrixGlassOuterBorderLight,
        ambientPurple = PrixGlassAmbientTopLight,
        ambientBlue = PrixGlassAmbientBlueLight,
        isDark = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = PrixPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE8FF),
    onPrimaryContainer = PrixPurpleDeep,
    secondary = PrixAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EEFF),
    onSecondaryContainer = Color(0xFF1348A8),
    tertiary = PrixPurpleDeep,
    onTertiary = Color.White,
    background = PrixBackgroundLight,
    onBackground = PrixTextPrimaryLight,
    surface = PrixSurfaceLight,
    onSurface = PrixTextPrimaryLight,
    surfaceVariant = PrixSurfaceVariantLight,
    onSurfaceVariant = PrixTextSecondaryLight,
    surfaceContainerLow = Color(0xFFFDFDFF),
    surfaceContainer = Color(0xFFF3F4FA),
    surfaceContainerHigh = Color(0xFFE9EBF6),
    outline = PrixOutlineLight,
    outlineVariant = Color(0xFFE6E8F3),
    error = PrixError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PrixPurpleDarkPrimary,
    onPrimary = Color(0xFF140A38),
    primaryContainer = Color(0xFF332668),
    onPrimaryContainer = Color(0xFFEBE6FF),
    secondary = PrixAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A335E),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = PrixPurpleDarkDeep,
    onTertiary = Color.White,
    background = PrixBackgroundDark,
    onBackground = PrixTextPrimaryDark,
    surface = PrixSurfaceDark,
    onSurface = PrixTextPrimaryDark,
    surfaceVariant = PrixElevatedSurfaceDark,
    onSurfaceVariant = PrixTextSecondaryDark,
    surfaceContainerLow = Color(0xFF121523),
    surfaceContainer = PrixSurfaceDark,
    surfaceContainerHigh = PrixElevatedSurfaceDark,
    outline = PrixOutlineDark,
    outlineVariant = Color(0xFF252940),
    error = PrixError,
    onError = Color.White
)

val PrixShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PrixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    performanceMode: PerformanceMode = PerformanceMode.AUTO,
    animationsEnabled: Boolean = true,
    reducedMotionOverride: Boolean = false,
    content: @Composable () -> Unit
) {
    val effects = rememberPrixEffectsConfig(
        performanceMode = performanceMode,
        animationsEnabled = animationsEnabled,
        reducedMotionOverride = reducedMotionOverride
    )

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val targetScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val animMs = if (effects.shouldAnimate) effects.animationDurationMs else 0
    val bgColor by animateColorAsState(
        targetValue = targetScheme.background,
        animationSpec = tween(animMs),
        label = "themeBg"
    )
    val surfaceColor by animateColorAsState(
        targetValue = targetScheme.surface,
        animationSpec = tween(animMs),
        label = "themeSurface"
    )

    val colorScheme = targetScheme.copy(
        background = bgColor,
        surface = surfaceColor
    )

    val extended = if (darkTheme) {
        PrixExtendedColors(
            success = PrixSuccess,
            warning = PrixWarning,
            accentBlue = PrixAccentBlue,
            deepPurple = PrixPurpleDarkDeep,
            elevatedSurface = PrixElevatedSurfaceDark,
            secondaryText = PrixTextSecondaryDark,
            cardBorder = PrixGlassOuterBorderDark,
            glassSurface = if (effects.isLowEndDevice) PrixSurfaceDark.copy(alpha = 0.94f) else PrixGlassSurfaceDark,
            glassElevated = if (effects.isLowEndDevice) PrixElevatedSurfaceDark else PrixGlassElevatedDark,
            glassHighlightBorder = PrixGlassBorderDark,
            glassOuterBorder = PrixGlassOuterBorderDark,
            ambientPurple = PrixGlassAmbientTopDark,
            ambientBlue = PrixGlassAmbientBlueDark,
            isDark = true
        )
    } else {
        PrixExtendedColors(
            success = PrixSuccess,
            warning = PrixWarning,
            accentBlue = PrixAccentBlue,
            deepPurple = PrixPurpleDeep,
            elevatedSurface = PrixSurfaceLight,
            secondaryText = PrixTextSecondaryLight,
            cardBorder = PrixGlassOuterBorderLight,
            glassSurface = if (effects.isLowEndDevice) PrixSurfaceLight.copy(alpha = 0.96f) else PrixGlassSurfaceLight,
            glassElevated = if (effects.isLowEndDevice) PrixSurfaceLight else PrixGlassElevatedLight,
            glassHighlightBorder = PrixGlassBorderLight,
            glassOuterBorder = PrixGlassOuterBorderLight,
            ambientPurple = PrixGlassAmbientTopLight,
            ambientBlue = PrixGlassAmbientBlueLight,
            isDark = false
        )
    }

    CompositionLocalProvider(
        LocalPrixColors provides extended,
        LocalPrixEffects provides effects
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PrixTypography,
            shapes = PrixShapes,
            content = content
        )
    }
}

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SbrDarkColorScheme = darkColorScheme(
    primary = SbrOrange,
    onPrimary = Color.White,
    primaryContainer = SbrOrangeDeep.copy(alpha = 0.35f),
    onPrimaryContainer = SbrOrangeSoft,
    secondary = StatusCyan,
    onSecondary = NavyAbyss,
    secondaryContainer = NavyElevated,
    onSecondaryContainer = Color.White,
    tertiary = StatusEmerald,
    onTertiary = NavyAbyss,
    background = NavyAbyss,
    onBackground = Color.White,
    surface = NavySurface,
    onSurface = Color.White,
    surfaceVariant = NavyElevated,
    onSurfaceVariant = NavyMutedText,
    outline = NavyBorder,
    outlineVariant = NavyBorder.copy(alpha = 0.5f),
    error = StatusCrimson,
    onError = Color.White
)

private val SbrLightColorScheme = lightColorScheme(
    primary = SbrOrange,
    onPrimary = Color.White,
    primaryContainer = SbrOrange.copy(alpha = 0.14f),
    onPrimaryContainer = SbrOrangeDeep,
    secondary = NavySurface,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnBackground,
    tertiary = StatusEmerald,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.6f),
    error = StatusCrimson,
    onError = Color.White
)

val SbrShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SbrVpnTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SbrDarkColorScheme else SbrLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SbrTypography,
        shapes = SbrShapes,
        content = content
    )
}

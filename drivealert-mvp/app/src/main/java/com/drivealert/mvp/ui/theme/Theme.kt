package com.drivealert.mvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DriveAlertColors = darkColorScheme(
    primary = DriveRed,
    onPrimary = TextPrimary,
    primaryContainer = DriveRedSoft,
    onPrimaryContainer = TextPrimary,
    secondary = Info,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceStrong,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderSoft,
    error = DriveRed,
    onError = TextPrimary,
)

@Composable
fun DriveAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DriveAlertColors, typography = Typography, content = content)
}

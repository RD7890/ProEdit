package com.rohan.proedit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ProEditColorScheme = darkColorScheme(
    primary           = PhotoshopBlue,
    onPrimary         = Color.White,
    primaryContainer  = NavySelected,
    onPrimaryContainer= AccentBlue,
    secondary         = AccentBlue,
    onSecondary       = NavyBg,
    background        = NavyBg,
    onBackground      = TextPrimary,
    surface           = NavySurface,
    onSurface         = TextPrimary,
    surfaceVariant    = NavyPanel,
    onSurfaceVariant  = TextSecondary,
    outline           = NavyDivider,
    error             = MaskRed,
    onError           = Color.White,
)

@Composable
fun ProEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProEditColorScheme,
        typography  = ProEditTypography,
        content     = content,
    )
}

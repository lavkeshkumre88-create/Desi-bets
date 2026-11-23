package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DesiBeatsDarkColorScheme = darkColorScheme(
    primary = SaffronGold,
    onPrimary = SaffronTextOnPrimary,
    primaryContainer = WarmAmberContainer,
    onPrimaryContainer = SaffronGold,
    secondary = CoralPulse,
    onSecondary = TextPrimary,
    tertiary = EmeraldOffline,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = DeepPlumSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedPlumCard,
    onSurfaceVariant = TextSecondary,
    outline = SubtlePlumBorder,
    error = CoralPulse,
    onError = TextPrimary,
)

@Composable
fun DesiBeatsTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = DesiBeatsDarkColorScheme,
        typography = DesiBeatsTypography,
        content = content,
    )
}

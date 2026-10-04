/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IslandColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF242425), onPrimaryContainer = Color.White,
    secondary = Color(0xFFB8B8BD), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF242425), onSecondaryContainer = Color.White,
    tertiary = Color.White, onTertiary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color(0xFF111111), onSurface = Color.White,
    surfaceTint = Color.White,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF191919),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF343434),
    surfaceVariant = Color(0xFF242425), onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF343434), outlineVariant = Color(0xFF262626),
    error = Color.White, onError = Color.Black
)

@Composable
fun DotIslandTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IslandColors, content = content)
}

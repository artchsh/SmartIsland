/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The physical corner radius of the display, in dp.
 *
 * The HIG states that the Dynamic Island "uses a corner radius of 44 points, and
 * its rounded corner shape matches the TrueDepth camera". The principle is that
 * the island's curvature should be continuous with the screen's own curvature
 * rather than being an arbitrary fixed value — which is why a hardcoded radius
 * looks subtly wrong on a device whose corners are a different shape.
 *
 * `WindowInsets.getRoundedCorner` (API 31+) reports exactly this. Values are in
 * **pixels**, hence the conversion.
 *
 * Returns null when unavailable (API < 31, or an OEM that does not report it), in
 * which case callers fall back to their own default.
 *
 * Note the value is a property of the *display*, so it is the same in portrait
 * and landscape and does not need to be recomputed on rotation.
 */
@Composable
fun rememberDisplayCornerRadius(): Dp? {
    val view = LocalView.current
    return remember(view) {
        readDisplayCornerRadius(view)
    }
}

private fun readDisplayCornerRadius(view: View): Dp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val insets: WindowInsets = view.rootWindowInsets ?: return null

    // Top corners are the relevant ones: the island is docked to the top edge.
    // Prefer the top-left value and fall back to top-right, since a corner can be
    // reported as absent when it is fully rounded off or masked.
    val radiusPx = RoundedCorner.POSITION_TOP_LEFT
        .let { insets.getRoundedCorner(it)?.radius }
        ?: RoundedCorner.POSITION_TOP_RIGHT
            .let { insets.getRoundedCorner(it)?.radius }
        ?: return null

    if (radiusPx <= 0) return null

    val density = view.resources.displayMetrics.density
    if (density <= 0f) return null

    return (radiusPx / density).dp
}

/**
 * Fallback display corner radius for devices that do not report one.
 *
 * Chosen to match the value the HIG quotes for the Dynamic Island (44pt) so that
 * a device which cannot report its radius still gets the documented geometry
 * rather than an arbitrary one.
 */
val FALLBACK_DISPLAY_CORNER_RADIUS: Dp = 44.dp

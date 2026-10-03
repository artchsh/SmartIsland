/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The physical corner radius of the display, in dp.
 *
 * The HIG states that the Dynamic Island "uses a corner radius of 44 points, and
 * its rounded corner shape matches the TrueDepth camera". The principle is that
 * the island's curvature should be continuous with the screen's own rather than
 * being an arbitrary fixed value — which is why a hardcoded radius looks subtly
 * wrong on a device whose corners are a different shape.
 *
 * ## Sources, in order
 *
 * 1. `WindowInsets.getRoundedCorner` on the view. Correct in principle, but on
 *    this app's `TYPE_ACCESSIBILITY_OVERLAY` window it returns **null** — the
 *    overlay window does not receive the rounded-corner insets. Measured on a
 *    Nothing Phone (4a): returns null even though the display reports a radius.
 * 2. `Display.getRoundedCorners()` (API 31+), read through `DisplayManager`.
 *    This is the authoritative source and is what `dumpsys window displays`
 *    prints as `mRoundedCorners`. On the Nothing Phone (4a) it reports
 *    **162 px** on all four corners, i.e. 162 / 2.75 = **58.9 dp** — noticeably
 *    more than the HIG's 44 pt, so a hardcoded 44 dp really does look wrong here.
 * 3. [FALLBACK_DISPLAY_CORNER_RADIUS].
 *
 * Values from `Display` are in **pixels**; the conversion uses the same density
 * Compose lays out with, including any density override.
 */
@Composable
fun rememberDisplayCornerRadius(): Dp? {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        readDisplayCornerRadius(context, view)
    }
}

private fun readDisplayCornerRadius(context: Context, view: View): Dp? {
    val density = view.resources.displayMetrics.density
    if (density <= 0f) return null

    fromWindowInsets(view, density)?.let { return it }
    fromDisplay(context, density)?.let { return it }
    return null
}

/** Primary: the window's own rounded-corner insets. */
private fun fromWindowInsets(view: View, density: Float): Dp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val insets: WindowInsets = view.rootWindowInsets ?: return null

    val radiusPx = RoundedCorner.POSITION_TOP_LEFT
        .let { insets.getRoundedCorner(it)?.radius }
        ?: RoundedCorner.POSITION_TOP_RIGHT
            .let { insets.getRoundedCorner(it)?.radius }
        ?: return null

    return radiusPx.takeIf { it > 0 }?.let { (it / density).dp }
}

/**
 * Fallback: the display's rounded corners.
 *
 * This is the source that actually works for a `TYPE_ACCESSIBILITY_OVERLAY`
 * window, and it matches what `dumpsys window displays` reports as
 * `mRoundedCorners`.
 *
 * `Display.getRoundedCorner(position)` is per-corner; the plural `RoundedCorners`
 * collection that dumpsys prints is not public API.
 */
private fun fromDisplay(context: Context, density: Float): Dp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    val display: Display = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.getDisplay(Display.DEFAULT_DISPLAY)
    }.getOrNull() ?: return null

    val radiusPx = runCatching {
        display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
    }.getOrNull()
        ?: runCatching {
            display.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius
        }.getOrNull()
        ?: return null

    return radiusPx.takeIf { it > 0 }?.let { (it / density).dp }
}

/**
 * Last-resort radius for devices that report nothing at all.
 *
 * Chosen to match the value the HIG quotes for the Dynamic Island (44 pt) so such
 * a device still gets documented geometry rather than an arbitrary number.
 */
val FALLBACK_DISPLAY_CORNER_RADIUS: Dp = 44.dp

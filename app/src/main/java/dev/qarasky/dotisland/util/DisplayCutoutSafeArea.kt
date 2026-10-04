/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.util

import android.hardware.display.DisplayManager
import android.graphics.RectF
import android.os.Build
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Rect

/** Actual cutout-path bounds where available; boundingRects are a conservative fallback. */
@Composable
fun rememberDisplayCameraBounds(): List<Rect> {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    return remember(context, view, configuration.orientation, configuration.screenWidthDp,
        configuration.screenHeightDp) {
        if (Build.VERSION.SDK_INT < 29) return@remember emptyList()
        val display = view.display ?: context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val cutout = display?.cutout ?: return@remember emptyList()
        val bounds = cutout.boundingRects.map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        val pathBounds = if (Build.VERSION.SDK_INT >= 31) {
            cutout.cutoutPath?.let { path ->
                val rect = RectF()
                path.computeBounds(rect, true)
                Rect(rect.left, rect.top, rect.right, rect.bottom).takeUnless { it.isEmpty }
            }
        } else null
        // Intersect each declared region separately rather than assuming one centred camera.
        val cameraBounds = bounds.map { declared ->
            pathBounds?.intersect(declared)?.takeUnless { it.isEmpty } ?: declared
        }
        android.util.Log.d("IslandCameraGeometry", "cutout rectangles=$bounds path=$pathBounds exclusions=$cameraBounds")
        cameraBounds
    }
}

/** Move only items that intersect the physical exclusion, not the entire header row. */
internal fun calculateCutoutAvoidingTop(
    leftPx: Float,
    widthPx: Float,
    topPx: Float,
    heightPx: Float,
    exclusions: List<Rect>,
    clearancePx: Float = 1f
): Float {
    var top = topPx
    for (rect in exclusions.sortedBy { it.top }) {
        if (leftPx < rect.right + clearancePx && leftPx + widthPx > rect.left - clearancePx &&
            top < rect.bottom + clearancePx && top + heightPx > rect.top - clearancePx) {
            top = rect.bottom + clearancePx
        }
    }
    return top
}

/** Screen-space safe top, even when the small overlay receives no window insets. */
@Composable
fun rememberDisplayCutoutSafeTop(fallbackTop: Dp): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(context, view, configuration.orientation, configuration.screenWidthDp,
        configuration.screenHeightDp, density.density, fallbackTop) {
        val display = view.display ?: context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val topPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && display != null) {
            // Unlike overlay WindowInsets, these are in display coordinates.
            runCatching { display.cutout?.safeInsetTop ?: 0 }.getOrNull()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.rootWindowInsets?.let { insets ->
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                insets.displayCutout?.let { it.safeInsetTop + location[1] } ?: 0
            }
        } else 0
        topPx?.let { with(density) { it.toDp() } } ?: fallbackTop
    }
}

/** Reserve the camera area inside the card without moving the outer morph. */
internal fun calculateExpandedContentTopPadding(
    cardTopDp: Float,
    cutoutSafeTopDp: Float,
    clearanceDp: Float = 6f
): Float = if (cutoutSafeTopDp > 0f) {
    (cutoutSafeTopDp + clearanceDp - cardTopDp).coerceAtLeast(0f)
} else 0f

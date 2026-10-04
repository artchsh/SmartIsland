/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sin

/** Nothing-inspired dots, smoothly dimmed in drawing rather than stepped layout. */
@Composable
internal fun DotMatrixActivity(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val phase = if (isPlaying) {
        rememberInfiniteTransition(label = "dotActivity").animateFloat(
            0f, 6.2831855f,
            infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "dotActivityPhase"
        )
    } else null
    Canvas(modifier) {
        val pitchX = size.width / 5f
        val pitchY = size.height / 7f
        val radius = minOf(pitchX, pitchY) * 0.29f
        repeat(5) { column ->
            val envelope = when (column) { 2 -> 1f; 1, 3 -> 0.8f; else -> 0.55f }
            val level = phase?.value?.let { envelope * (0.5f + 0.5f * abs(sin(it + column * 0.85f))) } ?: 0.2f
            repeat(7) { row ->
                drawCircle(Color.White, radius,
                    Offset(pitchX * (column + 0.5f), pitchY * (row + 0.5f)),
                    alpha = dotActivityOpacity(level, row))
            }
        }
    }
}

internal fun dotActivityOpacity(level: Float, row: Int): Float {
    val distance = abs(row - 3) / 3f
    return 0.12f + 0.68f * ((level - distance) * 4f + 0.5f).coerceIn(0f, 1f)
}

/** Brief diagonal wink, then rest. No frame clock runs during the idle delay. */
@Composable
internal fun StandbyDots(modifier: Modifier = Modifier, animate: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val wink = remember { Animatable(0f) }
    LaunchedEffect(animate, owner) {
        if (!animate) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    delay(9000)
                    val power = context.getSystemService(android.os.PowerManager::class.java)
                    val motion = android.provider.Settings.Global.getFloat(context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
                    if (power?.isInteractive == true && !power.isPowerSaveMode && motion) {
                        wink.animateTo(1f, tween(600, easing = LinearEasing))
                        wink.snapTo(0f)
                    }
                }
            } finally {
                // Lifecycle cancellation halts the animation, not just its drawing.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { wink.snapTo(0f) }
            }
        }
    }
    Canvas(modifier) {
        val pitch = minOf(size.width, size.height) / 3f
        repeat(3) { x -> repeat(3) { y ->
            drawCircle(Color.White, pitch * 0.27f,
                Offset(pitch * (x + 0.5f), pitch * (y + 0.5f)),
                alpha = standbyDotOpacity(wink.value, x, y))
        } }
    }
}

internal fun standbyDotOpacity(phase: Float, x: Int, y: Int): Float {
    val base = if (x == 1 || y == 1) 0.55f else 0.2f
    val sweep = ((phase * 1.6f - (x + y) * 0.15f).coerceIn(0f, 1f))
    return base + (0.9f - base) * sin(sweep * Math.PI.toFloat()).coerceAtLeast(0f)
}

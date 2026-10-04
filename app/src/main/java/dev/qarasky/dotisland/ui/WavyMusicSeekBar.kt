/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Media seek bar, styled to match the Dynamic Island.
 *
 * All values below were measured from a reference screenshot of the iOS expanded
 * music card rather than eyeballed:
 *
 * | Property        | Reference         |
 * | --------------- | ----------------- |
 * | Played segment  | `#9C9BA2`         |
 * | Remaining seg.  | `#242425`         |
 * | Track thickness | 7pt (21px @ 3x)  |
 * | End caps        | fully rounded     |
 * | Thumb           | none              |
 *
 * The previous implementation was a 4dp hairline with a white 8dp thumb knob
 * and an animated wave, none of which appear on iOS. The thumb is gone and the
 * track is the full 7pt capsule.
 *
 * @param showWave keeps Dot Island's original animated wave on the played
 *   segment. Off by default because it is a visible deviation from the reference;
 *   the wave is sampled at a fixed step rather than per pixel, which was the
 *   other half of AUDIT.md section 2.8.
 */
@Composable
fun WavyMusicSeekBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveColor: Color = ISLAND_SEEK_PLAYED,
    trackColor: Color = ISLAND_SEEK_REMAINING,
    thumbColor: Color = ISLAND_SEEK_PLAYED,
    waveHeight: Dp = 0.dp,
    trackThickness: Dp = 7.dp,
    thumbRadius: Dp = 0.dp,
    showWave: Boolean = false,
    progressLabel: String? = null
) {
    // The phase animation only needs to run while the wave is actually shown.
    // Previously it ran unconditionally on every media card, driving a redraw
    // loop even when nothing was animating.
    val phase = if (isPlaying && showWave) {
        rememberInfiniteTransition(label = "wavySeekBarPhase").animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 667, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
        )
    } else null
    val currentOnSeek by rememberUpdatedState(onSeek)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val activeProgress = if (isDragging) dragProgress else progress

    val density = LocalDensity.current
    val seekModifier = modifier
        .fillMaxWidth()
        .height(24.dp)
        // Exposed to assistive technology. The bar is a bare Canvas, so without
        // this it is completely invisible to TalkBack and cannot be operated by
        // any accessibility service. AUDIT.md section 5.12.
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(activeProgress, 0f..1f)
            if (progressLabel != null) contentDescription = progressLabel
            setProgress { value ->
                currentOnSeek(value.coerceIn(0f, 1f))
                true
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                dragProgress = newProgress
                currentOnSeek(newProgress)
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                },
                onDrag = { change, _ ->
                    change.consume()
                    dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                },
                onDragEnd = {
                    isDragging = false
                    currentOnSeek(dragProgress)
                },
                onDragCancel = {
                    isDragging = false
                }
            )
        }

    Canvas(modifier = seekModifier) {
        val width = size.width
        val height = size.height
        val progressX = (activeProgress.coerceIn(0f, 1f)) * width

        val baselineY = height / 2f
        val baseThicknessPx = trackThickness.toPx()
        val maxWaveHeightPx = waveHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val radius = CornerRadius(baseThicknessPx / 2f, baseThicknessPx / 2f)

        // 1. Remaining track. Drawn full-width underneath rather than only to the
        //    right of the thumb, so the played segment's rounded left cap sits on
        //    top of it instead of leaving a seam. This is how the reference looks.
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, baselineY - baseThicknessPx / 2f),
            size = Size(width, baseThicknessPx),
            cornerRadius = radius
        )

        // 2. Played segment.
        if (progressX > 0f) {
            if (showWave && maxWaveHeightPx > 0f) {
                drawWavyPlayedSegment(
                    progressX = progressX,
                    baselineY = baselineY,
                    baseThicknessPx = baseThicknessPx,
                    maxWaveHeightPx = maxWaveHeightPx,
                    phase = phase?.value ?: 0f,
                    color = waveColor,
                    density = density
                )
            } else {
                drawRoundRect(
                    color = waveColor,
                    topLeft = Offset(0f, baselineY - baseThicknessPx / 2f),
                    size = Size(progressX, baseThicknessPx),
                    cornerRadius = radius
                )
            }
        }

        // 3. Optional thumb. Zero radius by default, matching iOS.
        if (thumbRadiusPx > 0f) {
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(progressX, baselineY)
            )
        }
    }
}

/**
 * Draws the animated wave on the played segment.
 *
 * The original walked the progress width one pixel at a time, evaluating two
 * trig functions and calling `lineTo` for every vertex — roughly 1000 vertices
 * and 2000 transcendental calls per frame at 2.75 density. Sampling at a fixed
 * [WAVE_SAMPLE_STEP_DP] step cuts that by an order of magnitude with no visible
 * difference, because the wave's lowest frequency has a ~157dp wavelength.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavyPlayedSegment(
    progressX: Float,
    baselineY: Float,
    baseThicknessPx: Float,
    maxWaveHeightPx: Float,
    phase: Float,
    color: Color,
    density: androidx.compose.ui.unit.Density
) {
    val path = Path()
    val transitionWidthPx = with(density) { WAVE_FADE_WIDTH.toPx() }
    val stepPx = with(density) { WAVE_SAMPLE_STEP_DP.toPx() }.coerceAtLeast(1f)

    // Left cap, so the segment starts rounded like the reference.
    path.arcTo(
        rect = Rect(
            left = 0f,
            top = baselineY - baseThicknessPx / 2f,
            right = baseThicknessPx,
            bottom = baselineY + baseThicknessPx / 2f
        ),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = true
    )

    val pathStartX = baseThicknessPx / 2f
    if (progressX > pathStartX) {
        var x = pathStartX
        while (x <= progressX) {
            val dampLeft = ((x - pathStartX) / transitionWidthPx).coerceIn(0f, 1f)
            val dampRight = ((progressX - x) / transitionWidthPx).coerceIn(0f, 1f)
            val damp = dampLeft * dampRight

            val waveVal = sin(x * WAVE_FREQ_1 - phase) * 0.6f +
                cos(x * WAVE_FREQ_2 + phase * 1.3f) * 0.4f
            val normalizedWave = (waveVal + 1f) / 2f

            val y = baselineY - (baseThicknessPx / 2f) - (maxWaveHeightPx * damp * normalizedWave)
            path.lineTo(x, y)
            x += stepPx
        }
        // Always land exactly on the thumb position.
        path.lineTo(progressX, baselineY - baseThicknessPx / 2f)
    }

    path.lineTo(progressX, baselineY + baseThicknessPx / 2f)
    path.lineTo(pathStartX, baselineY + baseThicknessPx / 2f)
    path.close()

    drawPath(path = path, color = color, style = Fill)
}

/** Played portion of the media seek bar. Sampled `#9C9BA2` from the reference. */
val ISLAND_SEEK_PLAYED: Color = Color(0xFF9C9BA2)

/** Remaining portion of the media seek bar. Sampled `#242425` from the reference. */
val ISLAND_SEEK_REMAINING: Color = Color(0xFF242425)

private val WAVE_FADE_WIDTH = 24.dp

/** Horizontal sampling step for the wave. See [drawWavyPlayedSegment]. */
private val WAVE_SAMPLE_STEP_DP = 3.dp

private const val WAVE_FREQ_1 = 0.04f
private const val WAVE_FREQ_2 = 0.02f

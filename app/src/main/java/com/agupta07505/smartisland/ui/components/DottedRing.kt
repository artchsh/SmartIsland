/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * A ring of dots, `progress` of which are filled, optionally rotating.
 *
 * Rotation is applied as a `graphicsLayer` transform rather than being fed into
 * the dot geometry. Previously the animated angle was read in composition and
 * passed as a plain `Float`, which meant the calling glyph recomposed 60 times
 * a second and the draw loop recomputed 16 sin/cos pairs every frame.
 *
 * Reading the angle inside the graphicsLayer lambda defers it to the draw
 * phase, so the dots are laid out once and the GPU rotates them.
 *
 * @param rotationAngle supplies the current angle in degrees. Read inside the
 *   draw phase, so it may be a state read without causing recomposition.
 * @param color required rather than defaulted. It previously defaulted to a
 *   hardcoded green, which meant a caller that forgot to pass one silently got
 *   battery-green dots regardless of the mode it was rendering.
 */
@Composable
fun DottedRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0x33FFFFFF),
    numDots: Int = 16,
    dotRadius: androidx.compose.ui.unit.Dp = 1.2.dp,
    rotationAngle: () -> Float = { 0f }
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotationAngle()
        }
    ) {
        val radius = size.minDimension / 2f
        val dotRadiusPx = dotRadius.toPx()
        val activeDotsCount = (numDots * progress.coerceIn(0f, 1f)).toInt()
        for (i in 0 until numDots) {
            val angle = (-90f + i * 360f / numDots) * (Math.PI / 180f)
            val x = (center.x + radius * kotlin.math.cos(angle).toFloat())
            val y = (center.y + radius * kotlin.math.sin(angle).toFloat())
            val isActive = i < activeDotsCount
            drawCircle(
                color = if (isActive) color else trackColor,
                radius = dotRadiusPx,
                center = Offset(x, y)
            )
        }
    }
}

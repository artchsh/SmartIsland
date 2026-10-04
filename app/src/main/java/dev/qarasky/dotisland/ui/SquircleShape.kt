/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** A superellipse (exponent 4), not a square with circular corner arcs. */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        for (index in 0 until 128) {
            val angle = index * (Math.PI * 2 / 128)
            val x = size.width * (0.5f + 0.5f * squircleCoordinate(cos(angle).toFloat()))
            val y = size.height * (0.5f + 0.5f * squircleCoordinate(sin(angle).toFloat()))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

internal fun squircleCoordinate(value: Float): Float = sign(value) * sqrt(abs(value))

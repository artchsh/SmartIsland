/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.service

import com.agupta07505.smartisland.data.SmartIslandSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the collapsed overlay window geometry.
 *
 * This window sizing is what makes touch pass-through work without any hidden
 * API: the window is sized to the pill, and FLAG_NOT_TOUCH_MODAL delivers
 * touches outside those bounds to the window behind. If the window is wider than
 * it needs to be, the island silently swallows taps meant for the app underneath;
 * if it is narrower than the content, the pill clips.
 */
class CollapsedWindowGeometryTest {

    /** Nothing Phone (4a): 1224x2720 at an effective density of 2.75. */
    private val density = 2.75f
    private val screenWidthPx = 1224f

    private fun settings(
        width: Float = 112f,
        height: Float = 34f,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
        notchMode: Boolean = false,
        circleLeft: Boolean = false
    ) = SmartIslandSettings(
        width = width,
        height = height,
        xOffset = xOffset,
        yOffset = yOffset,
        enableNotchMode = notchMode,
        circlePosition = if (circleLeft) SmartIslandSettings.CIRCLE_POSITION_LEFT
        else SmartIslandSettings.CIRCLE_POSITION_RIGHT
    )

    private fun geometry(
        settings: SmartIslandSettings = settings(),
        notificationCount: Int = 1
    ) = computeCollapsedWindowGeometry(
        settings = settings,
        notificationCount = notificationCount,
        density = density,
        screenWidthPx = screenWidthPx
    )

    @Test
    fun `window is sized to the group plus the padding margin, never the full screen`() {
        val g = geometry()

        // group = 112dp pill; margin = 28dp per side.
        val expected = ((112f + 2 * 28f) * density).toInt()
        assertEquals(expected, g.widthPx)
        assertTrue(
            "Collapsed window must not span the display (was ${g.widthPx}px)",
            g.widthPx < screenWidthPx
        )
    }

    @Test
    fun `companion circle widens the window when two notifications are present`() {
        val single = geometry(notificationCount = 1)
        val split = geometry(notificationCount = 3)

        // Split adds gap (8dp) + circle (= height, 34dp).
        val deltaDp = 8f + 34f
        assertEquals(
            ((deltaDp) * density).toInt(),
            split.widthPx - single.widthPx
        )
    }

    @Test
    fun `notch mode suppresses the companion circle regardless of notification count`() {
        val withCompanion = geometry(settings(notchMode = false), notificationCount = 3)
        val notch = geometry(settings(notchMode = true), notificationCount = 3)

        assertEquals(
            "Notch mode must never reserve companion-circle space",
            geometry(settings(notchMode = true), notificationCount = 1).widthPx,
            notch.widthPx
        )
        assertTrue(notch.widthPx < withCompanion.widthPx)
    }

    @Test
    fun `left and right circle placement produce the same window width`() {
        val right = geometry(settings(circleLeft = false), notificationCount = 2)
        val left = geometry(settings(circleLeft = true), notificationCount = 2)

        assertEquals(right.widthPx, left.widthPx)
        // ...but mirrored, so the x offset flips sign.
        assertEquals(-right.xPx, left.xPx)
    }

    @Test
    fun `x offset is relative to screen centre because gravity is CENTER_HORIZONTAL`() {
        val centred = geometry(settings(xOffset = 0f))
        val right = geometry(settings(xOffset = 40f))
        val left = geometry(settings(xOffset = -40f))

        assertEquals(0, centred.xPx)
        assertEquals((40f * density).toInt(), right.xPx)
        assertEquals((-40f * density).toInt(), left.xPx)
    }

    @Test
    fun `visible group stays on screen for extreme x offsets`() {
        // Note what is being asserted: the GROUP (pill + companion circle), not
        // the window. When the group is clamped against a screen edge, the
        // window's 28dp margin still extends past it, so the window itself can
        // hang off-screen by up to 20dp. That is harmless: the overhang is
        // transparent padding, FLAG_LAYOUT_NO_LIMITS permits a window to extend
        // past the display, and the pill is never inside the overhang.
        //
        // The invariant that actually matters is that nothing VISIBLE is clipped.
        for (xOffset in listOf(-1000f, -140f, -50f, 0f, 50f, 140f, 1000f)) {
            for (circleLeft in listOf(true, false)) {
                for (count in listOf(0, 1, 3)) {
                    val s = settings(xOffset = xOffset, circleLeft = circleLeft)
                    val g = geometry(settings = s, notificationCount = count)

                    val hasCompanion = if (s.enableNotchMode) false else count >= 2
                    val groupWidthPx =
                        (s.width + if (hasCompanion) 8f + s.height else 0f) * density
                    val groupCentrePx = screenWidthPx / 2f + g.xPx
                    val groupLeftPx = groupCentrePx - groupWidthPx / 2f
                    val groupRightPx = groupCentrePx + groupWidthPx / 2f

                    val where = "xOffset=$xOffset circleLeft=$circleLeft count=$count"
                    assertTrue(
                        "Group left edge ${groupLeftPx}px off-screen ($where)",
                        groupLeftPx >= -1f
                    )
                    assertTrue(
                        "Group right edge ${groupRightPx}px off-screen ($where)",
                        groupRightPx <= screenWidthPx + 1f
                    )

                    // And the window must always fully contain the group,
                    // otherwise the pill itself would be clipped.
                    val windowLeftPx = groupCentrePx - g.widthPx / 2f
                    val windowRightPx = groupCentrePx + g.widthPx / 2f
                    assertTrue(
                        "Window does not contain the group ($where)",
                        windowLeftPx <= groupLeftPx && windowRightPx >= groupRightPx
                    )
                }
            }
        }
    }

    @Test
    fun `margin exceeds the collapsed drag clamp so the pill is never clipped`() {
        // IslandOverlayView clamps the collapsed horizontal drag to +/- 24dp.
        // The window margin must cover that or the pill clips mid-drag.
        val dragClampDp = 24f
        val g = geometry()
        val groupWidthPx = (112f * density).toInt()
        val marginEachSidePx = (g.widthPx - groupWidthPx) / 2f

        assertTrue(
            "Margin ${marginEachSidePx / density}dp per side is below the " +
                "${dragClampDp}dp drag clamp and would clip the pill",
            marginEachSidePx / density >= dragClampDp
        )
    }

    @Test
    fun `height leaves room for the pill elevation and shadow`() {
        val g = geometry()
        val expected = ((34f + 16f) * density).toInt()
        assertEquals(expected, g.heightPx)
    }

    @Test
    fun `notch mode docks to the top edge and ignores y offset`() {
        assertEquals(0, geometry(settings(notchMode = true, yOffset = 40f)).yPx)
        assertEquals((40f * density).toInt(), geometry(settings(notchMode = false, yOffset = 40f)).yPx)
        assertEquals(0, geometry(settings(yOffset = 0f)).yPx)
    }

    @Test
    fun `geometry is independent of a zero notification count`() {
        // Guards against a divide-by-zero or empty-list path: the window must
        // still be sized to the pill when nothing is showing.
        val empty = geometry(notificationCount = 0)
        val single = geometry(notificationCount = 1)
        assertEquals(single.widthPx, empty.widthPx)
    }

    @Test
    fun `maximum configured pill size still fits on screen`() {
        val g = geometry(
            settings = settings(width = SmartIslandSettings.MAX_WIDTH, height = SmartIslandSettings.MAX_HEIGHT),
            notificationCount = 3
        )
        assertTrue(
            "Widest pill + companion (${g.widthPx}px) must fit in ${screenWidthPx}px",
            g.widthPx <= screenWidthPx
        )
    }

    @Test
    fun `minimum configured pill size produces a positive window`() {
        val g = geometry(
            settings = settings(width = SmartIslandSettings.MIN_WIDTH, height = SmartIslandSettings.MIN_HEIGHT)
        )
        assertTrue("Window must have positive width", g.widthPx > 0)
        assertTrue("Window must have positive height", g.heightPx > 0)
    }
}

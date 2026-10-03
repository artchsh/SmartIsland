/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland

import com.agupta07505.smartisland.ui.CompactNotificationShape
import com.agupta07505.smartisland.ui.calculateCollapsedLayout
import com.agupta07505.smartisland.ui.calculateExpandedTopOffset
import com.agupta07505.smartisland.ui.calculateExpandedWidth
import com.agupta07505.smartisland.ui.compactNotificationShapes
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandOverlayLayoutTest {

    @Test
    fun compactIndicatorsMatchNotificationMatrix() {
        assertEquals(
            listOf(CompactNotificationShape.Circle),
            compactNotificationShapes(notificationCount = 2, expanded = false)
        )
        assertEquals(
            listOf(CompactNotificationShape.MiniPill),
            compactNotificationShapes(notificationCount = 2, expanded = true)
        )
        assertEquals(
            listOf(CompactNotificationShape.Circle),
            compactNotificationShapes(notificationCount = 3, expanded = false)
        )
        assertEquals(
            listOf(CompactNotificationShape.MiniPill, CompactNotificationShape.Circle),
            compactNotificationShapes(notificationCount = 3, expanded = true)
        )
    }

    /**
     * Exercises the REAL collapsed-layout function.
     *
     * The previous version of this test re-implemented the clamping arithmetic
     * inline (lines 48-70 of the old file), duplicating IslandOverlayView's
     * composable body because the math was unreachable from a unit test. The
     * test therefore asserted that its own copy agreed with itself and stayed
     * green no matter what the composable did. The copy had already drifted:
     * the production `when` has a `!hasCompanion` branch that the copy omitted
     * entirely, so the single-notification case was never tested.
     *
     * The math now lives in `calculateCollapsedLayout` and is called directly.
     */
    @Test
    fun splitModeGeometryKeepsPillAndCircleOnScreenWithoutOverlap() {
        val screenWidthDp = 1080f / 2.75f // matches the old px-based fixtures
        val screenCenterDp = screenWidthDp / 2f
        val pillWidthDp = 112f
        val circleSizeDp = 34f
        val compactGapDp = 8f
        val edgePaddingDp = 8f

        for (hasCompanion in listOf(true, false)) {
            for (isCircleLeft in listOf(true, false)) {
                for (xOffsetDp in listOf(-130f, 0f, 130f, 400f, -400f)) {
                    val g = calculateCollapsedLayout(
                        screenWidthDp = screenWidthDp,
                        screenCenterDp = screenCenterDp,
                        pillWidthDp = pillWidthDp,
                        circleSizeDp = circleSizeDp,
                        xOffsetDp = xOffsetDp,
                        hasCompanion = hasCompanion,
                        isCircleLeft = isCircleLeft,
                        isFullWidth = false,
                        enableNotchMode = false,
                        compactGapDp = compactGapDp
                    )
                    val where = "companion=$hasCompanion circleLeft=$isCircleLeft x=$xOffsetDp"

                    val mainRight = g.mainLeftDp + pillWidthDp
                    val circleRight = g.circleLeftDp + circleSizeDp

                    // 1. Pill stays within the screen with a sane edge margin,
                    //    even for extreme x offsets that would push it off-screen.
                    org.junit.Assert.assertTrue(
                        "Pill left (${g.mainLeftDp}) off-screen ($where)",
                        g.mainLeftDp >= edgePaddingDp - 0.01f
                    )
                    org.junit.Assert.assertTrue(
                        "Pill right ($mainRight) off-screen ($where)",
                        mainRight <= screenWidthDp - edgePaddingDp + 0.01f
                    )

                    if (hasCompanion) {
                        // 2. Circle stays within the screen.
                        org.junit.Assert.assertTrue(
                            "Circle left (${g.circleLeftDp}) off-screen ($where)",
                            g.circleLeftDp >= edgePaddingDp - 0.01f
                        )
                        org.junit.Assert.assertTrue(
                            "Circle right ($circleRight) off-screen ($where)",
                            circleRight <= screenWidthDp - edgePaddingDp + 0.01f
                        )

                        // 3. Circle never collapses into the pill.
                        val gap = if (isCircleLeft) {
                            g.mainLeftDp - circleRight
                        } else {
                            g.circleLeftDp - mainRight
                        }
                        org.junit.Assert.assertTrue(
                            "Circle overlaps pill, gap ($gap) < compactGap ($compactGapDp) ($where)",
                            gap >= compactGapDp - 0.01f
                        )

                        // 4. Circle is on the requested side.
                        if (isCircleLeft) {
                            org.junit.Assert.assertTrue(
                                "Circle should precede the pill ($where)",
                                g.circleLeftDp < g.mainLeftDp
                            )
                        } else {
                            org.junit.Assert.assertTrue(
                                "Circle should follow the pill ($where)",
                                g.circleLeftDp > mainRight
                            )
                        }
                    } else {
                        // 5. With no companion the circle offset is meaningless but
                        //    must not drag the pill out of bounds.
                        org.junit.Assert.assertTrue(
                            "No-companion pill left (${g.mainLeftDp}) off-screen ($where)",
                            g.mainLeftDp >= edgePaddingDp - 0.01f
                        )
                    }
                }
            }
        }
    }

    /**
     * Notch mode ignores the companion circle and uses the raw x offset, which
     * is the branch most likely to regress silently because it is visually
     * indistinguishable from the normal case when no companion is present.
     */
    @Test
    fun notchModeUsesRawXOffsetRegardlessOfCompanion() {
        val screenWidthDp = 400f
        for (hasCompanion in listOf(true, false)) {
            val g = calculateCollapsedLayout(
                screenWidthDp = screenWidthDp,
                screenCenterDp = screenWidthDp / 2f,
                pillWidthDp = 112f,
                circleSizeDp = 34f,
                xOffsetDp = 12f,
                hasCompanion = hasCompanion,
                isCircleLeft = false,
                isFullWidth = false,
                enableNotchMode = true
            )
            assertEquals("Notch mode must use the raw x offset", 12f, g.mainOffsetDp, 0.001f)
        }
    }

    /**
     * isFullWidth centres the pill on the screen rather than on the
     * pill+circle group, which is what the expanded full-bleed window needs.
     */
    @Test
    fun fullWidthModeCentresPillOnScreenNotOnGroup() {
        val screenWidthDp = 400f
        val screenCenterDp = screenWidthDp / 2f
        val pillWidthDp = 112f

        val grouped = calculateCollapsedLayout(
            screenWidthDp = screenWidthDp,
            screenCenterDp = screenCenterDp,
            pillWidthDp = pillWidthDp,
            circleSizeDp = 34f,
            xOffsetDp = 0f,
            hasCompanion = true,
            isCircleLeft = false,
            isFullWidth = false,
            enableNotchMode = false
        )
        val fullWidth = calculateCollapsedLayout(
            screenWidthDp = screenWidthDp,
            screenCenterDp = screenCenterDp,
            pillWidthDp = pillWidthDp,
            circleSizeDp = 34f,
            xOffsetDp = 0f,
            hasCompanion = true,
            isCircleLeft = false,
            isFullWidth = true,
            enableNotchMode = false
        )

        // With a companion on the right, the group centre sits left of the pill
        // centre, so the two offsets must differ.
        org.junit.Assert.assertTrue(
            "Grouped and full-width offsets should differ when a companion is present",
            kotlin.math.abs(grouped.mainOffsetDp - fullWidth.mainOffsetDp) > 0.01f
        )
        // fullWidth pins the pill centre to the screen centre exactly.
        val pillCenter = fullWidth.mainLeftDp + pillWidthDp / 2f
        assertEquals(screenCenterDp, pillCenter, 0.01f)
    }

    /**
     * iOS geometry: the expanded card is inset from the screen edges by a fixed
     * margin, equal on top, left and right.
     *
     * The previous implementation used `screenWidthDp * 0.95`, which is a ratio
     * rather than the HIG's fixed inset, so the horizontal inset did not match
     * the vertical one and the card's top edge did not line up with the pill's.
     * See REDESIGN.md section 3.2.
     */
    @Test
    fun expandedWidthIsTheScreenMinusEqualSideMargins() {
        val margin = 12f
        val widthDp = 445f // Nothing Phone (4a): 1224px / 2.75

        assertEquals(
            widthDp - 2f * margin,
            calculateExpandedWidth(
                isLandscape = false,
                screenWidthDp = widthDp,
                screenHeightDp = 989f
            ),
            0.01f
        )
    }

    /**
     * The card must be inset identically on the top and on the sides. This is the
     * property that makes the morph start from zero displacement: the pill's
     * default y offset is the same value, so the card's top edge lands exactly on
     * the pill's top edge.
     */
    @Test
    fun expandedTopOffsetEqualsTheSideMargin() {
        val margin = 12f
        val widthDp = 445f
        val cardWidth = calculateExpandedWidth(false, widthDp, 989f)
        val sideInset = (widthDp - cardWidth) / 2f
        val topInset = calculateExpandedTopOffset(
            enableNotchMode = false,
            hasCompanion = false,
            statusBarHeightDp = 24f
        )

        assertEquals(
            "Top and side insets must match so the morph has no vertical jump",
            sideInset,
            topInset,
            0.01f
        )
        assertEquals(margin, topInset, 0.01f)
    }

    /**
     * The old behaviour returned `maxOf(statusBarHeightDp, circleSizeDp +
     * compactGapDp)` = 42dp against a pill at 12dp. That 30dp downward jump is
     * exactly what made the expansion read as a separate panel appearing below the
     * pill. Guard against regressing to it.
     */
    @Test
    fun expandedTopOffsetDoesNotDropBelowThePillForTallStatusBarsOrCompanions() {
        val pillTopDp = 12f
        for (statusBar in listOf(24f, 40f, 56f)) {
            for (hasCompanion in listOf(true, false)) {
                val offset = calculateExpandedTopOffset(
                    enableNotchMode = false,
                    hasCompanion = hasCompanion,
                    statusBarHeightDp = statusBar,
                    circleSizeDp = 34f,
                    compactGapDp = 8f
                )
                assertEquals(
                    "Card top must not depend on status bar height or companion (statusBar=$statusBar, companion=$hasCompanion)",
                    pillTopDp,
                    offset,
                    0.01f
                )
            }
        }
    }

    /**
     * Notch mode is docked to the top edge but must still clear the hardware
     * cutout, so it keeps its own behaviour.
     */
    @Test
    fun notchModeClearsTheHardwareCutout() {
        val notchHeight = 35f
        val offset = calculateExpandedTopOffset(
            enableNotchMode = true,
            hasCompanion = false,
            statusBarHeightDp = 24f,
            notchHeightDp = notchHeight
        )
        org.junit.Assert.assertTrue(
            "Notch-mode offset ($offset) must clear the cutout ($notchHeight)",
            offset >= notchHeight
        )
        assertEquals(notchHeight, offset, 0.01f)
    }

    /**
     * Landscape uses the narrower dimension, and the result must stay usable on a
     * very short screen rather than collapsing.
     */
    @Test
    fun landscapeUsesTheNarrowerDimensionAndStaysUsable() {
        val landscapeWidth = 989f
        val landscapeHeight = 445f

        assertEquals(
            landscapeHeight - 2f * 12f,
            calculateExpandedWidth(
                isLandscape = true,
                screenWidthDp = landscapeWidth,
                screenHeightDp = landscapeHeight
            ),
            0.01f
        )

        val tiny = calculateExpandedWidth(
            isLandscape = true,
            screenWidthDp = 320f,
            screenHeightDp = 240f
        )
        org.junit.Assert.assertTrue("Card must never collapse below the floor", tiny >= 260f)
    }

    @Test
    fun portraitAndLandscapeAgreeOnTheSameLogicalScreen() {
        val portraitWidth = calculateExpandedWidth(false, 445f, 989f)
        val landscapeWidth = calculateExpandedWidth(true, 989f, 445f)
        assertEquals(portraitWidth, landscapeWidth, 0.01f)
    }

    @Test
    fun secondaryBubbleExpandedOffsetShiftsRightInLeftCircleMode() {
        val screenCenter = 200f
        val expandedCompactX = 144f // main pill start
        val miniPillWidth = 112f
        val circleSize = 34f
        val compactGap = 8f

        val pillCenterOffset = (expandedCompactX + miniPillWidth / 2f) - screenCenter

        // Collapsed left-side circle center offset: circle is to the LEFT of pill
        val collapsedLeftCircleOffset = (expandedCompactX - compactGap - circleSize / 2f) - screenCenter

        // 1. With ONLY 2 notifications (secondaryIsPill == true):
        // In left-side circle mode, it MUST shift RIGHT to pill location
        val expandedOffsetLeftMode = com.agupta07505.smartisland.ui.calculateSecondaryExpandedOffset(
            secondaryIsPill = true,
            isCircleLeft = true,
            isFullWidth = true,
            expandedCompactX = expandedCompactX,
            screenCenter = screenCenter,
            miniPillWidth = miniPillWidth,
            circleSize = circleSize,
            compactGap = compactGap
        )
        assertEquals(pillCenterOffset, expandedOffsetLeftMode, 0.01f)
        org.junit.Assert.assertTrue(
            "Left circle must shift to the right into pill position ($expandedOffsetLeftMode > $collapsedLeftCircleOffset)",
            expandedOffsetLeftMode > collapsedLeftCircleOffset
        )

        // 2. Right-side circle mode also lands at pill location
        val expandedOffsetRightMode = com.agupta07505.smartisland.ui.calculateSecondaryExpandedOffset(
            secondaryIsPill = true,
            isCircleLeft = false,
            isFullWidth = true,
            expandedCompactX = expandedCompactX,
            screenCenter = screenCenter,
            miniPillWidth = miniPillWidth,
            circleSize = circleSize,
            compactGap = compactGap
        )
        assertEquals(pillCenterOffset, expandedOffsetRightMode, 0.01f)

        // 3. With 3+ notifications (secondaryIsPill == false):
        // Left circle stays on the left
        val threeNotifLeftOffset = com.agupta07505.smartisland.ui.calculateSecondaryExpandedOffset(
            secondaryIsPill = false,
            isCircleLeft = true,
            isFullWidth = true,
            expandedCompactX = expandedCompactX,
            screenCenter = screenCenter,
            miniPillWidth = miniPillWidth,
            circleSize = circleSize,
            compactGap = compactGap
        )
        assertEquals(collapsedLeftCircleOffset, threeNotifLeftOffset, 0.01f)
    }
}

/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland

import com.agupta07505.smartisland.ui.CompactNotificationShape
import com.agupta07505.smartisland.ui.calculateCollapsedLayout
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

    @Test
    fun landscapeExpandedWidthIsFixedAndDoesNotFillLandscapeScreenWidth() {
        val landscapeScreenWidth = 914f
        val landscapeScreenHeight = 411f

        val portraitExpandedWidth = com.agupta07505.smartisland.ui.calculateExpandedWidth(
            isLandscape = false,
            screenWidthDp = landscapeScreenHeight, // 411dp in portrait
            screenHeightDp = landscapeScreenWidth  // 914dp in portrait
        )
        val landscapeExpandedWidth = com.agupta07505.smartisland.ui.calculateExpandedWidth(
            isLandscape = true,
            screenWidthDp = landscapeScreenWidth,  // 914dp in landscape
            screenHeightDp = landscapeScreenHeight // 411dp in landscape
        )

        // The landscape expanded width must equal the portrait compact width, NOT 95% of 914dp
        assertEquals(portraitExpandedWidth, landscapeExpandedWidth, 0.01f)
        org.junit.Assert.assertTrue(landscapeExpandedWidth < 450f)
        org.junit.Assert.assertTrue(landscapeExpandedWidth < landscapeScreenWidth * 0.95f)

        // Clamping bounds for tablets / ultra-wides
        val tabletLandscapeWidth = com.agupta07505.smartisland.ui.calculateExpandedWidth(
            isLandscape = true,
            screenWidthDp = 1280f,
            screenHeightDp = 800f
        )
        assertEquals(440f, tabletLandscapeWidth, 0.01f)

        val smallLandscapeWidth = com.agupta07505.smartisland.ui.calculateExpandedWidth(
            isLandscape = true,
            screenWidthDp = 640f,
            screenHeightDp = 320f
        )
        assertEquals(340f, smallLandscapeWidth, 0.01f)
    }

    @Test
    fun notchModeExpandedTopOffsetClearsHardwareNotchAndStatusBar() {
        val notchHeight = 35f
        val statusBarHeightStandard = 24f
        val statusBarHeightTall = 40f

        // Notch mode: MUST clear both the hardware notch height and status bar height with a safe gap
        val offsetStandard = com.agupta07505.smartisland.ui.calculateExpandedTopOffset(
            enableNotchMode = true,
            hasCompanion = false,
            statusBarHeightDp = statusBarHeightStandard,
            notchHeightDp = notchHeight
        )
        org.junit.Assert.assertTrue(
            "Expanded offset in notch mode ($offsetStandard) must strictly exceed notch height ($notchHeight)",
            offsetStandard >= notchHeight + 8f
        )
        org.junit.Assert.assertTrue(
            "Expanded offset in notch mode ($offsetStandard) must strictly exceed status bar ($statusBarHeightStandard)",
            offsetStandard >= statusBarHeightStandard + 8f
        )
        assertEquals(43f, offsetStandard, 0.01f)

        // Tall notch device (e.g., Pixel 3 XL or iPhone deep notch)
        val offsetTall = com.agupta07505.smartisland.ui.calculateExpandedTopOffset(
            enableNotchMode = true,
            hasCompanion = false,
            statusBarHeightDp = statusBarHeightTall,
            notchHeightDp = notchHeight
        )
        org.junit.Assert.assertTrue(
            "Expanded offset on tall status bar ($offsetTall) must clear tall status bar ($statusBarHeightTall)",
            offsetTall >= statusBarHeightTall + 8f
        )
        assertEquals(48f, offsetTall, 0.01f)

        // Non-notch mode preserves standard status bar positioning
        val normalOffset = com.agupta07505.smartisland.ui.calculateExpandedTopOffset(
            enableNotchMode = false,
            hasCompanion = false,
            statusBarHeightDp = statusBarHeightStandard
        )
        assertEquals(statusBarHeightStandard, normalOffset, 0.01f)

        val companionOffset = com.agupta07505.smartisland.ui.calculateExpandedTopOffset(
            enableNotchMode = false,
            hasCompanion = true,
            statusBarHeightDp = statusBarHeightStandard,
            circleSizeDp = 34f,
            compactGapDp = 8f
        )
        assertEquals(42f, companionOffset, 0.01f)
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

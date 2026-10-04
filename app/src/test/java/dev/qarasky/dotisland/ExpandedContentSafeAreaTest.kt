/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland

import dev.qarasky.dotisland.util.calculateExpandedContentTopPadding
import dev.qarasky.dotisland.util.calculateCutoutAvoidingTop
import androidx.compose.ui.geometry.Rect
import dev.qarasky.dotisland.ui.expanded.formatRemainingDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandedContentSafeAreaTest {
    private val camera = Rect(578.2f, 47.6f, 645.8f, 115.5f)

    @Test fun sideArtworkDoesNotGetCameraPadding() {
        assertEquals(55f, calculateCutoutAvoidingTop(99f, 176f, 55f, 176f, listOf(camera)), 0f)
    }

    @Test fun overlappingTitleClearsPathByOnePixel() {
        assertEquals(116.5f, calculateCutoutAvoidingTop(324f, 600f, 84f, 118f, listOf(camera)), 0.001f)
    }

    @Test fun titleAlreadyBelowCameraDoesNotMove() {
        assertEquals(130f, calculateCutoutAvoidingTop(324f, 600f, 130f, 118f, listOf(camera)), 0f)
    }

    @Test fun rightHandActivityBarsDoNotGetCameraPadding() {
        assertEquals(90f, calculateCutoutAvoidingTop(1053f, 71f, 90f, 88f, listOf(camera)), 0f)
    }

    @Test fun noCameraLeavesHeaderUntouched() {
        assertEquals(84f, calculateCutoutAvoidingTop(324f, 600f, 84f, 118f, emptyList()), 0f)
    }

    @Test fun offCentreCameraProtectsArtworkWhenNecessary() {
        assertEquals(116.5f, calculateCutoutAvoidingTop(550f, 176f, 55f, 176f, listOf(camera)), 0.001f)
    }

    @Test fun contentClearsNothingPhoneCutoutAtOverrideDensity() {
        val safeTop = 162f / 2.75f
        val padding = calculateExpandedContentTopPadding(12f, safeTop)
        assertEquals(safeTop + 6f, 12f + padding, 0.001f)
    }

    @Test fun noCutoutAddsNoPadding() {
        assertEquals(0f, calculateExpandedContentTopPadding(12f, 0f), 0f)
    }

    @Test fun cardAlreadyBelowCutoutAddsNoPadding() {
        assertEquals(0f, calculateExpandedContentTopPadding(80f, 59f), 0f)
    }

    @Test fun higherCardRetainsMoreCameraSpace() {
        assertEquals(65f, calculateExpandedContentTopPadding(0f, 59f), 0f)
    }

    @Test fun remainingTimeCountsDownAndNeverGoesNegative() {
        assertEquals("−2:11", formatRemainingDuration(116_000, 247_000))
        assertEquals("−0:00", formatRemainingDuration(250_000, 247_000))
        assertEquals("--:--", formatRemainingDuration(0, null))
        assertEquals("--:--", formatRemainingDuration(0, 0))
    }
}

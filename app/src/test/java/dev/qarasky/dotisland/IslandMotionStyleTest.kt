/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland

import dev.qarasky.dotisland.service.selectOverlayRefreshRate
import dev.qarasky.dotisland.ui.squircleCoordinate
import dev.qarasky.dotisland.ui.presentationFor
import dev.qarasky.dotisland.ui.EXPAND_HAPTIC_DURATION_MS
import dev.qarasky.dotisland.ui.EXPAND_HAPTIC_AMPLITUDE
import dev.qarasky.dotisland.model.IslandMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class IslandMotionStyleTest {
    @Test fun expansionPulseIsShortAndLowAmplitude() {
        assertEquals(12L, EXPAND_HAPTIC_DURATION_MS)
        assertTrue(EXPAND_HAPTIC_DURATION_MS < 60L)
        assertTrue(EXPAND_HAPTIC_AMPLITUDE in 1..64)
    }
    @Test fun balancedMusicInsetsHaveRoomWithoutShrinkingArtworkOrButtons() {
        val body = 20f + 64f + 4f + 24f + 48f + 20f
        assertEquals(180f, body, 0f)
        assertEquals(body, presentationFor(IslandMode.Music).estimatedHeight.value, 0f)
        assertEquals(body, presentationFor(IslandMode.Music).maxHeight.value, 0f)
        assertEquals(160f, presentationFor(IslandMode.IncomingCall).maxHeight.value, 0f)
    }
    @Test fun refreshHintSelects120WithoutChangingResolution() {
        assertEquals(120f, selectOverlayRefreshRate(listOf(30f, 60f, 90f, 120.00001f)), 0f)
    }
    @Test fun slowerPanelsGetTheirSupportedRate() {
        assertEquals(90f, selectOverlayRefreshRate(listOf(60f, 90f)), 0f)
        assertEquals(60f, selectOverlayRefreshRate(listOf(60f)), 0f)
    }
    @Test fun invalidRatesDoNotCreateImpossibleRequests() {
        assertEquals(0f, selectOverlayRefreshRate(emptyList()), 0f)
        assertEquals(0f, selectOverlayRefreshRate(listOf(Float.NaN, -1f, 0f, 144f)), 0f)
    }
    @Test fun squircleIsSymmetricAndInsideUnitBounds() {
        for (value in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(-squircleCoordinate(value), squircleCoordinate(-value), 0.001f)
            assertTrue(squircleCoordinate(value) in 0f..1f)
        }
    }
    @Test fun squircleFollowsExponentFourInsteadOfCircularCorners() {
        for (degrees in 0..360 step 5) {
            val angle = Math.toRadians(degrees.toDouble())
            val x = squircleCoordinate(cos(angle).toFloat())
            val y = squircleCoordinate(sin(angle).toFloat())
            assertEquals(1f, x.pow(4) + y.pow(4), 0.001f)
        }
    }
}

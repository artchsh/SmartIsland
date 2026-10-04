/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland

import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.CompactContentState
import dev.qarasky.dotisland.ui.dotActivityOpacity
import dev.qarasky.dotisland.ui.expandedContentFadeSpec
import dev.qarasky.dotisland.ui.standbyDotOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandContentMotionTest {
    @Test fun idleWinkReturnsToRestAndNeverChangesGeometry() {
        for (x in 0..2) for (y in 0..2) {
            assertEquals(standbyDotOpacity(0f, x, y), standbyDotOpacity(1f, x, y), 0.001f)
            for (step in 0..100) assertTrue(standbyDotOpacity(step / 100f, x, y) in 0.19f..0.91f)
        }
        assertTrue(standbyDotOpacity(0.3f, 0, 0) > standbyDotOpacity(0f, 0, 0))
    }
    private val music = IslandNotification(
        key = "spotify", packageName = "com.spotify.music", appName = "Spotify",
        title = "Song", text = "Artist", timeMillis = 0L, mode = IslandMode.Music
    )

    @Test fun collapseFadeStartsWithoutEntryDelayAndFinishesEarly() {
        val exit = expandedContentFadeSpec(false)
        assertEquals(0, exit.delay)
        assertEquals(100, exit.durationMillis)
        assertTrue(exit.durationMillis < expandedContentFadeSpec(true).durationMillis)
    }

    @Test fun expansionRetainsRoomBeforeContentEntry() {
        assertEquals(70, expandedContentFadeSpec(true).delay)
        assertEquals(200, expandedContentFadeSpec(true).durationMillis)
    }

    @Test fun metadataUpdatesDoNotRestartActivityAppearance() {
        assertEquals(CompactContentState(music, false).identity,
            CompactContentState(music.copy(title = "Next song", mediaPositionMs = 5000L), false).identity)
    }

    @Test fun suppressedMusicIsDistinctFromActualIdleAndReturningMusic() {
        val idle = CompactContentState(null, true).identity
        val suppressed = CompactContentState(null, false).identity
        val active = CompactContentState(music, false).identity
        assertNotEquals(idle, suppressed)
        assertNotEquals(suppressed, active)
        assertNotEquals(idle, active)
    }

    @Test fun dotOpacityIsBoundedAndSymmetric() {
        for (step in 0..100) {
            val level = step / 100f
            for (row in 0..6) {
                assertTrue(dotActivityOpacity(level, row) in 0.12f..0.81f)
                assertEquals(dotActivityOpacity(level, row), dotActivityOpacity(level, 6 - row), 0.0001f)
            }
        }
    }

    @Test fun dotBrightnessChangesContinuouslyInsteadOfSnappingRows() {
        val before = dotActivityOpacity(0.5f, 2)
        val after = dotActivityOpacity(0.501f, 2)
        assertTrue(after >= before)
        assertTrue(after - before < 0.004f)
    }
}

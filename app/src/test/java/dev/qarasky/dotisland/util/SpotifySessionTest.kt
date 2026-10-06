package dev.qarasky.dotisland.util

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class SpotifySessionTest {
    @Test fun deadStatesAreOnlyNoneStoppedErrorAndNull() {
        assertTrue(isSpotifyDeadState(null))
        assertTrue(isSpotifyDeadState(PlaybackState.STATE_NONE))
        assertTrue(isSpotifyDeadState(PlaybackState.STATE_STOPPED))
        assertTrue(isSpotifyDeadState(PlaybackState.STATE_ERROR))
        assertFalse(isSpotifyDeadState(PlaybackState.STATE_PLAYING))
        assertFalse(isSpotifyDeadState(PlaybackState.STATE_PAUSED))
        assertFalse(isSpotifyDeadState(PlaybackState.STATE_BUFFERING))
        assertFalse(isSpotifyDeadState(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertFalse(isSpotifyDeadState(PlaybackState.STATE_SKIPPING_TO_PREVIOUS))
    }

    @Test fun transitionalStatesCountAsPlayingSoASkipNeverFlashesIdle() {
        assertTrue(isSpotifyPlayingState(PlaybackState.STATE_PLAYING))
        assertTrue(isSpotifyPlayingState(PlaybackState.STATE_BUFFERING))
        assertTrue(isSpotifyPlayingState(PlaybackState.STATE_CONNECTING))
        assertTrue(isSpotifyPlayingState(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertTrue(isSpotifyPlayingState(PlaybackState.STATE_SKIPPING_TO_PREVIOUS))
        assertFalse(isSpotifyPlayingState(PlaybackState.STATE_PAUSED))
        assertFalse(isSpotifyPlayingState(PlaybackState.STATE_STOPPED))
        assertFalse(isSpotifyPlayingState(null))
    }

    @Test fun bestSessionPrefersPlayingThenTransitionalThenPaused() {
        assertEquals(
            1,
            bestSpotifyStateIndex(
                listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
            )
        )
        // A buffering session outranks a paused one during a track switch.
        assertEquals(
            0,
            bestSpotifyStateIndex(listOf(PlaybackState.STATE_BUFFERING, PlaybackState.STATE_PAUSED))
        )
    }

    @Test fun bestSessionIsNullOnlyWhenEverythingIsDead() {
        assertNull(bestSpotifyStateIndex(emptyList()))
        assertNull(
            bestSpotifyStateIndex(
                listOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR, null)
            )
        )
        assertEquals(1, bestSpotifyStateIndex(listOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_PAUSED)))
    }
}

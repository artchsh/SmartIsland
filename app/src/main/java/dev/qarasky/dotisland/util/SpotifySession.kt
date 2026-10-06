/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.util

import android.media.session.PlaybackState

/**
 * Pure rules for tracking the Spotify session across track switches.
 *
 * During next/previous the session briefly reports a transitional state
 * (buffering, connecting, skipping) or, for an instant, nothing usable at all.
 * The old code only accepted PLAYING/PAUSED, so a skip detached the controller
 * and removed the island — the pill flashed to idle mid-switch even though the
 * music never stopped. These helpers keep the island pinned to the session
 * through the transition and only treat genuinely-dead states as gone.
 */
fun isSpotifyDeadState(state: Int?): Boolean = state == null ||
    state == PlaybackState.STATE_NONE ||
    state == PlaybackState.STATE_STOPPED ||
    state == PlaybackState.STATE_ERROR

/** Transitional states still mean "music is happening" for the pill. */
fun isSpotifyPlayingState(state: Int?): Boolean =
    !isSpotifyDeadState(state) && state != PlaybackState.STATE_PAUSED

private fun spotifySessionRank(state: Int?): Int = when (state) {
    PlaybackState.STATE_PLAYING -> 0
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING -> 1
    PlaybackState.STATE_PAUSED -> 2
    else -> 3
}

/**
 * Index of the best candidate in [states], or null when every session is dead.
 * The service maps this back onto its MediaController list.
 */
fun bestSpotifyStateIndex(states: List<Int?>): Int? {
    var best: Int? = null
    states.forEachIndexed { index, state ->
        if (isSpotifyDeadState(state)) return@forEachIndexed
        if (best == null || spotifySessionRank(state) < spotifySessionRank(states[best!!])) {
            best = index
        }
    }
    return best
}

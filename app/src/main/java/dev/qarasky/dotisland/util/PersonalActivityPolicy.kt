/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.util

import dev.qarasky.dotisland.model.IslandMode

object PersonalActivityPolicy {
    const val SPOTIFY = "com.spotify.music"
    const val DODO = "ru.dodopizza.app"
    const val DIALER = "com.google.android.dialer"
    const val TELECOM = "com.android.server.telecom"

    fun mode(packageName: String, category: String?, isMedia: Boolean, ongoing: Boolean,
        title: String, text: String, progressMax: Int = 0, groupSummary: Boolean = false): IslandMode = when {
        groupSummary -> IslandMode.Empty
        packageName == SPOTIFY && isMedia -> IslandMode.Music
        packageName in setOf(DIALER, TELECOM) && category == "call" -> IslandMode.IncomingCall
        packageName == DODO && LiveActivityParser.isOrderTracking(title, text, ongoing, progressMax) -> IslandMode.LiveActivity
        else -> IslandMode.Empty
    }
}

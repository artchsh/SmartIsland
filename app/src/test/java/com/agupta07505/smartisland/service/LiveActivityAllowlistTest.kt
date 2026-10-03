/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.service

import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.model.IslandMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Live Activity allowlist.
 *
 * The Dynamic Island is a glanceable surface for a small number of *ongoing*
 * activities. Apple's own framing (WWDC23 "Meet ActivityKit") is that a Live
 * Activity "has a discrete start and end", is started by explicit action in the
 * app, and shows "most essential content". That is the opposite of mirroring
 * every notification into a status surface, which is what this gate replaces.
 */
class LiveActivityAllowlistTest {

    private fun settings(
        appsOnly: Boolean = true,
        packages: Set<String> = setOf("com.spotify.music", "com.dodopizza.app")
    ) = SmartIslandSettings(
        liveActivityAppsOnly = appsOnly,
        liveActivityPackages = packages
    )

    @Test
    fun `an allowlisted app is admitted`() {
        assertTrue(
            isLiveActivitySource("com.spotify.music", IslandMode.LiveActivity, settings())
        )
        assertTrue(
            isLiveActivitySource("com.dodopizza.app", IslandMode.LiveActivity, settings())
        )
    }

    @Test
    fun `an app outside the allowlist is left to the notification shade`() {
        assertFalse(
            isLiveActivitySource("com.whatsapp", IslandMode.Notification, settings())
        )
        assertFalse(
            isLiveActivitySource("com.android.chrome", IslandMode.Notification, settings())
        )
    }

    @Test
    fun `disabling the gate admits everything`() {
        val permissive = settings(appsOnly = false, packages = emptySet())
        assertTrue(isLiveActivitySource("com.whatsapp", IslandMode.Notification, permissive))
        assertTrue(isLiveActivitySource("com.anything", IslandMode.LiveActivity, permissive))
    }

    @Test
    fun `incoming calls bypass the allowlist`() {
        // A ringing phone is definitionally something the user must not miss, so
        // it is admitted regardless of the package allowlist.
        assertTrue(
            isLiveActivitySource(
                "com.some.random.dialer",
                IslandMode.IncomingCall,
                settings(packages = emptySet())
            )
        )
    }

    @Test
    fun `ongoing activities bypass the allowlist even from unlisted apps`() {
        // These represent a running session rather than a discrete event. They
        // have a start and an end and are continuous, which is what the island
        // is for, so gating them by app would be wrong.
        val ongoing = listOf(
            IslandMode.Music,
            IslandMode.Timer,
            IslandMode.Stopwatch,
            IslandMode.Navigation,
            IslandMode.ScreenRecording
        )
        for (mode in ongoing) {
            assertTrue(
                "$mode should not be gated by the app allowlist",
                isLiveActivitySource("com.unknown.app", mode, settings(packages = emptySet()))
            )
        }
    }

    @Test
    fun `discrete event modes from unlisted apps are rejected`() {
        val discrete = listOf(
            IslandMode.Notification,
            IslandMode.LiveActivity,
            IslandMode.DownloadUpload,
            IslandMode.Hotspot,
            IslandMode.Bluetooth,
            IslandMode.Flashlight
        )
        for (mode in discrete) {
            assertFalse(
                "$mode from an unlisted app should stay a normal notification",
                isLiveActivitySource("com.unknown.app", mode, settings(packages = emptySet()))
            )
        }
    }

    @Test
    fun `the default allowlist contains spotify and dodo pizza`() {
        val defaults = SmartIslandSettings()
        assertTrue(defaults.liveActivityAppsOnly)
        assertTrue(
            "Spotify must be a default Live Activity source",
            defaults.liveActivityPackages.contains("com.spotify.music")
        )
        assertTrue(
            "Dodo Pizza must be a default Live Activity source",
            defaults.liveActivityPackages.any { it.contains("dodo", ignoreCase = true) }
        )
    }
}

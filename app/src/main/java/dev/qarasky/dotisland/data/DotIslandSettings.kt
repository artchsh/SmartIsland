/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.data

/** A069 portrait profile. Only power is user-configurable and persisted. */
data class DotIslandSettings(
    val enabled: Boolean = false,
    val width: Float = 112f,
    val height: Float = 34f,
    val xOffset: Float = 0f,
    val yOffset: Float = 12f,
    val cornerRadius: Float = 22f,
    val enableNotchMode: Boolean = false,
    val circlePosition: String = CIRCLE_POSITION_RIGHT
) {
    val opacity = 1f
    val pillColor = 0xFF000000L
    val callColor = 0xFFFFFFFFL
    val musicVisualizerColor = 0xFFFFFFFFL
    val liveActivityColor = 0xFFFFFFFFL
    val showOnLockScreen = false
    val showInLandscape = false
    val lockScreenPrivacy = LOCK_SCREEN_APP_ICON_ONLY
    val hideWhenIdle = false
    val autoHidePill = false
    val autoHideTimeoutSeconds = 5
    val matchDisplayCorners = true
    val autoExpandOnNotification = false
    val enableShadow = false
    val shadowElevation = 0f
    val enableSwipeActions = true
    val enablePillSwipeActions = true
    val swipeUpAction = "DismissCurrent"
    val swipeHoldUpAction = "DismissAll"
    val swipeDownAction = "None"
    val pillSwipeUpAction = "DismissCurrent"
    val pillSwipeDownAction = "Expand"
    val pillSwipeLeftAction = "PreviousNotification"
    val pillSwipeRightAction = "NextNotification"

    companion object {
        val Default = DotIslandSettings()
        const val CIRCLE_POSITION_LEFT = "left"
        const val CIRCLE_POSITION_RIGHT = "right"
        const val LOCK_SCREEN_APP_ICON_ONLY = "AppIconOnly"

        /** Broadcast target for the public API; mirrors applicationId. */
        const val PACKAGE_NAME = "dev.qarasky.dotisland"
    }
}

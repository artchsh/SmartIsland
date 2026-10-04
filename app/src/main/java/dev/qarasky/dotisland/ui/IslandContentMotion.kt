/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification

internal fun expandedContentFadeSpec(expanding: Boolean): TweenSpec<Float> =
    if (expanding) tween(200, delayMillis = 70, easing = LinearOutSlowInEasing)
    else tween(100, easing = LinearEasing)

internal data class CompactContentState(val notification: IslandNotification?, val showIdle: Boolean) {
    // Metadata/position updates must not repeatedly restart the appearance fade.
    val identity: String get() = notification?.let { "activity:${it.key}" }
        ?: if (showIdle) "idle" else "suppressed"
}

@Composable
internal fun CompactActivityContent(
    notification: IslandNotification?,
    showIdleIndicator: Boolean,
    collapsedAlphaProvider: () -> Float,
    settings: DotIslandSettings
) {
    AnimatedContent(
        targetState = CompactContentState(notification, showIdleIndicator),
        contentKey = { it.identity },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // Pure opacity: retain fixed pill geometry and the outgoing snapshot.
            (fadeIn(tween(180, easing = LinearOutSlowInEasing)) togetherWith
                fadeOut(tween(100, easing = LinearEasing))).using(null)
        },
        label = "compactActivityAppearance"
    ) { state ->
        IslandCollapsedContent(
            mode = state.notification?.mode ?: IslandMode.Empty,
            notification = state.notification,
            collapsedAlpha = 1f,
            collapsedAlphaProvider = collapsedAlphaProvider,
            showIdleIndicator = state.showIdle,
            settings = settings
        )
    }
}

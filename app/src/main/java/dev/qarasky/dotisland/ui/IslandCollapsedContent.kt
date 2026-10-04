/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.util.LiveActivityParser
import dev.qarasky.dotisland.ui.expanded.publishedMicroStatus
import kotlinx.coroutines.delay

@Composable
fun IslandCollapsedContent(mode: IslandMode, notification: IslandNotification?, collapsedAlpha: Float,
    settings: DotIslandSettings, modifier: Modifier = Modifier, collapsedAlphaProvider: (() -> Float)? = null,
    showIdleIndicator: Boolean = false) {
    val motion = with(LocalDensity.current) { 8.dp.toPx() }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).graphicsLayer {
            translationX = (1f - (collapsedAlphaProvider?.invoke() ?: collapsedAlpha)) * motion
        }) {
            when (mode) {
                IslandMode.Empty -> if (showIdleIndicator) StandbyDots(Modifier.size(12.dp), animate = true)
                else -> ActivityGlyph(notification, Modifier.size(22.dp))
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).graphicsLayer {
            translationX = -(1f - (collapsedAlphaProvider?.invoke() ?: collapsedAlpha)) * motion
        }) {
            when (mode) {
                IslandMode.Empty -> if (showIdleIndicator) MicroStatus("IDLE")
                IslandMode.Music -> DotMatrixActivity(notification?.mediaIsPlaying == true, Modifier.size(19.dp, 17.dp))
                IslandMode.IncomingCall -> if (notification?.isCallRinging == true) MicroStatus("CALL")
                    else CallTimer(notification?.timeMillis ?: System.currentTimeMillis(), Color.White)
                IslandMode.LiveActivity -> MicroStatus(LiveActivityParser.eta(notification?.title.orEmpty(), notification?.text.orEmpty()) ?: "ORDER")
                IslandMode.Published -> MicroStatus(publishedMicroStatus(notification))
            }
        }
    }
}

@Composable internal fun ActivityGlyph(notification: IslandNotification?, modifier: Modifier = Modifier) {
    val artwork = notification?.largeIcon ?: notification?.icon
    if (artwork != null) Image(artwork.asImageBitmap(), null, modifier.clip(SquircleShape))
    else Icon(when (notification?.mode) {
        IslandMode.IncomingCall -> Icons.Rounded.Call
        IslandMode.LiveActivity -> Icons.Rounded.LocalPizza
        IslandMode.Published -> Icons.Rounded.Bolt
        else -> Icons.Rounded.MusicNote
    }, null, modifier, tint = Color.White)
}

@Composable private fun MicroStatus(text: String) {
    Text(text, color = Color(0xFFB8B8BD), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
}

@Composable internal fun CallTimer(postTimeMillis: Long, color: Color) {
    var elapsed by remember(postTimeMillis) { mutableStateOf(0L) }
    LaunchedEffect(postTimeMillis) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - postTimeMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Text("%02d:%02d".format(elapsed / 60, elapsed % 60), color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

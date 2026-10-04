/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui.expanded

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.MediaRouter2
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qarasky.dotisland.data.DotIslandCommand
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.di.DotIslandRepositories
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.WavyMusicSeekBar
import dev.qarasky.dotisland.ui.bounceClick
import dev.qarasky.dotisland.ui.SquircleShape
import dev.qarasky.dotisland.ui.DotMatrixActivity
import androidx.compose.ui.text.font.FontFamily
import dev.qarasky.dotisland.util.runCatchingLogged
import dev.qarasky.dotisland.util.calculateCutoutAvoidingTop
import kotlin.math.ceil

/** Black, compact media surface; camera padding is owned by the expanded container. */
@Composable
fun MusicExpanded(
    notification: IslandNotification?,
    settings: DotIslandSettings = DotIslandSettings.Default,
    onCollapse: () -> Unit = {},
    cameraBounds: List<Rect> = emptyList(),
    cardScreenTop: Dp = 12.dp
) {
    val context = LocalContext.current
    val repository = remember(context) { DotIslandRepositories.notificationRepository(context) }
    val controller = remember(notification?.mediaToken) {
        notification?.mediaToken?.let { token ->
            runCatchingLogged("MusicExpanded", "Failed to create MediaController") {
                MediaController(context, token)
            }
        }
    }
    var playbackState by remember(controller) { mutableStateOf(controller?.playbackState) }
    var metadata by remember(controller) { mutableStateOf(controller?.metadata) }
    var isPlaying by remember(notification?.key, notification?.mediaIsPlaying) {
        mutableStateOf(notification?.mediaIsPlaying == true)
    }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
                isPlaying = state?.state == PlaybackState.STATE_PLAYING
            }
            override fun onMetadataChanged(value: MediaMetadata?) {
                metadata = value
            }
        }
        controller.registerCallback(callback)
        playbackState = controller.playbackState
        metadata = controller.metadata
        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        onDispose { controller.unregisterCallback(callback) }
    }

    val metadataArtwork = remember(metadata) {
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }
    val artwork = notification?.largeIcon ?: metadataArtwork ?: notification?.icon
    val title = notification?.title?.takeIf { it.isNotBlank() } ?: "Song"
    val artist = notification?.text?.takeIf { it.isNotBlank() } ?: notification?.appName ?: "Artist"
    val durationMs = notification?.mediaDurationMs?.takeIf { it > 0 }
        ?: metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }

    Column(
        modifier = Modifier.fillMaxWidth()
            // No full-card artwork tint: the reference surface is solid black.
            .background(Color.Black)
            .padding(MUSIC_CONTENT_INSET)
    ) {
        MusicHeader(
            modifier = Modifier.fillMaxWidth(),
            cameraBounds = cameraBounds,
            screenWidthPx = context.resources.displayMetrics.widthPixels,
            screenTop = cardScreenTop + MUSIC_CONTENT_INSET
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(SquircleShape)
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(64.dp).clip(SquircleShape)
                        .background(Color(0xFF242425)).padding(16.dp)
                )
            }
            Column {
                Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = MEDIA_SECONDARY, fontSize = 16.sp, lineHeight = 21.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DotMatrixActivity(isPlaying, Modifier.size(width = 26.dp, height = 32.dp))
        }
        Spacer(Modifier.height(4.dp))
        // Polling is isolated here; playback progress does not recompose artwork/controls.
        MusicTimeline(notification, playbackState, isPlaying, durationMs) { position ->
            repository.resetTimer()
            if (controller != null) {
                runCatchingLogged("MusicExpanded", "Failed to seek") {
                    controller.transportControls.seekTo(position)
                }
            } else {
                notification?.packageName?.let { repository.sendCommand(DotIslandCommand.SeekTo(it, position)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaButton(Icons.Rounded.FastRewind, "Previous track", 36.dp) {
                    repository.resetTimer()
                    if (controller != null) {
                        runCatchingLogged("MusicExpanded", "Failed to skip previous") {
                            controller.transportControls.skipToPrevious()
                        }
                    } else notification.sendFirstAction(context, "previous", "prev", "rewind")
                }
                MediaButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play", 40.dp) {
                    repository.resetTimer()
                    val play = !isPlaying
                    isPlaying = play
                    if (controller != null) {
                        runCatchingLogged("MusicExpanded", "Failed to play/pause") {
                            if (play) controller.transportControls.play() else controller.transportControls.pause()
                        }
                    } else notification.sendFirstAction(context, "play", "pause", "resume")
                }
                MediaButton(Icons.Rounded.FastForward, "Next track", 36.dp) {
                    repository.resetTimer()
                    if (controller != null) {
                        runCatchingLogged("MusicExpanded", "Failed to skip next") {
                            controller.transportControls.skipToNext()
                        }
                    } else notification.sendFirstAction(context, "next", "skip", "forward")
                }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                MediaButton(Icons.Rounded.Headphones, "Audio output", 24.dp, MEDIA_SECONDARY) {
                    repository.resetTimer()
                    onCollapse()
                    openAudioOutput(context)
                }
            }
        }
    }
}

/** Position side artwork normally; move text only where it intersects the camera. */
@Composable
private fun MusicHeader(
    modifier: Modifier,
    cameraBounds: List<Rect>,
    screenWidthPx: Int,
    screenTop: Dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 18.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val art = measurables[0].measure(loose)
        val bars = measurables[2].measure(loose)
        val textWidth = (constraints.maxWidth - art.width - bars.width - gap * 2).coerceAtLeast(0)
        val text = measurables[1].measure(loose.copy(minWidth = textWidth, maxWidth = textWidth))
        val naturalHeight = maxOf(art.height, text.height, bars.height)
        val headerScreenLeft = (screenWidthPx - constraints.maxWidth) / 2f
        val headerScreenTop = screenTop.toPx()
        val textX = art.width + gap
        val barsX = constraints.maxWidth - bars.width
        fun topFor(x: Int, width: Int, height: Int): Int {
            val top = headerScreenTop + (naturalHeight - height) / 2f
            return ceil(calculateCutoutAvoidingTop(
                headerScreenLeft + x, width.toFloat(), top, height.toFloat(), cameraBounds
            ) - headerScreenTop).toInt().coerceAtLeast(0)
        }
        val artTop = topFor(0, art.width, art.height)
        val textTop = topFor(textX, text.width, text.height)
        val barsTop = topFor(barsX, bars.width, bars.height)
        val height = maxOf(artTop + art.height, textTop + text.height, barsTop + bars.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            art.place(0, artTop)
            text.place(textX, textTop)
            bars.place(barsX, barsTop)
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(Modifier.size(48.dp).bounceClick(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun MusicTimeline(
    notification: IslandNotification?,
    playbackState: PlaybackState?,
    isPlaying: Boolean,
    durationMs: Long?,
    onSeek: (Long) -> Unit
) {
    var positionMs by remember(notification?.key, notification?.title) {
        mutableStateOf(notification?.mediaPositionMs ?: playbackState?.position ?: 0L)
    }
    var lastSeekElapsed by remember(notification?.key, notification?.title) { mutableStateOf(0L) }
    LaunchedEffect(playbackState, isPlaying, notification?.mediaPositionMs) {
        do {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSeekElapsed > SEEK_SETTLE_MS) {
                val base = playbackState?.position ?: notification?.mediaPositionMs ?: 0L
                val elapsed = if (isPlaying && playbackState?.state == PlaybackState.STATE_PLAYING) {
                    ((now - playbackState.lastPositionUpdateTime).coerceAtLeast(0) * playbackState.playbackSpeed).toLong()
                } else 0L
                positionMs = (base + elapsed).coerceAtLeast(0L)
            }
            if (isPlaying) kotlinx.coroutines.delay(POSITION_POLL_INTERVAL_MS)
        } while (isPlaying)
    }
    val progress = if (durationMs != null) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else if (notification != null && notification.progressMax > 0)
            (notification.progress.toFloat() / notification.progressMax).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(positionMs), color = MEDIA_SECONDARY, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        WavyMusicSeekBar(progress = progress, isPlaying = isPlaying, modifier = Modifier.weight(1f),
            progressLabel = "${notification?.title ?: "Music"} playback position",
            onSeek = { fraction ->
                if (durationMs != null) {
                    positionMs = (fraction * durationMs).toLong()
                    lastSeekElapsed = SystemClock.elapsedRealtime()
                    onSeek(positionMs)
                }
            })
        Text(formatRemainingDuration(positionMs, durationMs), color = MEDIA_SECONDARY, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

internal fun formatRemainingDuration(positionMs: Long, durationMs: Long?): String =
    durationMs?.takeIf { it > 0 }?.let { "−${formatDuration((it - positionMs).coerceAtLeast(0))}" } ?: "--:--"

private fun openAudioOutput(context: Context) {
    val shown = Build.VERSION.SDK_INT >= 34 && runCatching {
        MediaRouter2.getInstance(context).showSystemOutputSwitcher()
    }.getOrDefault(false)
    if (!shown) {
        // Service overlays may be classified as background by the system switcher.
        runCatchingLogged("MusicExpanded", "Failed to open audio output settings") {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private val MEDIA_SECONDARY = Color(0xFF8E8E93)
private val MUSIC_CONTENT_INSET = 20.dp
private const val POSITION_POLL_INTERVAL_MS = 100L
private const val SEEK_SETTLE_MS = 1500L

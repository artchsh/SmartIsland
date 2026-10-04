/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui.expanded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.ActivityGlyph

/**
 * A third-party activity. Kept deliberately plain: the publisher owns the wording,
 * so the card only frames it and never embellishes it with a guessed stage, ETA or
 * brand colour.
 */
@Composable
fun PublishedExpanded(notification: IslandNotification, bottomPadding: Dp) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivityGlyph(notification, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.appName.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF8E8E93),
                    maxLines = 1
                )
                Text(
                    text = notification.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2
                )
            }
        }
        if (notification.text.isNotBlank()) {
            Text(notification.text, fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFFB8B8BD), maxLines = 3)
        }
        if (notification.progressMax > 0) {
            LinearProgressIndicator(
                progress = { notification.progress.toFloat() / notification.progressMax },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = Color.White,
                trackColor = Color(0xFF242425),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Text(
                text = "${notification.progress}%",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

/** Compact trailing slot: a publisher's own percentage, else a neutral label. */
internal fun publishedMicroStatus(notification: IslandNotification?): String =
    if (notification != null && notification.progressMax > 0) "${notification.progress}%" else "API"
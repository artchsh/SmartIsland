/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui.expanded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.ActivityGlyph
import dev.qarasky.dotisland.util.LiveActivityParser

@Composable
fun LiveActivityExpanded(notification: IslandNotification, bottomPadding: Dp) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActivityGlyph(notification, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text("DODO / ORDER", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF8E8E93))
                Text(notification.title.ifBlank { "Order update" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (notification.text.isNotBlank()) Text(notification.text, fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFFB8B8BD))
        LiveActivityParser.eta(notification.title, notification.text)?.let {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        LiveActivityParser.progress(notification.progress, notification.progressMax)?.let { progress ->
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                color = Color.White, trackColor = Color(0xFF242425))
        }
    }
}

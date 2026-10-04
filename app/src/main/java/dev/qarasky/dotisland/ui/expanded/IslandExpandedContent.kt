/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui.expanded

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.presentationFor

@Composable
fun IslandExpandedContent(notifications: List<IslandNotification>, selectedIndex: Int,
    onPageSelected: (Int) -> Unit, onOpenNotification: (IslandNotification) -> Unit,
    onCollapse: () -> Unit, statusBarHeight: Dp, onHeightMeasured: (Dp) -> Unit,
    settings: DotIslandSettings, cameraBounds: List<Rect>, cardScreenTop: Dp,
    modifier: Modifier = Modifier, onReplyStateChanged: (Boolean) -> Unit = {}) {
    if (notifications.isEmpty()) return
    val density = LocalDensity.current
    var heights by remember { mutableStateOf(emptyMap<String, Dp>()) }
    val keys = notifications.map { it.key }.toSet()
    LaunchedEffect(keys) { heights = heights.filterKeys { it in keys } }
    val pager = rememberPagerState(selectedIndex.coerceIn(0, notifications.lastIndex)) { notifications.size }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in notifications.indices && !pager.isScrollInProgress && pager.currentPage != selectedIndex)
            pager.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage in notifications.indices) onPageSelected(pager.settledPage) }
    fun pageHeight(index: Int): Dp {
        val item = notifications.getOrNull(index)
        val presentation = presentationFor(item?.mode)
        return heights[item?.key]?.let(presentation::clampHeight) ?: presentation.estimatedHeight
    }
    val current = pager.currentPage.coerceIn(0, notifications.lastIndex)
    val fraction = pager.currentPageOffsetFraction
    val next = (current + if (fraction >= 0) 1 else -1).coerceIn(0, notifications.lastIndex)
    val bodyHeight = pageHeight(current) + (pageHeight(next) - pageHeight(current)) * kotlin.math.abs(fraction)
    LaunchedEffect(bodyHeight) { onHeightMeasured(bodyHeight) }
    HorizontalPager(pager, modifier.fillMaxWidth().height(bodyHeight)) { page ->
        notifications.getOrNull(page)?.let { item ->
            Box(Modifier.fillMaxWidth().height(bodyHeight).verticalScroll(rememberScrollState())
                .clickable(remember { MutableInteractionSource() }, indication = null) { onOpenNotification(item) }) {
                Box(Modifier.fillMaxWidth().wrapContentHeight().onSizeChanged {
                    val measured = with(density) { it.height.toDp() }
                    if (heights[item.key] != measured) heights = heights + (item.key to measured)
                }) {
                    when (item.mode) {
                        IslandMode.Music -> MusicExpanded(item, settings, onCollapse, cameraBounds, cardScreenTop)
                        IslandMode.IncomingCall -> IncomingCallExpanded(item, 20.dp, onCollapse, settings)
                        IslandMode.LiveActivity -> LiveActivityExpanded(item, 20.dp)
                        IslandMode.Published -> PublishedExpanded(item, 20.dp)
                        IslandMode.Empty -> Unit
                    }
                }
            }
        }
    }
}

/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qarasky.dotisland.model.IslandNotification

@Composable
fun ThemedOverlayIsland(viewModel: IslandViewModel, statusBarHeight: Float,
    onOpenNotification: (IslandNotification) -> Unit, onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {}, isFullWidth: Boolean = true,
    onCollapseAnimationFinished: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val expanded by viewModel.expanded.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val notifications by viewModel.visibleNotifications.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val hasSourceActivity by viewModel.hasSourceActivity.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val index by viewModel.selectedIndex.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    DotIslandTheme {
        IslandOverlayView(settings, expanded && isFullWidth, notifications, index,
            onPageSelected = viewModel::setSelectedNotificationIndex, onOpenNotification = onOpenNotification,
            onToggleExpanded = viewModel::toggleExpanded, onDismissNotification = viewModel::dismissCurrentNotification,
            onDismissAllNotifications = viewModel::dismissAllNotifications, onOpenFloatingWindow = onOpenFloatingWindow,
            onOpenNotificationShade = onOpenNotificationShade, statusBarHeight = statusBarHeight,
            isFullWidth = isFullWidth, onCollapseAnimationFinished = onCollapseAnimationFinished,
            showIdleIndicator = !hasSourceActivity)
    }
}

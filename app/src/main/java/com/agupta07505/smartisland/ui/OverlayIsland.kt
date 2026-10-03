/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agupta07505.smartisland.data.AppShortcutProvider
import com.agupta07505.smartisland.data.LaunchableApp
import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.model.IslandNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shown in place of notification text while lock screen redaction is active. */
private const val REDACTED_PLACEHOLDER = "Contents hidden"

/**
 * Root composable for the floating overlay window.
 *
 * The overlay is always dark: it is a black pill floating over arbitrary app
 * content, so it does not follow the system light/dark setting. That is why
 * this wraps [SmartIslandTheme] with `darkTheme = true` rather than inheriting
 * `isSystemInDarkTheme()`.
 *
 * Previously nothing installed a theme here at all, so the single
 * `MaterialTheme.colorScheme.primary` reference in the overlay
 * (NotificationExpanded's "Send Reply" button) resolved to the M3 baseline
 * *light* scheme and rendered Material purple in a burnt-orange app.
 */
@Composable
fun OverlayIsland(
    viewModel: IslandViewModel,
    statusBarHeight: Float,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = true
) {
    // collectAsStateWithLifecycle(RESUMED), not collectAsState.
    //
    // SmartIslandOverlayService pauses overlayOwners on ACTION_SCREEN_OFF and
    // resumes them on ACTION_SCREEN_ON, which moves the lifecycle RESUMED ->
    // STARTED. Plain collectAsState keeps the composition live for the whole
    // window lifetime, so the overlay kept collecting flows with the screen
    // off.
    //
    // RESUMED is required rather than the default STARTED: pausing moves the
    // registry to STARTED, not below it, so the default threshold would keep
    // collecting and this change would do nothing.
    //
    // `minActiveState =` is named deliberately. There are two overloads: the
    // StateFlow one takes minActiveState first, but the plain Flow one takes
    // `initialValue` first. Positionally passing Lifecycle.State selects the
    // Flow overload and infers T as `Boolean & Lifecycle.State`.
    //
    // Because all of these are StateFlows, suspending collection loses nothing:
    // the latest value is replayed on resume.
    val settings by viewModel.settings.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val expanded by viewModel.expanded.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val notifications by viewModel.visibleNotifications.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val selectedIndex by viewModel.selectedIndex.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val isInputActive by viewModel.isInputActive.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val context = LocalContext.current

    val isContentRedacted =
        isLocked && settings.lockScreenPrivacy == SmartIslandSettings.LOCK_SCREEN_APP_ICON_ONLY

    // Redact EVERY mode, not just IslandMode.Notification.
    //
    // This previously filtered only Notification, so on the lock screen the
    // titles and bodies of LiveActivity (courier name, order contents),
    // DownloadUpload (file names), Navigation (street addresses), IncomingCall
    // (caller) and Timer (alarm label) were all still fully readable while the
    // user had explicitly chosen "App icon only".
    val processedNotifications = remember(notifications, isContentRedacted) {
        if (!isContentRedacted) {
            notifications
        } else {
            notifications.map { notif ->
                notif.copy(
                    title = notif.appName.ifBlank { notif.title },
                    text = REDACTED_PLACEHOLDER,
                    actionIntents = emptyList(),
                    appName = notif.appName.ifBlank { notif.title }
                )
            }
        }
    }

    val selectedApps = remember(settings.shortcutPackages, settings.enableAppShortcuts) {
        if (settings.enableAppShortcuts) {
            AppShortcutProvider.selectedApps(context, settings.shortcutPackages)
        } else {
            emptyList()
        }
    }
    val launcherApps by produceState<List<LaunchableApp>?>(
        initialValue = when {
            !settings.enableAppShortcuts -> emptyList()
            selectedApps.isNotEmpty() -> selectedApps
            settings.shortcutPackages.isEmpty() && !settings.showRecentApps -> emptyList()
            !AppShortcutProvider.hasUsageAccess(context) -> emptyList()
            else -> null
        },
        settings.enableAppShortcuts,
        settings.shortcutPackages,
        settings.showRecentApps
    ) {
        value = if (!settings.enableAppShortcuts) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                AppShortcutProvider.shortcuts(
                    context = context,
                    selectedPackages = settings.shortcutPackages,
                    includeRecent = settings.showRecentApps
                )
            }
        }
    }

    IslandOverlayView(
        settings = settings,
        expanded = expanded,
        notifications = processedNotifications,
        selectedIndex = selectedIndex,
        launcherApps = launcherApps,
        onPageSelected = { index -> viewModel.setSelectedNotificationIndex(index) },
        onOpenNotification = onOpenNotification,
        onLaunchApp = onLaunchApp,
        onToggleExpanded = { viewModel.toggleExpanded() },
        onDismissNotification = { viewModel.dismissCurrentNotification() },
        onDismissAllNotifications = { viewModel.dismissAllNotifications() },
        onOpenFloatingWindow = onOpenFloatingWindow,
        onOpenNotificationShade = onOpenNotificationShade,
        statusBarHeight = statusBarHeight,
        isInputActive = isInputActive,
        onReplyStateChanged = { viewModel.setInputActive(it) },
        isFullWidth = isFullWidth,
        modifier = modifier
    )
}

/**
 * Convenience wrapper used by [com.agupta07505.smartisland.service.SmartIslandOverlayService]
 * so the overlay always renders inside the app theme.
 */
@Composable
fun ThemedOverlayIsland(
    viewModel: IslandViewModel,
    statusBarHeight: Float,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    isFullWidth: Boolean = true
) {
    SmartIslandTheme(darkTheme = true) {
        OverlayIsland(
            viewModel = viewModel,
            statusBarHeight = statusBarHeight,
            onOpenNotification = onOpenNotification,
            onLaunchApp = onLaunchApp,
            onOpenFloatingWindow = onOpenFloatingWindow,
            onOpenNotificationShade = onOpenNotificationShade,
            isFullWidth = isFullWidth
        )
    }
}

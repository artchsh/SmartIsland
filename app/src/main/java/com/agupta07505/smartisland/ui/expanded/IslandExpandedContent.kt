/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agupta07505.smartisland.ui.expanded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agupta07505.smartisland.model.IslandMode
import com.agupta07505.smartisland.model.IslandNotification


import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.data.LaunchableApp
import com.agupta07505.smartisland.ui.accentColorFor
import com.agupta07505.smartisland.ui.presentationFor
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.agupta07505.smartisland.R

@Composable
fun IslandExpandedContent(
    notifications: List<IslandNotification>,
    launcherApps: List<LaunchableApp>?,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onCollapse: () -> Unit,
    statusBarHeight: Dp,
    onHeightMeasured: (Dp) -> Unit,
    settings: SmartIslandSettings,
    modifier: Modifier = Modifier,
    onReplyStateChanged: (Boolean) -> Unit = {}
) {
    if (notifications.isEmpty()) {
        if (!settings.enableAppShortcuts) {
            return
        }
        val density = LocalDensity.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true)
                .onSizeChanged {
                    val measuredHeight = with(density) { it.height.toDp() }
                    if (measuredHeight > 0.dp) {
                        val clamped = measuredHeight.coerceIn(80.dp, 180.dp)
                        onHeightMeasured(clamped)
                    }
                }
        ) {
            if (settings.enableNotificationBackdrop) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(settings.notificationDotColor).copy(alpha = 0.22f),
                                    Color(settings.notificationDotColor).copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = Offset(0f, 0f),
                                radius = 700f
                            )
                        )
                )
            }
            EmptyExpanded(settings = settings, apps = launcherApps, onLaunchApp = onLaunchApp)
        }
        return
    }

    val density = LocalDensity.current
    var pageHeights by remember { mutableStateOf(emptyMap<String, Dp>()) }

    // Clean up stale keys not present in notifications
    val activeKeys = remember(notifications) { notifications.map { it.key }.toSet() }
    LaunchedEffect(activeKeys) {
        pageHeights = pageHeights.filterKeys { it in activeKeys }
    }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, notifications.lastIndex),
        pageCount = { notifications.size }
    )

    // Sync external selectedIndex updates ONLY when not currently user-scrolling,
    // preventing gesture interrupts mid-swipe that cause half-page frozen states.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in notifications.indices &&
            !pagerState.isScrollInProgress &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // Sync settled page updates back to caller ONLY when scroll has settled
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage in notifications.indices) {
            onPageSelected(pagerState.settledPage)
        }
    }

    // Safety net: Ensure pager never stays stuck at a non-zero offset fraction when scroll finishes
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction != 0f) {
            val targetPage = pagerState.settledPage.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
            if (targetPage in notifications.indices) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    val bottomPadding = 16.dp

    Column(modifier = modifier.fillMaxWidth().wrapContentHeight()) {

        // Interpolate height between pages based on swipe progress
        // FIX: Clamp battery/call heights to match music/notification style to avoid
        // glitch where battery/call expand more (taller) than other modes.
        val currentPage = pagerState.currentPage
        val offsetFraction = pagerState.currentPageOffsetFraction
        val currentNotification = notifications.getOrNull(currentPage)

        /**
         * Clamps a measured page height into the HIG's expanded range.
         *
         * This previously had per-mode ranges that both exceeded the HIG ceiling
         * (LiveActivity was allowed up to 205dp) and disagreed with
         * `defaultEstimatedHeightForMode`, which estimated LiveActivity at 180dp
         * while the clamp permitted 140-205dp — so the estimate could never be a
         * meaningful fallback. One range now comes from the presentation registry.
         */
        fun clampHeightForMode(notif: IslandNotification?, height: Dp): Dp =
            presentationFor(notif?.mode).clampHeight(height)

        val currentPageHeightRaw = currentNotification?.let { pageHeights[it.key] }
        val currentPageHeight = currentPageHeightRaw?.let { clampHeightForMode(currentNotification, it) }
            ?: com.agupta07505.smartisland.ui.defaultEstimatedHeightForMode(currentNotification?.mode)

        val targetHeight = run {
            val nextPage = if (offsetFraction > 0f) {
                (currentPage + 1).coerceAtMost(notifications.lastIndex)
            } else if (offsetFraction < 0f) {
                (currentPage - 1).coerceAtLeast(0)
            } else {
                currentPage
            }
            val nextNotification = notifications.getOrNull(nextPage)
            val nextHeightRaw = nextNotification?.let { pageHeights[it.key] }
            val nextHeight = (nextHeightRaw?.let { clampHeightForMode(nextNotification, it) }
                ?: com.agupta07505.smartisland.ui.defaultEstimatedHeightForMode(nextNotification?.mode))
            val fraction = kotlin.math.abs(offsetFraction)
            // Interpolating between two already-clamped heights can never leave
            // the HIG range, so no further coercion is needed here. The previous
            // `.coerceIn(72.dp, 205.dp)` was the last remaining place the old,
            // HIG-violating ceiling survived.
            currentPageHeight + (nextHeight - currentPageHeight) * fraction
        }

        LaunchedEffect(targetHeight) {
            onHeightMeasured(targetHeight)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(presentationFor(currentNotification?.mode).clampHeight(targetHeight))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    // unbounded = true: pages measure at natural height even when parent Box has explicit height
                    .wrapContentHeight(unbounded = true)
            ) { page ->
                val notification = notifications.getOrNull(page)
                if (notification != null) {
                    val pageScroll = rememberScrollState()
                    // Natural height of this page's content, measured on an inner
                    // element that is NOT height-constrained.
                    //
                    // The outer Box is capped to the HIG's 84-160dp range and made
                    // scrollable; the inner Box measures the content at its natural
                    // size so the measurement is not corrupted by the cap. Measuring
                    // on the outer element instead would feed the capped viewport
                    // height back into the measurement and create a layout loop.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(presentationFor(notification.mode).clampHeight(targetHeight))
                            .verticalScroll(pageScroll)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onOpenNotification(notification)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onSizeChanged { size ->
                                    val heightDp = with(density) { size.height.toDp() }
                                    if (pageHeights[notification.key] != heightDp) {
                                        pageHeights = pageHeights.toMutableMap().apply {
                                            put(notification.key, heightDp)
                                        }
                                    }
                                }
                        ) {
                            if (settings.enableNotificationBackdrop && notification.mode != IslandMode.Music) {
                                NotificationBackdrop(
                                    notification = notification,
                                    settings = settings,
                                    modifier = Modifier.matchParentSize()
                                )
                            }

                        when (notification.mode) {
                            IslandMode.Notification -> NotificationExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                showActions = settings.showNotificationActions,
                                settings = settings,
                                onReplyStateChanged = onReplyStateChanged
                            )
                            IslandMode.IncomingCall -> IncomingCallExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Music -> MusicExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                settings = settings
                            )
                            IslandMode.LiveActivity -> LiveActivityExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Navigation -> NavigationExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.DownloadUpload -> DownloadExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Hotspot -> HotspotExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Bluetooth -> BluetoothExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Flashlight -> FlashlightExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.ScreenRecording -> ScreenRecordingExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Timer -> TimerExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Stopwatch -> StopwatchExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Empty -> EmptyExpanded(
                                settings = settings,
                                apps = launcherApps,
                                onLaunchApp = onLaunchApp
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExpanded(
    settings: SmartIslandSettings,
    apps: List<LaunchableApp>?,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true)
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val loadedApps = apps
        val hasConfiguration = settings.shortcutPackages.isNotEmpty() || settings.showRecentApps

        if (loadedApps == null) {
            // Keep the configured launcher visually clean while PackageManager and
            // UsageStats are queried. In particular, do not flash the setup state.
            Spacer(Modifier.height(84.dp))
        } else if (loadedApps.isEmpty() && !hasConfiguration) {
            Text(stringResource(R.string.shortcuts_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.shortcuts_desc),
                color = Color(0xFFB7C0CA),
                fontSize = 13.sp
            )
            Text(
                stringResource(R.string.quick_launch_open_settings),
                color = Color(0xFF67E8F9),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable { onLaunchApp(context.packageName) }
            )
        } else if (loadedApps.isEmpty()) {
            Text(
                stringResource(R.string.quick_launch_apps_unavailable),
                color = Color(0xFFB7C0CA),
                fontSize = 13.sp
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                loadedApps.chunked(4).forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowApps.forEach { app ->
                            ShortcutApp(app = app, onClick = { onLaunchApp(app.packageName) })
                        }
                        repeat(4 - rowApps.size) { Box(Modifier.size(width = 64.dp, height = 1.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutApp(app: LaunchableApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                modifier = Modifier.size(44.dp)
            )
        }
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun NotificationBackdrop(
    notification: IslandNotification,
    settings: SmartIslandSettings,
    modifier: Modifier = Modifier
) {
    val artwork = notification.largeIcon ?: notification.icon
    val imageBitmap = remember(artwork) {
        if (artwork != null && !artwork.isRecycled) {
            runCatching { artwork.asImageBitmap() }.getOrNull()
        } else null
    }

    // Single resolver for every mode's accent colour. This was previously a
    // second hand-maintained `when`, which is how the collapsed Navigation slot
    // ended up reading liveActivityColor while the expanded layer read
    // navigationColor. AUDIT.md section 5.12.
    val accentColor = accentColorFor(notification.mode, settings)

    Box(modifier = modifier) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.38f }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                // Restrained for the same reason as the no-artwork
                                // case: the artwork already supplies the colour, so a
                                // heavy accent wash on top only muddies it.
                                accentColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 320f
                        )
                    )
            )
        } else {
            // iOS: the expanded Live Activity card is black. The accent colour
            // appears in the glyphs, progress rings and small highlights — not as
            // a wash across the whole surface.
            //
            // This previously applied accentColor at 35% alpha over a 750px radius
            // from the top-leading corner, which on a 421x~100dp card covered the
            // entire surface. The battery card rendered as a solid green block.
            //
            // The accent is now a restrained corner glow behind the leading glyph,
            // roughly a fifth of the previous strength and a third of the radius,
            // over a near-black base.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF0A0A0C))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.16f),
                                accentColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 260f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }
    }
}


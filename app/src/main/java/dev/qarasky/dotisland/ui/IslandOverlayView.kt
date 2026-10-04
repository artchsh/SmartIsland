/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui

import dev.qarasky.dotisland.data.DotIslandCommand
import dev.qarasky.dotisland.model.SwipeAction
import dev.qarasky.dotisland.ui.expanded.IslandExpandedContent
import dev.qarasky.dotisland.ui.expanded.trySendFirstAction
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.di.DotIslandRepositories
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.util.FALLBACK_DISPLAY_CORNER_RADIUS
import dev.qarasky.dotisland.util.rememberDisplayCornerRadius
import dev.qarasky.dotisland.util.rememberDisplayCutoutSafeTop
import dev.qarasky.dotisland.util.rememberDisplayCameraBounds
import dev.qarasky.dotisland.util.calculateExpandedContentTopPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun IslandOverlayView(
    settings: DotIslandSettings,
    expanded: Boolean,
    notifications: List<IslandNotification>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onToggleExpanded: () -> Unit,
    onDismissNotification: () -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    statusBarHeight: Float,
    modifier: Modifier = Modifier,
    isInputActive: Boolean = false,
    onReplyStateChanged: (Boolean) -> Unit = {},
    onDismissAllNotifications: () -> Unit = {},
    isFullWidth: Boolean = true,
    onCollapseAnimationFinished: () -> Unit = {},
    showIdleIndicator: Boolean = true
) {
    // Fix #1: rememberUpdatedState ensures the lambda is always fresh
    // even though pointerInput(Unit) never restarts its coroutine
    val currentOnToggle by rememberUpdatedState(onToggleExpanded)
    val currentOnDismiss by rememberUpdatedState(onDismissNotification)
    val currentOnDismissAll by rememberUpdatedState(onDismissAllNotifications)
    val currentOnOpenFloatingWindow by rememberUpdatedState(onOpenFloatingWindow)
    val currentOnOpenNotificationShade by rememberUpdatedState(onOpenNotificationShade)
    val currentOnOpenNotification by rememberUpdatedState(onOpenNotification)
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    val currentExpanded by rememberUpdatedState(expanded)
    val currentSettings by rememberUpdatedState(settings)
    val safeIndex = selectedIndex.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
    val currentNotifications by rememberUpdatedState(notifications)
    val currentSelectedIndex by rememberUpdatedState(safeIndex)

    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(0f) }
    var pillDragOffsetX by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    val windowLocation = remember(hostView) { IntArray(2) }
    var windowWidthPx by remember(hostView) { mutableStateOf(hostView.width) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val displayMetrics = context.resources.displayMetrics
    val density = LocalDensity.current
    val screenWidth = with(density) { displayMetrics.widthPixels.toDp() }
    val screenCenter = screenWidth / 2f
    val expandedWidth = calculateExpandedWidth(
        isLandscape = isLandscape,
        screenWidthDp = configuration.screenWidthDp.toFloat(),
        screenHeightDp = configuration.screenHeightDp.toFloat()
    ).dp
    val transition = updateTransition(targetState = expanded, label = "islandTransition")

    // The display's own corner radius, so the island's curvature is continuous
    // with the screen's. Null when the platform does not report one (API < 31 or
    // an OEM that omits it), in which case the setting's manual radius is used.
    val displayCornerRadius = rememberDisplayCornerRadius()

    // The expanded card keeps a slightly tighter radius than the pill. iOS does
    // the same: the compact pill is a near-capsule, the expanded card has visibly
    // flatter corners because it is much wider. Derived from the display radius
    // so it still tracks the device.
    val expandedCardRadius = remember(displayCornerRadius) {
        (displayCornerRadius ?: FALLBACK_DISPLAY_CORNER_RADIUS) * 0.78f
    }

    // Direction-aware motion specs.
    //
    // A single spring was previously used in both directions. Springs are
    // symmetric, so the 0.72 damping ratio that gives a pleasant hint of
    // overshoot on the way IN also produced a visible bounce on the way OUT,
    // which reads as wrong: the Dynamic Island settles rather than rebounds when
    // it collapses.
    //
    // Expansion has a small overshoot; collapse is critically damped.
    // The service waits for actual completion before shrinking the window.
    val sizeExpandSpec = spring<Dp>(dampingRatio = 0.86f, stiffness = 520f)
    val sizeCollapseSpec = spring<Dp>(dampingRatio = 1f, stiffness = 780f)
    val heightExpandSpec = spring<Dp>(dampingRatio = 0.88f, stiffness = 520f)
    val heightCollapseSpec = spring<Dp>(dampingRatio = 1f, stiffness = 780f)
    val floatExpandSpec = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)
    val floatCollapseSpec = spring<Float>(dampingRatio = 1f, stiffness = 780f)
    val alphaSpec = tween<Float>(
        durationMillis = 190,
        easing = FastOutSlowInEasing
    )

    // Cross-fade specs.
    //
    // Both layers previously faded on the same 190ms curve, so at the midpoint
    // both sat at roughly 50% alpha and the swap looked muddy. That is a large
    // part of why the expansion read as two surfaces replacing each other rather
    // than one shape changing its content.
    //
    // The compact content now clears out early and faster than the expanded
    // content arrives, which is how the iOS transition reads: the shape is
    // already the card before the card's content is legible.
    val compactExitSpec = tween<Float>(
        durationMillis = 120,
        easing = FastOutLinearInEasing
    )

    val activeNotification = notifications.getOrNull(safeIndex)
    val activeMode = activeNotification?.mode ?: IslandMode.Empty

    val initialEstimatedHeight = remember(activeMode, notifications.isEmpty()) {
        if (notifications.isEmpty()) 135.dp else defaultEstimatedHeightForMode(activeMode)
    }
    var expandedHeight by remember { mutableStateOf(initialEstimatedHeight) }

    // A measurement that arrived while the transition was running, applied once
    // it settles. See onHeightMeasured below.
    var pendingMeasuredHeight by remember { mutableStateOf<Dp?>(null) }
    val isTransitionSettled = !transition.isRunning &&
        transition.currentState == transition.targetState
    val currentOnCollapseFinished by rememberUpdatedState(onCollapseAnimationFinished)
    LaunchedEffect(expanded, isTransitionSettled) {
        if (!expanded && isTransitionSettled) currentOnCollapseFinished()
    }

    LaunchedEffect(isTransitionSettled, pendingMeasuredHeight) {
        val pending = pendingMeasuredHeight
        if (isTransitionSettled && pending != null && pending != expandedHeight) {
            android.util.Log.d(TAG, "expanded height ${expandedHeight.value} -> ${pending.value} (deferred)")
            expandedHeight = pending
        }
        if (isTransitionSettled) pendingMeasuredHeight = null
    }

    LaunchedEffect(activeMode, notifications.isEmpty()) {
        if (!expanded) {
            expandedHeight = if (notifications.isEmpty()) 135.dp else defaultEstimatedHeightForMode(activeMode)
        }
    }

    val compactGap = COMPACT_INDICATOR_GAP_DP.dp
    val miniPillWidth = settings.width.dp
    val circleSize = settings.height.dp
    val compactShapes = compactNotificationShapes(notifications.size, expanded)
    val hasCompanion = if (settings.enableNotchMode) false else notifications.size >= 2
    val isCircleLeft = settings.circlePosition == DotIslandSettings.CIRCLE_POSITION_LEFT

    // Layout math lives in calculateCollapsedLayout() so it can be unit tested.
    val collapsedGeometry = calculateCollapsedLayout(
        screenWidthDp = screenWidth.value,
        screenCenterDp = screenCenter.value,
        pillWidthDp = settings.width,
        circleSizeDp = settings.height,
        xOffsetDp = settings.xOffset,
        hasCompanion = hasCompanion,
        isCircleLeft = isCircleLeft,
        // Animation targets are always screen-relative. Window resizing only
        // changes the draw-time conversion, never the spring's target.
        isFullWidth = true,
        enableNotchMode = settings.enableNotchMode
    )
    val collapsedMainLeft = collapsedGeometry.mainLeftDp.dp
    val circleLeft = collapsedGeometry.circleLeftDp.dp
    val groupCenter = collapsedGeometry.groupCenterDp.dp
    val collapsedMainOffset = collapsedGeometry.mainOffsetDp.dp
    val circleCenter = circleLeft + circleSize / 2f
    val mainCenter = collapsedMainLeft + settings.width.dp / 2f

    val expandedTopOffset = calculateExpandedTopOffset(
        enableNotchMode = settings.enableNotchMode,
        hasCompanion = hasCompanion,
        statusBarHeightDp = statusBarHeight,
        notchHeightDp = settings.height,
        circleSizeDp = settings.height,
        compactGapDp = COMPACT_INDICATOR_GAP_DP
    ).dp
    val cutoutSafeTop = rememberDisplayCutoutSafeTop(statusBarHeight.dp)
    val cameraBounds = rememberDisplayCameraBounds()
    // Music arranges its artwork and text individually around the actual cutout.
    val expandedContentTopPadding = if (activeMode == IslandMode.Music && cameraBounds.isNotEmpty()) 0.dp else calculateExpandedContentTopPadding(
        expandedTopOffset.value, cutoutSafeTop.value
    ).dp
    val isIdleHiding = settings.hideWhenIdle && notifications.isEmpty()

    var isAutoHidden by remember { mutableStateOf(false) }
    var userInteractionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // Reset auto-hide whenever active notifications change or selection changes
    LaunchedEffect(notifications.map { it.key }, selectedIndex) {
        isAutoHidden = false
        userInteractionTimestamp = System.currentTimeMillis()
    }

    // Auto-hide countdown timer when pill is collapsed and autoHidePill is enabled
    LaunchedEffect(expanded, settings.autoHidePill, settings.autoHideTimeoutSeconds, userInteractionTimestamp) {
        if (expanded || !settings.autoHidePill) {
            isAutoHidden = false
            return@LaunchedEffect
        }
        val timeoutMs = (settings.autoHideTimeoutSeconds.coerceAtLeast(1) * 1000L)
        kotlinx.coroutines.delay(timeoutMs)
        isAutoHidden = true
    }

    val isHiding = isIdleHiding || (settings.autoHidePill && isAutoHidden)
    val pillBackgroundColor = Color(settings.pillColor)

    // `targetState` inside a Transition.Segment is the state being transitioned
    // TO, so it tells us the direction: true = expanding, false = collapsing.
    val width by transition.animateDp(
        transitionSpec = { if (targetState) sizeExpandSpec else sizeCollapseSpec },
        label = "islandWidth"
    ) {
        if (it) expandedWidth else if (isHiding) 0.dp else settings.width.dp
    }
    val height by transition.animateDp(
        transitionSpec = { if (targetState) heightExpandSpec else heightCollapseSpec },
        label = "islandHeight"
    ) {
        // The camera reservation is separate from the usable 84–160dp body.
        if (it) expandedHeight + expandedContentTopPadding else if (isHiding) 0.dp else settings.height.dp
    }
    val yOffset by transition.animateDp(
        transitionSpec = { if (targetState) sizeExpandSpec else sizeCollapseSpec },
        label = "islandYOffset"
    ) {
        if (it) expandedTopOffset else if (settings.enableNotchMode) 0.dp else settings.yOffset.dp
    }
    val radius by transition.animateDp(
        transitionSpec = { if (targetState) sizeExpandSpec else sizeCollapseSpec },
        label = "islandRadius"
    ) {
        when {
            // Collapsed: follow the display's own corner radius so the island's
            // curvature is continuous with the screen's, which is what the HIG
            // means by "its rounded corner shape matches the camera". Falls back
            // to the user's setting only if the platform reports nothing.
            it -> expandedCardRadius
            isHiding -> 0.dp
            settings.matchDisplayCorners && displayCornerRadius != null -> displayCornerRadius
            else -> settings.cornerRadius.dp
        }
    }
    val animatedXOffset by transition.animateDp(
        transitionSpec = { if (targetState) sizeExpandSpec else sizeCollapseSpec },
        label = "islandXOffset"
    ) {
        if (it) 0.dp else collapsedMainOffset
    }

    val collapsedAlpha by transition.animateFloat(
        transitionSpec = { compactExitSpec },
        label = "collapsedAlpha"
    ) {
        if (it || isHiding) 0f else 1f
    }

    val expandedAlpha by transition.animateFloat(
        // Entry waits for room; exit starts immediately, before the text clips.
        transitionSpec = { expandedContentFadeSpec(targetState) },
        label = "expandedAlpha"
    ) {
        if (it) 1f else 0f
    }

    val contentScale by transition.animateFloat(
        transitionSpec = { if (targetState) floatExpandSpec else floatCollapseSpec },
        label = "contentScale"
    ) {
        if (it) 1f else 0.95f
    }

    val contentSlideY by transition.animateDp(
        transitionSpec = { if (targetState) sizeExpandSpec else sizeCollapseSpec },
        label = "contentSlideY"
    ) {
        if (it) 0.dp else (-6).dp
    }

    // Reads happen in measurement/layers, not the root composition on every frame.
    val safeWidth = { width.coerceAtLeast(0.dp) }
    val safeHeight = { height.coerceAtLeast(0.dp) }
    val safeRadius = { radius.coerceAtLeast(0.dp) }
    val showCompactContent by remember { derivedStateOf { collapsedAlpha > 0f } }
    val showExpandedContent by remember(expanded) {
        derivedStateOf { expanded || expandedAlpha > 0.01f }
    }

    // Tactile spring scale bounce animation only when user switches between active notifications
    val switchScaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    var isInitialComposition by remember { mutableStateOf(true) }
    var lastSelectedIndex by remember { mutableStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (isInitialComposition) {
            isInitialComposition = false
            lastSelectedIndex = selectedIndex
            return@LaunchedEffect
        }
        if (lastSelectedIndex != selectedIndex) {
            lastSelectedIndex = selectedIndex
            switchScaleAnim.animateTo(
                targetValue = 0.92f,
                animationSpec = tween(40, easing = FastOutSlowInEasing)
            )
            switchScaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 650f
                )
            )
        }
    }

    // Dual Pill (Multi-Tasking Split Island) Detection:
    // When 2 or more notifications exist (e.g. Music + Notification/Timer/Call), split into Main Pill + Secondary Bubble
    val secondaryNotification = if (!settings.enableNotchMode && notifications.size >= 2) {
        notifications.firstOrNull { it.key != activeNotification?.key }
    } else null
    val secondaryIndex = if (secondaryNotification != null) {
        notifications.indexOfFirst { it.key == secondaryNotification.key }
    } else -1
    val tertiaryNotification = if (!settings.enableNotchMode && notifications.size >= 3) {
        notifications.firstOrNull {
            it.key != activeNotification?.key && it.key != secondaryNotification?.key
        }
    } else null
    val tertiaryIndex = if (tertiaryNotification != null) {
        notifications.indexOfFirst { it.key == tertiaryNotification.key }
    } else -1
    val isSplitMode = if (settings.enableNotchMode) false else secondaryNotification != null
    val secondaryIsPill = compactShapes.singleOrNull() == CompactNotificationShape.MiniPill
    val showTertiaryPill = compactShapes.size == 2 && tertiaryNotification != null

    val secondaryAlpha by animateFloatAsState(
        targetValue = if (isSplitMode && !isHiding) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "secondaryAlpha"
    )
    val secondaryScale by animateFloatAsState(
        targetValue = if (isSplitMode && !isHiding) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "secondaryScale"
    )
    val secondaryBubbleWidth by animateDpAsState(
        targetValue = if (secondaryIsPill) miniPillWidth else circleSize,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "secondaryBubbleWidth"
    )
    val secondaryPillProgress = (miniPillWidth - circleSize).value.let { widthDelta ->
        if (widthDelta == 0f) {
            if (secondaryIsPill) 1f else 0f
        } else {
            ((secondaryBubbleWidth - circleSize).value / widthDelta).coerceIn(0f, 1f)
        }
    }
    val secondaryBubbleCorner by animateDpAsState(
        targetValue = if (secondaryIsPill) settings.cornerRadius.dp else circleSize / 2f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "secondaryBubbleCorner"
    )
    val tertiaryAlpha by animateFloatAsState(
        targetValue = if (showTertiaryPill && !isHiding) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tertiaryAlpha"
    )
    val tertiaryScale by animateFloatAsState(
        targetValue = if (showTertiaryPill && !isHiding) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "tertiaryScale"
    )

    val collapsedSecondaryOffset = circleCenter - screenCenter

    // The companion stays at its collapsed screen position while fading out.
    // Its layer converts that position into the actual window's coordinates.
    val secondaryOffset = collapsedSecondaryOffset

    // Outer Box: Fills the entire WindowManager window bounds (which are padded for easy touch)
    //
    // Tap-outside-to-collapse must NOT fire for taps that land on the card.
    //
    // Previously this was a bare detectTapGestures that collapsed on any tap,
    // while the card's own gesture loop deliberately does not consume its down
    // event (it needs to coexist with the pager). So a single tap on the expanded
    // card ran BOTH handlers: it opened the source app AND collapsed the island.
    // See AUDIT.md section 5.1.
    //
    // The fix is an explicit hit test against the card's rect, rather than
    // relying on event consumption between two competing detectors.
    val cardHitPadding = 8f * displayMetrics.density
    val outerModifier = if (currentExpanded) {
        modifier
            .fillMaxSize()
            .pointerInput(expanded, isInputActive, expandedWidth) {
                detectTapGestures { offset ->
                    val cardLeft = (configuration.screenWidthDp.dp / 2f - expandedWidth / 2f).toPx() - cardHitPadding
                    val cardRight = (configuration.screenWidthDp.dp / 2f + expandedWidth / 2f).toPx() + cardHitPadding
                    val cardTop = yOffset.toPx() - cardHitPadding
                    val cardBottom = (yOffset + safeHeight()).toPx() + cardHitPadding
                    val insideCard = offset.x in cardLeft..cardRight && offset.y in cardTop..cardBottom
                    if (!insideCard) {
                        if (isInputActive) {
                            onReplyStateChanged(false)
                        }
                        currentOnToggle()
                    }
                }
            }
    } else {
        modifier.fillMaxSize()
    }

    Box(
        modifier = outerModifier.onSizeChanged { windowWidthPx = it.width },
        contentAlignment = Alignment.TopCenter
    ) {

        // Invisible touch target over the pill location when hiding, so tapping the area reveals the pill or opens shortcuts
        if (isHiding && !currentExpanded) {
            Box(
                modifier = Modifier
                    .width(settings.width.dp)
                    .height(settings.height.dp)
                    .graphicsLayer {
                        hostView.getLocationOnScreen(windowLocation)
                        translationX = collapsedMainOffset.toPx() + calculateWindowCenterCorrection(
                            displayMetrics.widthPixels, windowWidthPx, windowLocation[0]
                        )
                        translationY = (if (settings.enableNotchMode) 0f else settings.yOffset.dp.toPx()) - windowLocation[1]
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // currentSettings, not settings: this block is keyed on
                            // Unit so it never restarts, and a direct read of the
                            // `settings` parameter captured whichever value was current
                            // at first composition. See AUDIT.md section 5.3.
                            if (currentSettings.autoHidePill && isAutoHidden) {
                                // First tap on auto-hidden pill: awaken and reveal the pill
                                isAutoHidden = false
                                userInteractionTimestamp = System.currentTimeMillis()
                            } else if (currentNotifications.isNotEmpty()) {
                                currentOnToggle()
                            }
                        }
                    }
            )
        }

        val squareTop = settings.enableNotchMode && !expanded
        // Border's outline is computed inside its draw cache and observes radius there.
        val mainShape = remember(squareTop) {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density
                ): androidx.compose.ui.graphics.Outline =
                    islandCornerShape(safeRadius(), squareTop).createOutline(size, layoutDirection, density)
            }
        }

        // Diagnostic: one line per composition reporting what the platform says
        // the display's corner radius is, so a mismatch between the island and
        // the screen curvature can be diagnosed without guessing.
        LaunchedEffect(displayCornerRadius) {
            android.util.Log.d(
                "DotIslandOverlayView",
                "display corner radius = ${displayCornerRadius} (fallback $FALLBACK_DISPLAY_CORNER_RADIUS)"
            )
        }

        // Inner Box: The actual visible pill container, managing the black background shape and size animations
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = safeWidth().roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val h = safeHeight().roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
                    val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(w, h) { child.place(0, 0) }
                }
                .graphicsLayer {
                    hostView.getLocationOnScreen(windowLocation)
                    translationX = animatedXOffset.toPx() + calculateWindowCenterCorrection(
                        displayMetrics.widthPixels, windowWidthPx, windowLocation[0]
                    ) + (if (!currentExpanded) pillDragOffsetX else 0f)
                    translationY = yOffset.toPx() - windowLocation[1] + dragOffset
                    scaleX = switchScaleAnim.value
                    scaleY = switchScaleAnim.value
                    shape = islandCornerShape(safeRadius(), squareTop)
                    clip = true
                    shadowElevation = if (settings.enableShadow && !isHiding) {
                        settings.shadowElevation.dp.toPx() * if (currentExpanded) 1.5f else 1f
                    } else 0f
                }
                .background(pillBackgroundColor.copy(alpha = settings.opacity))
                // Hairline outline. The Dynamic Island draws a subtle neutral
                // border on both the collapsed pill and the expanded card; without
                // it the shape dissolves into a black wallpaper. Sampled value,
                // see ISLAND_BORDER_COLOR.
                .border(ISLAND_BORDER_WIDTH, ISLAND_BORDER_COLOR, mainShape)
                .pointerInput(displayMetrics.density, isInputActive) {
                    // coroutineScope gives the gesture loop a CoroutineScope whose job
                    // is a child of this pointerInput block, so the hold-detection job
                    // below is cancelled when the block is cancelled. AwaitPointerEventScope
                    // is not itself a CoroutineScope, which is why this wrapper is needed.
                    coroutineScope {
                    if (isInputActive) return@coroutineScope
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        userInteractionTimestamp = System.currentTimeMillis()
                        val pressTimeMs = System.currentTimeMillis()
                        val wasExpandedAtStart = currentExpanded
                        var isHoldRegistered = false
                        var dragAccumulatorY = 0f
                        var dragAccumulatorX = 0f
                        var isDragging = false
                        var pillGestureTriggered = false

                        // Launched in the POINTER-INPUT scope, not the composition
                        // scope (`scope`), so an aborted gesture cancels the hold.
                        val holdJob = launch {
                            kotlinx.coroutines.delay(HOLD_GESTURE_THRESHOLD_MS)
                            isHoldRegistered = true
                            // iOS expands on touch-and-hold, and it expands WHILE the
                            // finger is still down rather than on release. Doing it
                            // here rather than in the up-branch is what makes the
                            // gesture feel responsive rather than laggy.
                            if (!wasExpandedAtStart && currentNotifications.isNotEmpty()) {
                                triggerHapticVibration(context, EXPAND_HAPTIC_DURATION_MS, EXPAND_HAPTIC_AMPLITUDE)
                                currentOnToggle()
                            }
                        }

                        val pointerId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (!change.pressed && !change.changedToUp()) {
                                // Gesture cancellation. Compose has no public "cancel"
                                // event type: a pointer that stops being pressed without
                                // an up event is how cancellation surfaces (a parent
                                // intercepted the gesture, the window lost focus, a
                                // system dialog appeared).
                                //
                                // Previously this was never handled, so the up-branch
                                // still ran and fired a tap action for a gesture the
                                // user never actually completed. See AUDIT.md 5.6.
                                holdJob.cancel()
                                break
                            }

                            if (change.changedToUp()) {
                                change.consume()
                                holdJob.cancel()
                                val totalElapsedMs = System.currentTimeMillis() - pressTimeMs
                                val currentNotification = currentNotifications.getOrNull(currentSelectedIndex)

                                if (wasExpandedAtStart) {
                                    val swipeUpThreshold = -SWIPE_THRESHOLD_DP * displayMetrics.density
                                    val swipeDownThreshold = SWIPE_THRESHOLD_DP * displayMetrics.density
                                    if (isDragging && currentSettings.enableSwipeActions && dragOffset < swipeUpThreshold) {
                                        val isHold = isHoldRegistered || totalElapsedMs >= HOLD_GESTURE_THRESHOLD_MS
                                        val actionStr = if (isHold) currentSettings.swipeHoldUpAction else currentSettings.swipeUpAction
                                        val action = SwipeAction.fromId(actionStr, if (isHold) SwipeAction.DismissAll else SwipeAction.DismissCurrent)
                                        executeSwipeAction(
                                            action = action,
                                            currentNotification = currentNotification,
                                            context = context,
                                            onDismiss = currentOnDismiss,
                                            onDismissAll = currentOnDismissAll,
                                            onToggle = currentOnToggle,
                                            onOpenNotification = currentOnOpenNotification,
                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                            onPageSelected = currentOnPageSelected,
                                            notificationsSize = currentNotifications.size,
                                            currentIndex = currentSelectedIndex
                                        )
                                    } else if (isDragging && currentSettings.enableSwipeActions && dragOffset > swipeDownThreshold) {
                                        val action = SwipeAction.fromId(currentSettings.swipeDownAction, SwipeAction.FloatingWindow)
                                        executeSwipeAction(
                                            action = action,
                                            currentNotification = currentNotification,
                                            context = context,
                                            onDismiss = currentOnDismiss,
                                            onDismissAll = currentOnDismissAll,
                                            onToggle = currentOnToggle,
                                            onOpenNotification = currentOnOpenNotification,
                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                            onPageSelected = currentOnPageSelected,
                                            notificationsSize = currentNotifications.size,
                                            currentIndex = currentSelectedIndex
                                        )
                                    } else if (abs(dragAccumulatorY) < 10f * displayMetrics.density &&
                                        abs(dragAccumulatorX) < 10f * displayMetrics.density
                                    ) {
                                        // Tap (or micro-drag) on the expanded card.
                                        //
                                        // Previously this guard was `!isDragging || ...`,
                                        // but `isDragging` flips true on the first
                                        // sub-pixel move, so it was effectively always
                                        // false and the test collapsed to a vertical-only
                                        // check. A purely horizontal 60dp drag therefore
                                        // satisfied it and opened the app. Now both
                                        // accumulators are tested. See AUDIT.md 5.2.
                                        if (!isHoldRegistered) {
                                            if (currentNotification != null) {
                                                currentOnOpenNotification(currentNotification)
                                            } else {
                                                DotIslandRepositories.notificationRepository(context).resetTimer()
                                            }
                                        }
                                    }
                                } else {
                                    // Collapsed state (In-Pill Gestures)
                                    if (!pillGestureTriggered) {
                                        val isPillSwipeEnabled = currentSettings.enablePillSwipeActions
                                        val pillThreshold = PILL_SWIPE_THRESHOLD_DP * displayMetrics.density
                                        val absX = abs(dragAccumulatorX)
                                        val absY = abs(dragAccumulatorY)

                                        if (isPillSwipeEnabled && (absX >= pillThreshold || absY >= pillThreshold)) {
                                            pillGestureTriggered = true
                                            if (absX > absY) {
                                                if (dragAccumulatorX < -pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeLeftAction, SwipeAction.PreviousNotification)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                } else if (dragAccumulatorX > pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeRightAction, SwipeAction.NextNotification)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                }
                                            } else {
                                                if (dragAccumulatorY < -pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeUpAction, SwipeAction.DismissCurrent)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                } else if (dragAccumulatorY > pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeDownAction, SwipeAction.Expand)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                }
                                            }
                                        } else {
                                            // Tap on the collapsed pill.
                                            //
                                            // iOS mapping: a SINGLE TAP opens the source
                                            // app; a touch-and-hold expands. Expansion
                                            // already happened in holdJob above while the
                                            // finger was down, so reaching here without a
                                            // hold means this is a tap.
                                            //
                                            // This used to be the opposite (tap expanded),
                                            // which is why Dot Island never felt like a
                                            // Dynamic Island. See REDESIGN.md section 3.1.
                                            if (!isHoldRegistered) {
                                                if (currentSettings.autoHidePill && isAutoHidden) {
                                                    // Waking a hidden pill must not also launch
                                                    // an app.
                                                    isAutoHidden = false
                                                    userInteractionTimestamp = System.currentTimeMillis()
                                                } else {
                                                    val currentNotification =
                                                        currentNotifications.getOrNull(currentSelectedIndex)
                                                    if (currentNotification != null) {
                                                        currentOnOpenNotification(currentNotification)
                                                    } else {
                                                        DotIslandRepositories.notificationRepository(context)
                                                            .resetTimer()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                break
                            } else {
                                val dragAmountY = change.positionChange().y
                                val dragAmountX = change.positionChange().x
                                if (abs(dragAmountY) > 0.5f || abs(dragAmountX) > 0.5f) {
                                    isDragging = true
                                    dragAccumulatorY += dragAmountY
                                    dragAccumulatorX += dragAmountX
                                    change.consume()
                                    if (abs(dragAccumulatorY) > 5f * displayMetrics.density || abs(dragAccumulatorX) > 5f * displayMetrics.density) {
                                        holdJob.cancel()
                                    }
                                    if (wasExpandedAtStart) {
                                        dragOffset = dragAccumulatorY.coerceIn(
                                            -DRAG_MAX_OFFSET_DP * displayMetrics.density,
                                            DRAG_MAX_OFFSET_DP * displayMetrics.density
                                        )
                                    } else {
                                        pillDragOffsetX = (dragAccumulatorX * 0.35f).coerceIn(
                                            -24f * displayMetrics.density,
                                            24f * displayMetrics.density
                                        )
                                        dragOffset = (dragAccumulatorY * 0.35f).coerceIn(
                                            -12f * displayMetrics.density,
                                            12f * displayMetrics.density
                                        )

                                        // Snappy immediate execution when swipe threshold is crossed while dragging
                                        if (!pillGestureTriggered && currentSettings.enablePillSwipeActions) {
                                            val pillThreshold = PILL_SWIPE_THRESHOLD_DP * displayMetrics.density
                                            val absX = abs(dragAccumulatorX)
                                            val absY = abs(dragAccumulatorY)

                                            if (absX >= pillThreshold || absY >= pillThreshold) {
                                                pillGestureTriggered = true
                                                holdJob.cancel()
                                                val currentNotification = currentNotifications.getOrNull(currentSelectedIndex)

                                                if (absX > absY) {
                                                    if (dragAccumulatorX < -pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeLeftAction, SwipeAction.PreviousNotification)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    } else if (dragAccumulatorX > pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeRightAction, SwipeAction.NextNotification)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    }
                                                } else {
                                                    if (dragAccumulatorY < -pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeUpAction, SwipeAction.DismissCurrent)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    } else if (dragAccumulatorY > pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeDownAction, SwipeAction.Expand)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        holdJob.cancel()
                        if (pillDragOffsetX != 0f) {
                            val startPillOffset = pillDragOffsetX
                            scope.launch {
                                androidx.compose.animation.core.Animatable(startPillOffset).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    pillDragOffsetX = value
                                }
                            }
                        }
                        if (dragOffset != 0f) {
                            val startDrag = dragOffset
                            scope.launch {
                                androidx.compose.animation.core.Animatable(startDrag).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    dragOffset = value
                                }
                            }
                        }
                    }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Compact content layer, pinned to the pill bounds at top-centre.
            //
            // It shrinks slightly as it fades, so the compact glyphs read as being
            // absorbed into the shape as it grows rather than sitting on top of it
            // and then blinking out. Combined with the staggered cross-fade specs,
            // this is what makes the container feel like a single morphing object.
            if (showCompactContent) {
                Box(
                    modifier = Modifier
                        .width(settings.width.dp)
                        .height(settings.height.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = collapsedAlpha
                            val absorb = COMPACT_ABSORB_MIN_SCALE +
                                (1f - COMPACT_ABSORB_MIN_SCALE) * collapsedAlpha
                            scaleX = absorb
                            scaleY = absorb
                        }
                ) {
                    CompactActivityContent(
                        notification = activeNotification,
                        showIdleIndicator = showIdleIndicator,
                        collapsedAlphaProvider = { collapsedAlpha },
                        settings = settings
                    )
                }
            }

            // Expanded content layer — smoothly fade out while collapsing
            if (showExpandedContent) {
                Box(
                    modifier = Modifier
                        // Measure text/pager at the final width, not every
                        // intermediate morph width. The parent clips the reveal.
                        .requiredWidth(expandedWidth)
                        .wrapContentHeight(unbounded = true)
                        .graphicsLayer {
                            alpha = expandedAlpha
                            // Camera-aware music placement uses exact screen-space
                            // exclusions; don't independently slide/scale its text.
                            scaleX = if (activeMode == IslandMode.Music) 1f else contentScale
                            scaleY = if (activeMode == IslandMode.Music) 1f else contentScale
                            translationY = if (activeMode == IslandMode.Music) 0f else contentSlideY.toPx()
                        }
                ) {
                    IslandExpandedContent(
                        notifications = notifications,
                        selectedIndex = selectedIndex,
                        onPageSelected = onPageSelected,
                        onOpenNotification = onOpenNotification,
                        onCollapse = onToggleExpanded,
                        statusBarHeight = statusBarHeight.dp,
                        // Each mode owns its natural height and presentation budget.
                        // Music permits 180dp for equal insets; other modes use 84–160dp.
                        // Overflow stays reachable because
                        // IslandExpandedContent makes the page content scrollable when
                        // it exceeds the ceiling.
                        //
                        // Measurements are DEFERRRED while the expand/collapse
                        // transition is in flight. `height` below is a spring
                        // animating toward `expandedHeight`, so writing to it from
                        // here re-aims that spring mid-animation: the card would
                        // visibly resize while it was growing, which reads as a pop.
                        // The registry estimate is used for the animation and the
                        // real measurement is applied once the transition settles.
                        onHeightMeasured = { measured ->
                            val clamped = presentationFor(activeMode).clampHeight(measured)
                            if (isTransitionSettled) {
                                if (expandedHeight != clamped) {
                                    android.util.Log.d(
                                        TAG,
                                        "expanded height ${expandedHeight.value} -> ${clamped.value}"
                                    )
                                    expandedHeight = clamped
                                }
                            } else {
                                pendingMeasuredHeight = clamped
                            }
                        },
                        settings = settings,
                        cameraBounds = cameraBounds,
                        cardScreenTop = expandedTopOffset,
                        // Outside the page's vertical scroller: scrolling cannot
                        // bring text back underneath the punch-hole camera.
                        modifier = Modifier.padding(top = expandedContentTopPadding),
                        onReplyStateChanged = onReplyStateChanged
                    )
                }
            }
        }

        // Collapsed: secondary circle. Expanded with 2: the same item morphs
        // into a full-size pill. Expanded with 3+: it stays the circle on the right.
        //
        // Faded out by expandedAlpha. These bubbles were previously gated only on
        // `secondaryAlpha > 0f`, which is driven by isSplitMode and is independent
        // of expansion. So they stayed at full opacity and drew ON TOP of the
        // expanded card, which is a large part of why the expansion read as a
        // separate popup panel sitting over the pill rather than the pill becoming
        // the card. See REDESIGN.md section 4.1.
        val collapsedBubbleAlpha = { secondaryAlpha * (1f - expandedAlpha) }
        val showSecondary by remember { derivedStateOf { collapsedBubbleAlpha() > 0.01f } }
        if (!settings.enableNotchMode && showSecondary && secondaryNotification != null) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset(
                            secondaryOffset.roundToPx(),
                            0
                        )
                    }
                    .width(secondaryBubbleWidth)
                    .height(circleSize)
                    .graphicsLayer {
                        hostView.getLocationOnScreen(windowLocation)
                        translationX = calculateWindowCenterCorrection(
                            displayMetrics.widthPixels, windowWidthPx, windowLocation[0]
                        )
                        translationY = (if (settings.enableNotchMode) 0f else settings.yOffset.dp.toPx()) - windowLocation[1]
                        alpha = collapsedBubbleAlpha()
                        scaleX = secondaryScale * switchScaleAnim.value
                        scaleY = secondaryScale * switchScaleAnim.value
                    }
                    .then(
                        if (settings.enableShadow && settings.shadowElevation > 0f) {
                            Modifier.shadow(
                                elevation = (settings.shadowElevation * 0.85f).dp,
                                shape = RoundedCornerShape(secondaryBubbleCorner),
                                clip = false,
                                ambientColor = Color.Black,
                                spotColor = Color.Black
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(secondaryBubbleCorner))
                    .background(pillBackgroundColor.copy(alpha = settings.opacity))
                    .border(ISLAND_BORDER_WIDTH, ISLAND_BORDER_COLOR, RoundedCornerShape(secondaryBubbleCorner))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (secondaryIndex >= 0) {
                            onPageSelected(secondaryIndex)
                        }
                        if (!currentExpanded) {
                            currentOnToggle()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - secondaryPillProgress },
                    contentAlignment = Alignment.Center
                ) {
                    SecondaryBubbleContent(
                        notification = secondaryNotification,
                        settings = settings
                    )
                }
                Box(
                    modifier = Modifier
                        .requiredWidth(miniPillWidth)
                        .height(circleSize)
                        .graphicsLayer { alpha = secondaryPillProgress },
                    contentAlignment = Alignment.Center
                ) {
                    IslandCollapsedContent(
                        mode = secondaryNotification.mode,
                        notification = secondaryNotification,
                        collapsedAlpha = 1f,
                        settings = settings
                    )
                }
            }
        }

        val collapsedTertiaryAlpha = { tertiaryAlpha * (1f - expandedAlpha) }
        val showTertiary by remember { derivedStateOf { collapsedTertiaryAlpha() > 0.01f } }
        if (!settings.enableNotchMode && showTertiary && tertiaryNotification != null) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        // Screen-relative; converted by the graphics layer below.
                        IntOffset(
                            (collapsedMainLeft - screenCenter + miniPillWidth / 2f).roundToPx(),
                            0
                        )
                    }
                    .width(miniPillWidth)
                    .height(circleSize)
                    .graphicsLayer {
                        hostView.getLocationOnScreen(windowLocation)
                        translationX = calculateWindowCenterCorrection(
                            displayMetrics.widthPixels, windowWidthPx, windowLocation[0]
                        )
                        translationY = (if (settings.enableNotchMode) 0f else settings.yOffset.dp.toPx()) - windowLocation[1]
                        alpha = collapsedTertiaryAlpha()
                        scaleX = tertiaryScale * switchScaleAnim.value
                        scaleY = tertiaryScale * switchScaleAnim.value
                    }
                    .then(
                        if (settings.enableShadow && settings.shadowElevation > 0f) {
                            Modifier.shadow(
                                elevation = (settings.shadowElevation * 0.85f).dp,
                                shape = RoundedCornerShape(settings.cornerRadius.dp),
                                clip = false,
                                ambientColor = Color.Black,
                                spotColor = Color.Black
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(settings.cornerRadius.dp))
                    .background(pillBackgroundColor.copy(alpha = settings.opacity))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (tertiaryIndex >= 0) {
                            onPageSelected(tertiaryIndex)
                        }
                        if (!currentExpanded) {
                            currentOnToggle()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                IslandCollapsedContent(
                    mode = tertiaryNotification.mode,
                    notification = tertiaryNotification,
                    collapsedAlpha = 1f,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun SecondaryBubbleContent(
    notification: IslandNotification,
    settings: DotIslandSettings
) {
    ActivityGlyph(notification, Modifier.size(20.dp))
}

// Animation specs
/**
 * Inset applied to the expanded card on its top, left and right edges.
 *
 * Equal on all three sides is the point: it makes the card's top edge align with
 * the collapsed pill's default position so the morph starts from zero
 * displacement, instead of the card dropping down before it grows.
 *
 * 12dp matches the pill's default y offset (33px at 2.75 density on the Nothing
 * Phone (4a)). The HIG's equivalent is 11pt, derived from 430 - 408 = 22.
 * See REDESIGN.md section 3.2.
 */
internal const val EXPANDED_CARD_MARGIN_DP = 12f

/** Floor so the card stays usable on very narrow screens. */
internal const val MIN_EXPANDED_WIDTH_DP = 260f

/**
 * Scale the compact content shrinks to as it is absorbed into the expanding
 * shape. 1f is fully collapsed, 0.82f is fully absorbed.
 */
private const val COMPACT_ABSORB_MIN_SCALE = 0.82f

/**
 * Width of the expanded card.
 *
 * iOS geometry: the Dynamic Island's expanded presentation is inset from the
 * screen edges by a fixed margin rather than being a percentage of the screen.
 * The HIG's own numbers are internally consistent at an 11pt inset
 * (430 - 408 = 22, 393 - 371 = 22), so the margin is the portable constant and
 * the width falls out of it.
 *
 * Previously this was `screenWidthDp * 0.95`, which on a 445dp screen produced a
 * 423dp card only by coincidence of the ratio, and produced a card whose inset
 * differed from the inset used on the vertical axis.
 */
internal fun calculateExpandedWidth(
    isLandscape: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    marginDp: Float = EXPANDED_CARD_MARGIN_DP
): Float {
    val effectiveWidth = if (isLandscape) minOf(screenWidthDp, screenHeightDp) else screenWidthDp
    return (effectiveWidth - 2f * marginDp).coerceAtLeast(MIN_EXPANDED_WIDTH_DP)
}

/**
 * Top offset of the expanded card.
 *
 * iOS geometry, and the single most important value for the morph: the card's top
 * edge sits at the SAME margin as its left and right edges, so it aligns exactly
 * with the collapsed pill's default position. The container therefore does not
 * drop before it grows, and the expansion begins with zero vertical displacement.
 *
 * The previous implementation returned `maxOf(statusBarHeightDp,
 * circleSizeDp + compactGapDp)`, which on this device is 42dp against a pill at
 * 12dp. That 30dp downward jump is what made the expansion read as a separate
 * panel appearing below the pill rather than the pill becoming the card.
 *
 * Notch mode still needs to clear the hardware cutout, so it retains its own
 * behaviour.
 */
internal fun calculateExpandedTopOffset(
    enableNotchMode: Boolean,
    hasCompanion: Boolean,
    statusBarHeightDp: Float,
    notchHeightDp: Float = 35f,
    circleSizeDp: Float = 34f,
    compactGapDp: Float = COMPACT_INDICATOR_GAP_DP,
    marginDp: Float = EXPANDED_CARD_MARGIN_DP
): Float {
    return if (enableNotchMode) {
        // Docked to the top edge, but must still clear the camera cutout.
        maxOf(marginDp, notchHeightDp)
    } else {
        marginDp
    }
}


/**
 * Geometry for the collapsed pill plus its optional companion circle.
 *
 * All values are plain density-independent pixels so this is callable from a
 * JVM unit test. It was extracted from the body of IslandOverlayView for
 * exactly that reason: the layout math used to live inline inside the
 * @Composable, which made it unreachable from tests. IslandOverlayLayoutTest
 * previously worked around that by re-implementing the same arithmetic inside
 * the test file, which asserted that the copy agreed with itself and passed
 * green even when the real composable was wrong.
 *
 * The `!hasCompanion` branch is included deliberately. The old inline test
 * copy omitted it entirely, so the no-companion case was never exercised.
 */
internal data class CollapsedLayoutGeometry(
    val mainLeftDp: Float,
    val circleLeftDp: Float,
    val groupCenterDp: Float,
    val mainOffsetDp: Float
)

internal fun calculateCollapsedLayout(
    screenWidthDp: Float,
    screenCenterDp: Float,
    pillWidthDp: Float,
    circleSizeDp: Float,
    xOffsetDp: Float,
    hasCompanion: Boolean,
    isCircleLeft: Boolean,
    isFullWidth: Boolean,
    enableNotchMode: Boolean,
    compactGapDp: Float = COMPACT_INDICATOR_GAP_DP
): CollapsedLayoutGeometry {
    val collapsedGroupWidth = pillWidthDp + if (hasCompanion) compactGapDp + circleSizeDp else 0f

    val desiredMainLeft = screenCenterDp + xOffsetDp - pillWidthDp / 2f
    val (minMainLeft, maxMainLeft) = when {
        !hasCompanion -> compactGapDp to
            (screenWidthDp - compactGapDp - pillWidthDp).coerceAtLeast(compactGapDp)
        isCircleLeft -> (compactGapDp + circleSizeDp + compactGapDp) to
            (screenWidthDp - compactGapDp - pillWidthDp).coerceAtLeast(compactGapDp + circleSizeDp + compactGapDp)
        else -> compactGapDp to
            (screenWidthDp - compactGapDp - collapsedGroupWidth).coerceAtLeast(compactGapDp)
    }
    val collapsedMainLeft = desiredMainLeft.coerceIn(minMainLeft, maxMainLeft)
    val mainCenter = collapsedMainLeft + pillWidthDp / 2f
    val circleLeft = if (isCircleLeft) {
        collapsedMainLeft - compactGapDp - circleSizeDp
    } else {
        collapsedMainLeft + pillWidthDp + compactGapDp
    }
    val groupStart = if (isCircleLeft && hasCompanion) circleLeft else collapsedMainLeft
    val groupEnd = if (!isCircleLeft && hasCompanion) circleLeft + circleSizeDp else collapsedMainLeft + pillWidthDp
    val groupCenter = (groupStart + groupEnd) / 2f

    val mainOffset = when {
        enableNotchMode -> xOffsetDp
        isFullWidth -> mainCenter - screenCenterDp
        else -> mainCenter - groupCenter
    }

    return CollapsedLayoutGeometry(
        mainLeftDp = collapsedMainLeft,
        circleLeftDp = circleLeft,
        groupCenterDp = groupCenter,
        mainOffsetDp = mainOffset
    )
}

private const val SWIPE_THRESHOLD_DP = 35f
private const val PILL_SWIPE_THRESHOLD_DP = 16f
private const val DRAG_MAX_OFFSET_DP = 100f
private const val COMPACT_INDICATOR_GAP_DP = 8f
private const val HOLD_GESTURE_THRESHOLD_MS = 300L

internal const val EXPAND_HAPTIC_DURATION_MS = 12L
internal const val EXPAND_HAPTIC_AMPLITUDE = 64

private fun triggerHapticVibration(
    context: android.content.Context,
    durationMs: Long = 60L,
    amplitude: Int = android.os.VibrationEffect.DEFAULT_AMPLITUDE
) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            val vibrator = vm?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, amplitude))
                return
            }
        }
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }
}

internal enum class CompactNotificationShape { MiniPill, Circle }

internal fun defaultEstimatedHeightForMode(mode: IslandMode?): Dp =
    presentationFor(mode).estimatedHeight

internal fun compactNotificationShapes(
    notificationCount: Int,
    expanded: Boolean
): List<CompactNotificationShape> = when {
    notificationCount < 2 -> emptyList()
    !expanded -> listOf(CompactNotificationShape.Circle)
    notificationCount == 2 -> listOf(CompactNotificationShape.MiniPill)
    else -> listOf(CompactNotificationShape.MiniPill, CompactNotificationShape.Circle)
}

private fun trySkipMedia(
    context: android.content.Context?,
    notification: IslandNotification?,
    forward: Boolean
): Boolean {
    if (context == null) return false

    // 1. Direct notification action PendingIntent (e.g. Spotify, YouTube Music, podcasts)
    val actionSent = if (forward) {
        notification.trySendFirstAction(context, "next", "skip", "forward")
    } else {
        notification.trySendFirstAction(context, "previous", "prev", "rewind")
    }
    if (actionSent) return true

    // 2. Notification MediaSession token
    val token = notification?.mediaToken
    if (token != null) {
        val success = runCatching {
            val controller = android.media.session.MediaController(context, token)
            if (forward) controller.transportControls.skipToNext() else controller.transportControls.skipToPrevious()
            true
        }.getOrDefault(false)
        if (success) return true
    }

    // 3. Delegate to NotificationListenerService via DotIslandCommand
    runCatching {
        val repo = DotIslandRepositories.notificationRepository(context)
        if (forward) {
            repo.sendCommand(DotIslandCommand.SkipNext(notification?.packageName))
        } else {
            repo.sendCommand(DotIslandCommand.SkipPrevious(notification?.packageName))
        }
    }

    // 4. Fallback: AudioManager media key events
    runCatching {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val keyCode = if (forward) android.view.KeyEvent.KEYCODE_MEDIA_NEXT else android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(down)
        audioManager?.dispatchMediaKeyEvent(up)
    }

    return true
}

private fun tryPlayPauseMedia(
    context: android.content.Context?,
    notification: IslandNotification?
) {
    if (context == null) return

    // 1. Direct notification action PendingIntent
    val actionSent = notification.trySendFirstAction(context, "play", "pause", "resume", "toggle")
    if (actionSent) return

    // 2. Notification MediaSession token
    val token = notification?.mediaToken
    if (token != null) {
        val success = runCatching {
            val controller = android.media.session.MediaController(context, token)
            if (notification.mediaIsPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            true
        }.getOrDefault(false)
        if (success) return
    }

    // 3. Delegate to NotificationListenerService via DotIslandCommand
    runCatching {
        val repo = DotIslandRepositories.notificationRepository(context)
        repo.sendCommand(DotIslandCommand.PlayPause(notification?.packageName))
    }

    // 4. Fallback: AudioManager media key events
    runCatching {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(down)
        audioManager?.dispatchMediaKeyEvent(up)
    }
}

internal fun executeSwipeAction(
    action: SwipeAction,
    currentNotification: IslandNotification?,
    context: android.content.Context? = null,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onToggle: () -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit,
    onPageSelected: (Int) -> Unit,
    notificationsSize: Int,
    currentIndex: Int
) {
    if (context != null && action != SwipeAction.None) {
        if (action == SwipeAction.Expand) {
            triggerHapticVibration(context, EXPAND_HAPTIC_DURATION_MS, EXPAND_HAPTIC_AMPLITUDE)
        } else triggerHapticVibration(context)
    }
    when (action) {
        SwipeAction.DismissCurrent -> onDismiss()
        SwipeAction.DismissAll -> onDismissAll()
        SwipeAction.Collapse -> onToggle()
        SwipeAction.Expand -> onToggle()
        SwipeAction.OpenApp -> {
            if (currentNotification != null) {
                onOpenNotification(currentNotification)
            }
        }
        SwipeAction.FloatingWindow -> onOpenFloatingWindow()
        SwipeAction.NotificationShade -> onOpenNotificationShade()
        SwipeAction.NextPrevious, SwipeAction.NextNotification -> {
            if (notificationsSize > 1) {
                val nextIndex = (currentIndex + 1) % notificationsSize
                onPageSelected(nextIndex)
            } else {
                trySkipMedia(context, currentNotification, forward = true)
            }
        }
        SwipeAction.PreviousNotification -> {
            if (notificationsSize > 1) {
                val prevIndex = (currentIndex - 1 + notificationsSize) % notificationsSize
                onPageSelected(prevIndex)
            } else {
                trySkipMedia(context, currentNotification, forward = false)
            }
        }
        SwipeAction.NextTrack -> {
            trySkipMedia(context, currentNotification, forward = true)
        }
        SwipeAction.PreviousTrack -> {
            trySkipMedia(context, currentNotification, forward = false)
        }
        SwipeAction.PlayPause -> {
            tryPlayPauseMedia(context, currentNotification)
        }
        SwipeAction.None -> {
            // Disabled / No action
        }
    }
}

/** Convert a screen-centred translation to a translation about this window's centre. */
internal fun calculateWindowCenterCorrection(
    screenWidthPx: Int,
    windowWidthPx: Int,
    windowLeftPx: Int
): Float = screenWidthPx / 2f - windowLeftPx - windowWidthPx / 2f

private const val TAG = "DotIslandOverlayView"

private fun islandCornerShape(radius: Dp, squareTop: Boolean): RoundedCornerShape =
    if (squareTop) RoundedCornerShape(0.dp, 0.dp, radius, radius) else RoundedCornerShape(radius)

/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.service

import androidx.lifecycle.Lifecycle
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.qarasky.dotisland.MainActivity
import dev.qarasky.dotisland.R
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandCommand
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.IslandViewModel
import dev.qarasky.dotisland.ui.ThemedOverlayIsland
import dev.qarasky.dotisland.ui.expanded.sendIntentWithOptions
import dev.qarasky.dotisland.util.runCatchingLogged
import dev.qarasky.dotisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DotIslandOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    @Inject lateinit var repository: DotIslandSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    private var islandView: ComposeView? = null
    private val overlayOwners = OverlayViewTreeOwners()
    private lateinit var viewModel: IslandViewModel
    private var isLockScreenActive: Boolean = false
    private var screenStateReceiverRegistered = false
    private var foregroundStarted = false
    @Volatile private var destroyed = false
    private var isWindowExpanded: Boolean = false
    private val expandedWindowReady = MutableStateFlow(false)
    private var lastParams: WindowManager.LayoutParams? = null
    private val overlayRefreshRate by lazy {
        val display = getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        selectOverlayRefreshRate(display?.supportedModes.orEmpty()
            .filter { mode != null && it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate })
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, error ->
                android.util.Log.e(TAG, "Unhandled overlay coroutine failure", error)
            }
    )

    // Monitor screen state and unlock events to show/hide the island accordingly
    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatchingLogged(TAG, "Screen-state callback failed") {
                if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        overlayOwners.resume()
                        isLockScreenActive = keyguardManager?.isKeyguardLocked == true
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        overlayOwners.pause()
                        isLockScreenActive = true
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        isLockScreenActive = false
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                }
            }
        }
    }

    // Fallback sync: check if keyguard locked state changed on window changes.
    // Wrapped: an uncaught throw here makes Android auto-disable the
    // AccessibilityService, which is exactly the "turns off by itself" symptom.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatchingLogged(TAG, "onAccessibilityEvent failed") {
            if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val locked = keyguardManager?.isKeyguardLocked == true
            if (isLockScreenActive != locked) {
                isLockScreenActive = locked
                updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
            }

            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val openedPackage = event.packageName?.toString()
                if (!openedPackage.isNullOrEmpty() &&
                    (openedPackage != packageName || event.className?.toString() == "dev.qarasky.dotisland.MainActivity") &&
                    openedPackage != "com.android.systemui"
                ) {
                    viewModel.foregroundPackage.value = openedPackage
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override, no-op
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Wrapped: a throw here would make Android disable the service automatically.
        runCatchingLogged(TAG, "onConfigurationChanged failed") {
            if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
            updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
        }
    }

    override fun onCreate() {
        super.onCreate()
        destroyed = false

        runCatchingLogged(TAG, "createNotificationChannel failed") {
            createNotificationChannel()
        }

        val resolvedWindowManager = runCatchingLogged(TAG, "WindowManager initialization failed") {
            getSystemService(WindowManager::class.java)
        }
        if (resolvedWindowManager == null) {
            android.util.Log.e(TAG, "WindowManager is unavailable; overlay cannot start")
            return
        }
        windowManager = resolvedWindowManager

        val initializedViewModel = runCatchingLogged(TAG, "Overlay ViewModel initialization failed") {
            // Lifecycle must be restored before the service-owned ViewModel is created.
            overlayOwners.resume()
            ViewModelProvider(
                overlayOwners,
                IslandViewModel.provideFactory(repository, notificationRepository)
            )[IslandViewModel::class.java]
        }
        if (initializedViewModel == null) {
            android.util.Log.e(TAG, "Overlay ViewModel is unavailable; overlay cannot start")
            return
        }
        viewModel = initializedViewModel
        
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isLockScreenActive = keyguardManager?.isKeyguardLocked == true

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatchingLogged(TAG, "registerReceiver screenStateReceiver failed") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, screenFilter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenStateReceiver, screenFilter)
            }
            screenStateReceiverRegistered = true
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Settings collector failed") {
                repository.settings.collect { settings ->
                    if (destroyed) return@collect
                    if (!settings.enabled) {
                        stopOverlaySession()
                    } else {
                        startOverlaySession(settings)
                    }
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Expanded-state collector failed") {
                viewModel.expanded.collectLatest { expanded ->
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    if (expanded) {
                        isWindowExpanded = true
                        updateWindowLayoutParams(true, viewModel.settings.value)
                    }
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Notifications-state collector failed") {
                viewModel.visibleNotifications.collectLatest {
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Input-active collector failed") {
                viewModel.isInputActive.collectLatest {
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isSystemConnected = true
        if (destroyed || !::viewModel.isInitialized) return
        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Service reconnect failed") {
                val settings = repository.settings.first()
                if (settings.enabled) {
                    startOverlaySession(settings)
                } else {
                    stopOverlaySession()
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isSystemConnected = false
        // Return true so Android system knows to re-bind the accessibility service automatically
        return true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runCatchingLogged(TAG, "onTaskRemoved recovery failed") {
            if (!destroyed &&
                ::viewModel.isInitialized &&
                viewModel.settings.value.enabled
            ) {
                ensureForegroundStarted()
                ensureCollapsedWindow()
            }
        }
    }

    override fun onDestroy() {
        if (destroyed) return
        destroyed = true
        isSystemConnected = false
        serviceScope.cancel()

        if (screenStateReceiverRegistered) {
            runCatchingLogged(TAG, "unregisterReceiver screenStateReceiver failed") {
                unregisterReceiver(screenStateReceiver)
            }
            screenStateReceiverRegistered = false
        }

        removeCollapsedWindow()
        stopForegroundSafely()
        runCatchingLogged(TAG, "Overlay owners destroy failed") {
            overlayOwners.destroy()
        }
        super.onDestroy()
    }

    private fun startOverlaySession(settings: DotIslandSettings) {
        if (destroyed || !::windowManager.isInitialized || !::viewModel.isInitialized) return
        ensureForegroundStarted()
        ensureCollapsedWindow()
        updateWindowLayoutParams(isWindowExpanded, settings)
    }

    private fun stopOverlaySession() {
        removeCollapsedWindow()
        stopForegroundSafely()
        if (::viewModel.isInitialized) {
            viewModel.collapse()
        }
    }

    private fun ensureForegroundStarted() {
        if (foregroundStarted || destroyed) return
        runCatchingLogged(TAG, "startForeground failed") {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        }
    }

    private fun stopForegroundSafely() {
        if (!foregroundStarted) return
        runCatchingLogged(TAG, "stopForeground failed") {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted = false
    }

    private val statusBarHeight: Float
        get() {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val heightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
            val heightDp = heightPx / resources.displayMetrics.density
            return if (heightDp > 0f) heightDp else 24f
        }

    private fun ensureCollapsedWindow() {
        if (destroyed ||
            islandView != null ||
            !::windowManager.isInitialized ||
            !::viewModel.isInitialized
        ) return
        try {
            islandView = ComposeView(this).apply {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = keyguardManager?.isKeyguardLocked == true
                isLockScreenActive = isLocked
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val isHidden = (!viewModel.settings.value.showOnLockScreen && isLocked) || (isLandscape && !viewModel.settings.value.showInLandscape)
                visibility = if (isHidden) android.view.View.GONE else android.view.View.VISIBLE

                installOverlayViewTreeOwners()
                isFocusable = true
                isFocusableInTouchMode = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    expandedWindowReady.value = isWindowExpanded &&
                        view.width == resources.displayMetrics.widthPixels &&
                        view.height > computeCollapsedWindowGeometry(
                            viewModel.settings.value,
                            viewModel.visibleNotifications.value.size,
                            resources.displayMetrics.density,
                            resources.displayMetrics.widthPixels.toFloat()
                        ).heightPx
                }
                setContent {
                    // Start the morph only after the enlarged window is laid out.
                    val windowReady by expandedWindowReady.collectAsStateWithLifecycle(
                        minActiveState = Lifecycle.State.RESUMED
                    )
                    ThemedOverlayIsland(
                        viewModel = this@DotIslandOverlayService.viewModel,
                        statusBarHeight = statusBarHeight,
                        onOpenNotification = { notification -> openNotification(notification) },
                        onOpenFloatingWindow = {},
                        onOpenNotificationShade = {
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                            viewModel.collapse()
                        },
                        isFullWidth = windowReady,
                        onCollapseAnimationFinished = { finishWindowCollapse() }
                    )
                }
            }
            runCatchingLogged(TAG, "windowManager.addView failed") {
                windowManager.addView(islandView, collapsedParams(viewModel.settings.value))
            } ?: run {
                islandView = null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ensureCollapsedWindow fatal", e)
            islandView = null
        }
    }

    // REMOVED: setupTouchableRegion()
    //
    // This built a dynamic java.lang.reflect.Proxy for the hidden
    // android.view.ViewTreeObserver$OnComputeInternalInsetsListener so the
    // window could declare a touchable Region equal to the pill while still
    // being MATCH_PARENT. It depended on hidden APIs and an exemption in
    // DotIslandApp. Neither is needed for the small-window touch model.
    //
    // The capability is replaced by FLAG_NOT_TOUCH_MODAL, which this window has
    // always set: touches landing outside a window's bounds go to the window
    // behind it. So the collapsed window is now simply sized to the pill plus a
    // margin, rather than spanning the display and then carving a hole in it.
    // See AUDIT.md section 4.1.

    private fun updateWindowLayoutParams(expanded: Boolean, settings: DotIslandSettings) {
        if (destroyed || !::windowManager.isInitialized || !::viewModel.isInitialized) return
        val view = islandView ?: return
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        isLockScreenActive = isLocked
        viewModel.isLocked.value = isLocked

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val isIdleHidden = settings.hideWhenIdle && viewModel.notifications.value.isEmpty()
        val isHidden = (!settings.showOnLockScreen && isLocked) || (isLandscape && !settings.showInLandscape) || isIdleHidden

        val targetVisibility = if (isHidden) android.view.View.GONE else android.view.View.VISIBLE
        if (view.visibility != targetVisibility) {
            view.visibility = targetVisibility
        }

        // Collapsed: size the window to the pill. Expanded: span the display so
        // the card can be full-bleed and so taps outside collapse it.
        val geometry = computeCollapsedWindowGeometry(
            settings = settings,
            notificationCount = viewModel.visibleNotifications.value.size,
            density = density,
            screenWidthPx = screenWidthPx
        )

        val h = if (expanded) WindowManager.LayoutParams.MATCH_PARENT else geometry.heightPx
        val w = if (expanded) WindowManager.LayoutParams.MATCH_PARENT else geometry.widthPx
        val isInput = viewModel.isInputActive.value && expanded
        val focusFlags = if (isInput) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val currentFlags = focusFlags or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val currentX = if (expanded) 0 else geometry.xPx
        // Public hint, not a forced device setting. Keep it through collapse,
        // then release the vote when the small window returns.
        val currentRefreshRate = if (expanded) overlayRefreshRate else 0f
        val currentY = if (expanded || settings.enableNotchMode) 0 else geometry.yPx
        val currentSoftInputMode = if (isInput) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            0
        }

        val lp = lastParams
        if (lp != null &&
            lp.width == w &&
            lp.height == h &&
            lp.flags == currentFlags &&
            lp.x == currentX &&
            lp.y == currentY &&
            lp.preferredRefreshRate == currentRefreshRate &&
            lp.softInputMode == currentSoftInputMode
        ) {
            return
        }

        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            currentFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = currentX
            y = currentY
            preferredRefreshRate = currentRefreshRate
            if (Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
            softInputMode = currentSoftInputMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        lastParams = params
        runCatchingLogged(TAG, "Failed to update view layout") { 
            windowManager.updateViewLayout(view, params) 
        }
    }

    private fun finishWindowCollapse() {
        // Ignore stale completion callbacks when an expansion has superseded them.
        if (destroyed || viewModel.expanded.value || !isWindowExpanded) return
        isWindowExpanded = false
        updateWindowLayoutParams(false, viewModel.settings.value)
    }

    private fun removeCollapsedWindow() {
        val view = islandView ?: return
        // Clear the reference before removal so repeated teardown calls are harmless,
        // even when an OEM WindowManager throws while detaching an already-removed view.
        islandView = null
        lastParams = null
        isWindowExpanded = false
        expandedWindowReady.value = false
        if (!::windowManager.isInitialized) return
        runCatchingLogged(TAG, "Failed to remove view") {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        }
    }

    private fun collapsedParams(settings: DotIslandSettings): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()
        val geometry = computeCollapsedWindowGeometry(
            settings = settings,
            notificationCount = if (::viewModel.isInitialized) viewModel.visibleNotifications.value.size else 0,
            density = density,
            screenWidthPx = screenWidthPx
        )

        val currentFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            geometry.widthPx,
            geometry.heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            currentFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = geometry.xPx
            y = geometry.yPx
            if (Build.VERSION.SDK_INT >= 34) setCanPlayMoveAnimation(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }.also {
            lastParams = it
        }
    }

    private fun openNotification(notification: IslandNotification) {
        if (notification.contentIntent != null) {
            sendIntentWithOptions(this, notification.contentIntent)
        } else {
            runCatchingLogged(TAG, "Failed to launch package activity") {
                packageManager.getLaunchIntentForPackage(notification.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }
        }
        viewModel.collapse()
    }

    private fun Float.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun ComposeView.installOverlayViewTreeOwners() {
        setViewTreeLifecycleOwner(overlayOwners)
        setViewTreeViewModelStoreOwner(overlayOwners)
        setViewTreeSavedStateRegistryOwner(overlayOwners)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                OVERLAY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Dot Island overlay running"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, dev.qarasky.dotisland.MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle("Dot Island is active")
            .setContentText("Tap to open Dot Island")
            .setSmallIcon(R.drawable.ic_stat_dot_island)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    companion object {
        @Volatile
        var isSystemConnected: Boolean = false
            private set

        private const val TAG = "DotIslandOverlayService"
        private const val NOTIFICATION_ID = 8105
        private const val OVERLAY_CHANNEL_ID = "dot_island_overlay"
        private const val OVERLAY_CHANNEL_NAME = "Dot Island overlay"
        /**
         * How long the overlay window stays MATCH_PARENT after `expanded` flips
         * to false, before shrinking back to pill size.
         *
         * This has to be at least as long as the collapse animation in
         * IslandOverlayView, otherwise the content is still animating while the
         * window it is laid out in shrinks underneath it, which shows up as
         * elements jumping as they are re-measured against a smaller box.
         *
         * The collapse spring is critically damped at stiffness 780, which settles
         * in roughly 200ms, so 220ms clears it. Kept in step with
         * IslandOverlayView's collapse specs.
         */
    }
}

/**
 * Geometry for the collapsed overlay window.
 *
 * The window is deliberately sized to the pill (plus a margin) rather than
 * spanning the display. Touches outside a window's bounds are delivered to the
 * window behind it as long as FLAG_NOT_TOUCH_MODAL is set, which this window
 * always sets. That is how the island lets you tap straight through to the app
 * underneath it, and it needs no hidden API.
 *
 * This replaces an approach where the window was MATCH_PARENT and a hidden
 * ViewTreeObserver$OnComputeInternalInsetsListener callback carved a touchable
 * Region down to the pill. See AUDIT.md section 4.1.
 *
 * Extracted as a pure function because this arithmetic previously existed in
 * three places that could drift apart.
 */
internal data class CollapsedWindowGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val xPx: Int,
    val yPx: Int
)

/**
 * @param horizontalPaddingDp margin added to each side of the group.
 *   Must be at least [IslandOverlayView]'s collapsed drag clamp
 *   (`PILL_DRAG_MAX_OFFSET_DP`, 24dp) or the pill clips against the window edge
 *   at the extremes of a drag. The collapsed expand/collapse slide
 *   ([COLLAPSED_TRANSLATION_MAX_DP], 32dp) only runs while the window is
 *   MATCH_PARENT, so it does not need headroom here.
 */
internal fun computeCollapsedWindowGeometry(
    settings: DotIslandSettings,
    notificationCount: Int,
    density: Float,
    screenWidthPx: Float,
    horizontalPaddingDp: Float = COLLAPSED_WINDOW_PADDING_DP
): CollapsedWindowGeometry {
    val hasCompanion = if (settings.enableNotchMode) false else notificationCount >= 2
    val isCircleLeft = settings.circlePosition == DotIslandSettings.CIRCLE_POSITION_LEFT

    val mainWidthPx = settings.width * density
    val circleSizePx = settings.height * density
    val compactGapPx = COMPACT_GAP_DP * density
    val edgePaddingPx = EDGE_PADDING_DP * density
    val groupWidthPx = mainWidthPx + if (hasCompanion) compactGapPx + circleSizePx else 0f

    val desiredMainLeftPx = screenWidthPx / 2f + settings.xOffset * density - mainWidthPx / 2f
    val (minMainLeftPx, maxMainLeftPx) = when {
        !hasCompanion -> edgePaddingPx to
            (screenWidthPx - edgePaddingPx - mainWidthPx).coerceAtLeast(edgePaddingPx)
        isCircleLeft -> (edgePaddingPx + circleSizePx + compactGapPx) to
            (screenWidthPx - edgePaddingPx - mainWidthPx)
                .coerceAtLeast(edgePaddingPx + circleSizePx + compactGapPx)
        else -> edgePaddingPx to
            (screenWidthPx - edgePaddingPx - groupWidthPx).coerceAtLeast(edgePaddingPx)
    }
    val mainLeftPx = desiredMainLeftPx.coerceIn(minMainLeftPx, maxMainLeftPx)

    val groupStartPx = if (isCircleLeft && hasCompanion) mainLeftPx - compactGapPx - circleSizePx else mainLeftPx
    val groupEndPx = if (!isCircleLeft && hasCompanion) {
        mainLeftPx + mainWidthPx + compactGapPx + circleSizePx
    } else {
        mainLeftPx + mainWidthPx
    }
    val groupCenterPx = (groupStartPx + groupEndPx) / 2f

    // The window is gravity TOP | CENTER_HORIZONTAL, so x is an offset from the
    // screen centre rather than an absolute left edge.
    val widthPx = (groupWidthPx + 2f * horizontalPaddingDp * density).toInt()
    val xPx = (groupCenterPx - screenWidthPx / 2f).toInt()

    return CollapsedWindowGeometry(
        widthPx = widthPx,
        heightPx = ((settings.height + COLLAPSED_WINDOW_VERTICAL_PADDING_DP) * density).toInt(),
        xPx = xPx,
        yPx = if (settings.enableNotchMode) 0 else (settings.yOffset * density).toInt()
    )
}

/** Gap between the pill and its companion circle. Mirrors COMPACT_INDICATOR_GAP_DP. */
private const val COMPACT_GAP_DP = 8f

/** Minimum distance kept between the group and the screen edge. */
private const val EDGE_PADDING_DP = 8f

/**
 * Horizontal margin around the group, per side.
 *
 * Must exceed the collapsed drag clamp of 24dp so the pill is never clipped by
 * the window edge mid-drag, plus a little slack.
 */
private const val COLLAPSED_WINDOW_PADDING_DP = 28f

/** Vertical margin, so the pill's shadow and elevation are not clipped. */
private const val COLLAPSED_WINDOW_VERTICAL_PADDING_DP = 16f

internal fun selectOverlayRefreshRate(rates: List<Float>): Float =
    rates.filter { it.isFinite() && it > 0f && it <= 120.5f }.maxOrNull()?.coerceAtMost(120f) ?: 0f

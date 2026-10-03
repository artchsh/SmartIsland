/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.service

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
import com.agupta07505.smartisland.MainActivity
import com.agupta07505.smartisland.R
import com.agupta07505.smartisland.data.INotificationRepository
import com.agupta07505.smartisland.data.SmartIslandCommand
import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.data.SmartIslandSettingsRepository
import com.agupta07505.smartisland.model.IslandNotification
import com.agupta07505.smartisland.ui.IslandViewModel
import com.agupta07505.smartisland.ui.ThemedOverlayIsland
import com.agupta07505.smartisland.ui.expanded.sendIntentWithOptions
import com.agupta07505.smartisland.util.runCatchingLogged
import com.agupta07505.smartisland.util.runSuspendCatchingLogged
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
class SmartIslandOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    @Inject lateinit var repository: SmartIslandSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    private var islandView: ComposeView? = null
    private val overlayOwners = OverlayViewTreeOwners()
    private lateinit var systemEventReceiver: SystemEventReceiver
    private lateinit var viewModel: IslandViewModel
    private var isLockScreenActive: Boolean = false
    private var systemEventReceiverRegistered = false
    private var screenStateReceiverRegistered = false
    private var torchCallbackRegistered = false
    private var foregroundStarted = false
    @Volatile private var destroyed = false
    private var isWindowExpanded: Boolean = false
    private var collapseJob: kotlinx.coroutines.Job? = null
    private var lastParams: WindowManager.LayoutParams? = null

    private val torchCallback = object : android.hardware.camera2.CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (destroyed || !::viewModel.isInitialized) return
            if (enabled) {
                notificationRepository.postNotification(
                    IslandNotification(
                        key = "system_flashlight",
                        packageName = "com.android.systemui",
                        appName = "Flashlight",
                        title = "Flashlight ON",
                        text = "Tap to turn off",
                        mode = com.agupta07505.smartisland.model.IslandMode.Flashlight,
                        timeMillis = System.currentTimeMillis(),
                        actionIntents = listOf(
                            com.agupta07505.smartisland.model.IslandNotificationAction("Turn Off", null)
                        )
                    ),
                    autoExpand = true
                )
            } else {
                notificationRepository.removeNotification("system_flashlight")
            }
        }
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
                    openedPackage != packageName &&
                    openedPackage != "com.android.systemui"
                ) {
                    viewModel.foregroundPackage.value = openedPackage
                    val hasNonMusicNotifications = notificationRepository.notifications.value.any {
                        it.packageName == openedPackage && it.mode != com.agupta07505.smartisland.model.IslandMode.Music
                    }
                    if (hasNonMusicNotifications) {
                        notificationRepository.removeNotificationsForPackage(openedPackage)
                    }
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
        
        systemEventReceiver = SystemEventReceiver(notificationRepository, settingsProvider = { viewModel.settings.value })
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        
        // CRASH FIX: Android 13+/14+ requires explicit export flag for system broadcasts
        runCatchingLogged(TAG, "registerReceiver failed") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(systemEventReceiver, filter)
            }
            systemEventReceiverRegistered = true
        }

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

        runCatchingLogged(TAG, "registerTorchCallback failed") {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            cameraManager?.registerTorchCallback(torchCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            torchCallbackRegistered = true
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
                    collapseJob?.cancel()
                    if (expanded) {
                        isWindowExpanded = true
                        updateWindowLayoutParams(true, viewModel.settings.value)
                    } else {
                        collapseJob = serviceScope.launch {
                            kotlinx.coroutines.delay(AUTO_COLLAPSE_DELAY_MS)
                            isWindowExpanded = false
                            updateWindowLayoutParams(false, viewModel.settings.value)
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Notifications-state collector failed") {
                viewModel.notifications.collectLatest {
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

        if (::systemEventReceiver.isInitialized && systemEventReceiverRegistered) {
            runCatchingLogged(TAG, "unregisterReceiver failed") {
                unregisterReceiver(systemEventReceiver)
            }
            systemEventReceiverRegistered = false
        }
        if (screenStateReceiverRegistered) {
            runCatchingLogged(TAG, "unregisterReceiver screenStateReceiver failed") {
                unregisterReceiver(screenStateReceiver)
            }
            screenStateReceiverRegistered = false
        }
        if (torchCallbackRegistered) {
            runCatchingLogged(TAG, "unregisterTorchCallback failed") {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                cameraManager?.unregisterTorchCallback(torchCallback)
            }
            torchCallbackRegistered = false
        }

        removeCollapsedWindow()
        stopForegroundSafely()
        runCatchingLogged(TAG, "Overlay owners destroy failed") {
            overlayOwners.destroy()
        }
        super.onDestroy()
    }

    private fun startOverlaySession(settings: SmartIslandSettings) {
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
                setContent {
                    // isFullWidth tracks the window geometry directly: the window
                    // is MATCH_PARENT only while the card is expanded, and pill-sized
                    // when collapsed. Previously this came from an
                    // isTouchableRegionSupported StateFlow, which existed only to
                    // report whether the hidden touchable-region reflection had
                    // succeeded. With that gone the window is always pill-sized when
                    // collapsed, so the flag is simply "are we expanded".
                    val expandedNow by viewModel.expanded.collectAsStateWithLifecycle(
                        minActiveState = Lifecycle.State.RESUMED
                    )
                    ThemedOverlayIsland(
                        viewModel = this@SmartIslandOverlayService.viewModel,
                        statusBarHeight = statusBarHeight,
                        onOpenNotification = { notification -> openNotification(notification) },
                        onLaunchApp = { packageName -> launchApp(packageName) },
                        onOpenFloatingWindow = { openCurrentNotificationInFloatingWindow() },
                        onOpenNotificationShade = {
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                            viewModel.collapse()
                        },
                        isFullWidth = expandedNow
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
    // being MATCH_PARENT. It required the hidden-API exemption in SmartIslandApp,
    // which is blocked on Android 14+ (so it had already stopped working on the
    // fork's target OS) and is a Play policy violation.
    //
    // The capability is replaced by FLAG_NOT_TOUCH_MODAL, which this window has
    // always set: touches landing outside a window's bounds go to the window
    // behind it. So the collapsed window is now simply sized to the pill plus a
    // margin, rather than spanning the display and then carving a hole in it.
    // See AUDIT.md section 4.1.

    private fun updateWindowLayoutParams(expanded: Boolean, settings: SmartIslandSettings) {
        if (destroyed || !::windowManager.isInitialized || !::viewModel.isInitialized) return
        val view = islandView ?: return
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        isLockScreenActive = isLocked
        viewModel.isLocked.value = isLocked

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val isIdleHidden = settings.hideWhenIdle && viewModel.notifications.value.isEmpty() && !settings.enableAppShortcuts
        val isHidden = (!settings.showOnLockScreen && isLocked) || (isLandscape && !settings.showInLandscape) || isIdleHidden

        val targetVisibility = if (isHidden) android.view.View.GONE else android.view.View.VISIBLE
        if (view.visibility != targetVisibility) {
            view.visibility = targetVisibility
        }

        // Collapsed: size the window to the pill. Expanded: span the display so
        // the card can be full-bleed and so taps outside collapse it.
        val geometry = computeCollapsedWindowGeometry(
            settings = settings,
            notificationCount = viewModel.notifications.value.size,
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

    private fun removeCollapsedWindow() {
        val view = islandView ?: return
        // Clear the reference before removal so repeated teardown calls are harmless,
        // even when an OEM WindowManager throws while detaching an already-removed view.
        islandView = null
        lastParams = null
        isWindowExpanded = false
        collapseJob?.cancel()
        collapseJob = null
        if (!::windowManager.isInitialized) return
        runCatchingLogged(TAG, "Failed to remove view") {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        }
    }

    private fun collapsedParams(settings: SmartIslandSettings): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()
        val geometry = computeCollapsedWindowGeometry(
            settings = settings,
            notificationCount = if (::viewModel.isInitialized) viewModel.notifications.value.size else 0,
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
                when (notification.mode) {
                    com.agupta07505.smartisland.model.IslandMode.Bluetooth -> {
                        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    com.agupta07505.smartisland.model.IslandMode.Battery -> {
                        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else {
                            val altIntent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(altIntent)
                        }
                    }
                    com.agupta07505.smartisland.model.IslandMode.Hotspot -> {
                        val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    com.agupta07505.smartisland.model.IslandMode.Timer,
                    com.agupta07505.smartisland.model.IslandMode.Stopwatch -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                            ?: Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        if (launchIntent.resolveActivity(packageManager) != null) {
                            startActivity(launchIntent)
                        } else {
                            val clockIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(clockIntent)
                        }
                    }
                    else -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        } else {
                            Toast.makeText(this, "Opening ${notification.appName} (Demo)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        if (notification.mode != com.agupta07505.smartisland.model.IslandMode.Music) {
            notificationRepository.removeNotificationsForPackage(notification.packageName)
        }
        viewModel.collapse()
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatchingLogged(TAG, "Failed to launch shortcut app") {
            startActivity(launchIntent)
        }
        notificationRepository.removeNotificationsForPackage(packageName)
        viewModel.collapse()
    }

    private fun openCurrentNotificationInFloatingWindow() {
        val list = viewModel.notifications.value
        val index = viewModel.selectedIndex.value
        if (list.isNotEmpty() && index in list.indices) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Toast.makeText(this, "Floating window requires Android 7+.", Toast.LENGTH_SHORT).show()
                viewModel.collapse()
                return
            }
            val notification = list[index]
            val options = ActivityOptions.makeBasic()
            runCatchingLogged(TAG, "Failed to set launch bounds") {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                val w = (screenWidth * 0.90f).toInt()
                val h = (screenHeight * 0.65f).toInt()
                val left = (screenWidth - w) / 2
                val top = (screenHeight - h) / 2
                options.setLaunchBounds(android.graphics.Rect(left, top, left + w, top + h))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatchingLogged(TAG, "Failed to set background activity start mode") {
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }
            val bundle = options.toBundle() ?: android.os.Bundle()
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM)

            val fillInIntent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }

            if (notification.contentIntent != null) {
                runCatchingLogged(TAG, "Failed to send content intent") {
                    notification.contentIntent.send(this, 0, fillInIntent, null, null, null, bundle)
                }
            } else {
                runCatchingLogged(TAG, "Failed to launch package activity") {
                    val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        startActivity(launchIntent, bundle)
                    } else {
                        Toast.makeText(this, "Opening ${notification.appName} in floating window (Demo)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            notificationRepository.removeNotification(notification.key)
            notificationRepository.sendCommand(SmartIslandCommand.CancelNotification(notification.key))
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
                description = "Keeps the Smart Island overlay running"
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
            Intent(this, com.agupta07505.smartisland.MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle("Smart Island is active")
            .setContentText("Tap to open Smart Island")
            .setSmallIcon(R.drawable.ic_stat_smart_island)
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

        private const val TAG = "SmartIslandOverlayService"
        private const val NOTIFICATION_ID = 8105
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val OVERLAY_CHANNEL_ID = "smart_island_overlay"
        private const val OVERLAY_CHANNEL_NAME = "Smart Island overlay"
        private const val AUTO_COLLAPSE_DELAY_MS = 220L
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
    settings: SmartIslandSettings,
    notificationCount: Int,
    density: Float,
    screenWidthPx: Float,
    horizontalPaddingDp: Float = COLLAPSED_WINDOW_PADDING_DP
): CollapsedWindowGeometry {
    val hasCompanion = if (settings.enableNotchMode) false else notificationCount >= 2
    val isCircleLeft = settings.circlePosition == SmartIslandSettings.CIRCLE_POSITION_LEFT

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

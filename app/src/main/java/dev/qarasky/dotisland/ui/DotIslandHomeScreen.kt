/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandPublisherStore
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.service.DotIslandNotificationListenerService
import dev.qarasky.dotisland.service.DotIslandOverlayService
import dev.qarasky.dotisland.util.PersonalActivityPolicy
import dev.qarasky.dotisland.util.SystemServiceRecovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DotIslandHomeScreen(
    repository: DotIslandSettingsRepository,
    notificationRepository: INotificationRepository,
    publisherStore: DotIslandPublisherStore
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(DotIslandSettings.Default)
    val activities by notificationRepository.notifications.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(readAccess(context)) }
    var connected by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = readAccess(context)
                SystemServiceRecovery.requestRecovery(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(owner) {
        // Visible screen only; service status is not confused with permission grant.
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                connected = DotIslandOverlayService.isSystemConnected && DotIslandNotificationListenerService.isSystemConnected
                delay(1000)
            }
        }
    }
    val status = when {
        !access.accessibility || !access.notifications -> "SETUP REQUIRED"
        !settings.enabled -> "OFF"
        !connected -> "CONNECTING"
        else -> "ACTIVE"
    }
    Surface(color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("DOT", fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Color(0xFF8E8E93))
                    Text("Island", fontSize = 42.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(status, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFB8B8BD))
            }
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF111111), border = BorderStroke(1.dp, Color(0xFF262626))) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Your island", fontSize = 21.sp, fontWeight = FontWeight.Medium)
                            Text("Tap to open · Hold to expand", color = Color(0xFF8E8E93), fontSize = 12.sp)
                        }
                        Switch(settings.enabled, onCheckedChange = {
                            scope.launch { repository.setEnabled(it) }
                            if (it) SystemServiceRecovery.requestRecovery(context)
                        })
                    }
                    // Same compact renderer and activity snapshot as the real overlay;
                    // preview never inserts demo notifications or changes playback.
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(50), color = Color.Black, border = BorderStroke(1.dp, ISLAND_BORDER_COLOR)) {
                            val active = activities.firstOrNull()
                            IslandCollapsedContent(active?.mode ?: dev.qarasky.dotisland.model.IslandMode.Empty,
                                active, 1f, settings, Modifier.width(settings.width.dp).height(settings.height.dp),
                                showIdleIndicator = active == null)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                MicroLabel("ACTIVITIES")
                SourceRow("Spotify", PersonalActivityPolicy.SPOTIFY, access.notifications)
                SourceRow("Dodo Pizza", PersonalActivityPolicy.DODO, access.notifications, unverified = true)
                SourceRow("Calls", PersonalActivityPolicy.DIALER, access.notifications)
            }
            PublisherAccessSection(publisherStore)
            if (!access.accessibility || !access.notifications || !access.batteryExempt) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MicroLabel("DEVICE ACCESS")
                    if (!access.accessibility) RepairButton("Enable accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    if (!access.notifications) RepairButton("Allow notification access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    if (!access.batteryExempt) OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"))) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Allow background operation") }
                }
            }
            HorizontalDivider(color = Color(0xFF262626))
            Text("Only ongoing activities.\nYour notifications stay where they belong.",
                color = Color(0xFF8E8E93), fontSize = 13.sp, lineHeight = 20.sp)
            MicroLabel("A069 / ANDROID 16 / PERSONAL BUILD")
        }
    }
}

@Composable private fun MicroLabel(text: String) {
    Text(text, color = Color(0xFF8E8E93), fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
}

@Composable private fun SourceRow(name: String, packageName: String, notificationAccess: Boolean, unverified: Boolean = false) {
    val context = LocalContext.current
    val installed = remember(packageName) { runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StandbyDots(Modifier.size(14.dp))
        Text(name, Modifier.padding(start = 14.dp).weight(1f), fontSize = 17.sp)
        Text(when { !installed -> "NOT INSTALLED"; !notificationAccess -> "ACCESS REQUIRED"; unverified -> "UNVERIFIED"; else -> "READY" },
            color = Color(0xFF8E8E93), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable private fun RepairButton(label: String, action: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(action)) } }, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

/**
 * Manages which apps may publish to the island. Kept as a plain package-name list
 * rather than an installed-app picker: `com.android.shell` is allowed by default,
 * so adb and Termux work with no setup, and anything else is added deliberately.
 */
@Composable
private fun PublisherAccessSection(store: DotIslandPublisherStore) {
    val scope = rememberCoroutineScope()
    val allowed by store.allowedPackages.collectAsStateWithLifecycle(emptySet())
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MicroLabel("PUBLISHERS")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF111111),
            border = BorderStroke(1.dp, Color(0xFF262626)),
            modifier = Modifier.fillMaxWidth().bounceClick { open = true }
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Allowed to publish", fontSize = 16.sp)
                Text(
                    if (allowed.isEmpty()) "None" else "${allowed.size} allowed",
                    color = Color(0xFF8E8E93), fontFamily = FontFamily.Monospace, fontSize = 10.sp
                )
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Color(0xFF111111),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB8B8BD),
            title = { Text("Publishers", fontSize = 19.sp) },
            text = {
                var entry by remember { mutableStateOf("") }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Apps publish by broadcast. Their identity is read from the system, so they " +
                            "cannot claim another app's name.",
                        fontSize = 12.sp, lineHeight = 17.sp
                    )
                    allowed.sorted().forEach { pkg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pkg, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text(
                                "Remove",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                modifier = Modifier.bounceClick { scope.launch { store.setAllowed(pkg, false) } }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = entry,
                        onValueChange = { entry = it },
                        label = { Text("Package name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = entry.isNotBlank(),
                        onClick = {
                            val pkg = entry.trim()
                            scope.launch {
                                store.setAllowed(pkg, true)
                                entry = ""
                            }
                        }
                    ) { Text("Add") }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } }
        )
    }
}

private data class DeviceAccess(val accessibility: Boolean, val notifications: Boolean, val batteryExempt: Boolean)
private fun readAccess(context: android.content.Context): DeviceAccess {
    fun granted(key: String, component: ComponentName): Boolean = Settings.Secure.getString(context.contentResolver, key)
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    return DeviceAccess(
        granted(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentName(context, DotIslandOverlayService::class.java)),
        granted("enabled_notification_listeners", ComponentName(context, DotIslandNotificationListenerService::class.java)),
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    )
}

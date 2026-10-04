/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandCommand
import dev.qarasky.dotisland.data.DotIslandSettings
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.util.runSuspendCatchingLogged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class IslandViewModel(
    private val settingsRepo: DotIslandSettingsRepository,
    val notificationRepo: INotificationRepository
) : ViewModel() {

    val settings = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DotIslandSettings.Default
    )

    val notifications = notificationRepo.notifications
    // Do not label foreground-suppressed music as idle, or subscribe the overlay
    // to every source metadata update merely to distinguish these two states.
    val hasSourceActivity = notifications.map { it.isNotEmpty() }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = notifications.value.isNotEmpty()
    )
    val foregroundPackage = MutableStateFlow<String?>(null)

    val visibleNotifications: StateFlow<List<IslandNotification>> = combine(
        notifications,
        foregroundPackage,
        settings
    ) { list, fgPkg, s ->
        list.filterNot { notif ->
            !fgPkg.isNullOrEmpty() && notif.mode == IslandMode.Music && notif.packageName == fgPkg
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val expanded = MutableStateFlow(false)
    val selectedIndex = MutableStateFlow(0)
    val isLocked = MutableStateFlow(false)
    val isInputActive = MutableStateFlow(false)

    val mode: StateFlow<IslandMode> = combine(visibleNotifications, selectedIndex) { list, idx ->
        list.getOrNull(idx)?.mode ?: IslandMode.Empty
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = IslandMode.Empty
    )

    private var autoCollapseJob: Job? = null

    init {
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Notifications collector failed") {
                visibleNotifications.collect { list ->
                    selectedIndex.update { currentSelected ->
                        currentSelected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                    }
                    if (list.isEmpty()) collapse()
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Auto-expand collector failed") {
                notificationRepo.autoExpandEvent.collect { key ->
                    val notif = notifications.value.firstOrNull { it.key == key } ?: return@collect
                    val isFgMusic = notif.mode == IslandMode.Music &&
                        !foregroundPackage.value.isNullOrEmpty() &&
                        notif.packageName == foregroundPackage.value
                    if (isFgMusic) return@collect

                    val index = notifications.value.indexOfFirst { it.key == key }
                    if (index >= 0) {
                        selectedIndex.value = index
                        if (settings.value.autoExpandOnNotification) {
                            expand()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Timer-reset collector failed") {
                notificationRepo.resetTimerEvent.collect {
                    resetAutoCollapseTimer()
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Expanded-state collector failed") {
                expanded.collect { isExpanded ->
                    if (isExpanded) {
                        if (!isInputActive.value) {
                            startAutoCollapseTimer()
                        }
                    } else {
                        stopAutoCollapseTimer()
                        isInputActive.value = false
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Input-active collector failed") {
                isInputActive.collect { active ->
                    if (active) {
                        stopAutoCollapseTimer()
                    } else if (expanded.value) {
                        startAutoCollapseTimer()
                    }
                }
            }
        }
    }

    fun expand() {
        if (visibleNotifications.value.isNotEmpty()) expanded.value = true
    }

    fun collapse() {
        isInputActive.value = false
        expanded.value = false
    }

    fun setInputActive(active: Boolean) {
        if (isInputActive.value != active) {
            isInputActive.value = active
        }
    }

    private var lastToggleTimeMs = 0L

    fun toggleExpanded() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimeMs < 350L) return
        lastToggleTimeMs = now
        if (expanded.value) collapse() else expand()
    }

    private fun startAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        if (isInputActive.value) return
        autoCollapseJob = viewModelScope.launch {
            delay(AUTO_COLLAPSE_DELAY_MS)
            collapse()
        }
    }

    private fun stopAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
    }

    fun resetAutoCollapseTimer() {
        if (expanded.value) {
            startAutoCollapseTimer()
        }
    }

    fun setSelectedNotificationIndex(index: Int) {
        val list = visibleNotifications.value
        if (index in list.indices) {
            selectedIndex.value = index
            resetAutoCollapseTimer()
        }
    }

    fun dismissCurrentNotification() {
        val list = visibleNotifications.value
        val index = selectedIndex.value
        if (index in list.indices) {
            val notification = list[index]
            notificationRepo.removeNotification(notification.key)
            notificationRepo.sendCommand(DotIslandCommand.CancelNotification(notification.key))
        }
        collapse()
    }

    fun dismissAllNotifications() {
        val list = visibleNotifications.value
        for (notification in list) {
            notificationRepo.sendCommand(DotIslandCommand.CancelNotification(notification.key))
        }
        notificationRepo.removeAllNotifications()
        collapse()
    }

    companion object {
        private const val TAG = "IslandViewModel"
        private const val AUTO_COLLAPSE_DELAY_MS = 5000L

        fun provideFactory(
            settingsRepo: DotIslandSettingsRepository,
            notificationRepo: INotificationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return IslandViewModel(settingsRepo, notificationRepo) as T
            }
        }
    }
}

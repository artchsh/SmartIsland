/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.data

import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import kotlinx.coroutines.flow.*

class DotIslandNotificationRepository : INotificationRepository {
    private val items = MutableStateFlow<List<IslandNotification>>(emptyList())
    override val notifications = items.asStateFlow()
    private val expansions = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val autoExpandEvent = expansions.asSharedFlow()
    private val timerResets = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val resetTimerEvent = timerResets.asSharedFlow()
    private val actions = MutableSharedFlow<DotIslandCommand>(extraBufferCapacity = 16)
    override val commands = actions.asSharedFlow()

    override fun postNotification(notification: IslandNotification, autoExpand: Boolean) {
        if (notification.mode == IslandMode.Empty) return
        val isNew = items.value.none { it.key == notification.key }
        items.update { list ->
            // Occupancy slot: a mode owns one island slot, but a published
            // activity owns one slot per publisher so two publishers coexist.
            (list.filterNot { it.occupiesSameSlotAs(notification) } + notification)
                .sortedBy { it.sortRank }
        }
        if (autoExpand && isNew) expansions.tryEmit(notification.key)
    }
    override fun removeNotification(key: String) { items.update { it.filterNot { item -> item.key == key } } }
    override fun removeNotificationsForPackage(packageName: String) { items.update { it.filterNot { item -> item.packageName == packageName } } }

    /**
     * Drops published activities for any publisher not in [keepPublishers].
     * Used after a removal so an expired or dismissed activity cannot linger in
     * the overlay while its stored copy is already gone.
     */
    override fun removeAllPublished(keepPublishers: Set<String>) {
        items.update { list ->
            list.filterNot { it.mode == IslandMode.Published && it.packageName !in keepPublishers }
        }
    }
    override fun removeAllNotifications() { items.value = emptyList() }
    override fun resetTimer() { timerResets.tryEmit(Unit) }
    override fun sendCommand(command: DotIslandCommand) { actions.tryEmit(command) }
}

/** Two notifications share a slot if they would compete for the same island entry. */
internal fun IslandNotification.occupiesSameSlotAs(other: IslandNotification): Boolean = when {
    other.mode == IslandMode.Published -> mode == IslandMode.Published && packageName == other.packageName
    else -> mode == other.mode
}

private val IslandNotification.sortRank: Int
    get() = when (mode) {
        IslandMode.IncomingCall -> 0
        IslandMode.LiveActivity -> 1
        IslandMode.Music -> 2
        IslandMode.Published -> 3
        IslandMode.Empty -> 4
    }

sealed interface DotIslandCommand {
    data class CancelNotification(val key: String) : DotIslandCommand
    data class SeekTo(val packageName: String, val positionMs: Long) : DotIslandCommand
    data class SkipNext(val packageName: String? = null) : DotIslandCommand
    data class SkipPrevious(val packageName: String? = null) : DotIslandCommand
    data class PlayPause(val packageName: String? = null) : DotIslandCommand
}

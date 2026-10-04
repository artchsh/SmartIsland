/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.data

import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface INotificationRepository {
    val notifications: StateFlow<List<IslandNotification>>
    val autoExpandEvent: SharedFlow<String>
    val resetTimerEvent: SharedFlow<Unit>
    val commands: SharedFlow<DotIslandCommand>

    fun postNotification(notification: IslandNotification, autoExpand: Boolean = false)
    fun removeNotification(key: String)
    fun removeNotificationsForPackage(packageName: String)
    fun removeAllPublished(keepPublishers: Set<String>)
    fun removeAllNotifications()
    fun resetTimer()
    fun sendCommand(command: DotIslandCommand)
}

package dev.qarasky.dotisland.data

import dev.qarasky.dotisland.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DotIslandNotificationRepositoryTest {
    private fun item(mode: IslandMode, key: String = mode.name) = IslandNotification(key, mode.name, mode.name, "Title", "Text", 0L, mode = mode)
    private fun published(packageName: String, id: String) =
        IslandNotification(id, packageName, packageName, "Published", "", 0L, mode = IslandMode.Published)
    @Test fun callsTakePriorityAndOnlyThreeActivitiesAreRetained() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(item(IslandMode.Music))
        repo.postNotification(item(IslandMode.LiveActivity))
        repo.postNotification(item(IslandMode.IncomingCall))
        assertEquals(listOf(IslandMode.IncomingCall, IslandMode.LiveActivity, IslandMode.Music), repo.notifications.value.map { it.mode })
        repo.postNotification(item(IslandMode.IncomingCall, "telecom_update"))
        assertEquals(3, repo.notifications.value.size)
        assertEquals("telecom_update", repo.notifications.value.first().key)
    }
    @Test fun updatesReplaceRatherThanDuplicateAndRemovalIsLocal() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(item(IslandMode.Music))
        repo.postNotification(item(IslandMode.Music).copy(title = "New song"))
        assertEquals(1, repo.notifications.value.size)
        assertEquals("New song", repo.notifications.value.single().title)
        repo.removeNotification(IslandMode.Music.name)
        assertTrue(repo.notifications.value.isEmpty())
    }
    @Test fun emptyModeCannotCreateAnActivity() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(item(IslandMode.Empty))
        assertTrue(repo.notifications.value.isEmpty())
    }
    @Test fun twoPublishersCoexistAndEachKeepsItsOwnSlot() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(published("com.tasker", "a:1"))
        repo.postNotification(published("com.termux", "b:1"))
        assertEquals(2, repo.notifications.value.size)
        repo.postNotification(published("com.tasker", "a:1").copy(title = "Updated"))
        val after = repo.notifications.value
        assertEquals(2, after.size)
        assertEquals("Updated", after.single { it.packageName == "com.tasker" }.title)
    }
    @Test fun publishedActivitiesRankBelowSystemActivities() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(published("com.tasker", "a:1"))
        repo.postNotification(item(IslandMode.Music))
        assertEquals(listOf(IslandMode.Music, IslandMode.Published), repo.notifications.value.map { it.mode })
    }
    @Test fun removeAllPublishedKeepsOnlySurvivingPublishers() {
        val repo = DotIslandNotificationRepository()
        repo.postNotification(published("com.tasker", "a:1"))
        repo.postNotification(published("com.termux", "b:1"))
        repo.postNotification(item(IslandMode.Music))
        repo.removeAllPublished(setOf("com.termux"))
        assertEquals(listOf(IslandMode.Music, IslandMode.Published), repo.notifications.value.map { it.mode })
        assertEquals("com.termux", repo.notifications.value.last().packageName)
        repo.removeAllPublished(emptySet())
        assertEquals(listOf(IslandMode.Music), repo.notifications.value.map { it.mode })
    }
    @Test fun controlsAndTimerResetEventsRemainAvailable() = runTest {
        val repo = DotIslandNotificationRepository()
        val commands = mutableListOf<DotIslandCommand>()
        var resets = 0
        backgroundScope.launch { repo.commands.collect { commands.add(it) } }
        backgroundScope.launch { repo.resetTimerEvent.collect { resets++ } }
        runCurrent()
        repo.sendCommand(DotIslandCommand.SeekTo("com.spotify.music", 12000))
        repo.resetTimer()
        runCurrent()
        assertEquals(1, commands.size)
        assertEquals(1, resets)
    }
}

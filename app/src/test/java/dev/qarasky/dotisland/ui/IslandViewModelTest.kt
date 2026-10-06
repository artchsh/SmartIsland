package dev.qarasky.dotisland.ui

import dev.qarasky.dotisland.data.*
import dev.qarasky.dotisland.model.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IslandViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun viewModel(repo: INotificationRepository): IslandViewModel {
        val settings = mockk<DotIslandSettingsRepository>()
        every { settings.settings } returns MutableStateFlow(DotIslandSettings(enabled = true))
        return IslandViewModel(settings, repo)
    }
    private fun music() = IslandNotification("spotify", "com.spotify.music", "Spotify", "Song", "Artist", 0L, mode = IslandMode.Music)
    @Test fun idleCannotExpand() = runTest {
        val vm = viewModel(DotIslandNotificationRepository())
        runCurrent()
        vm.expand()
        assertFalse(vm.expanded.value)
    }
    @Test fun musicStaysVisibleInsideItsOwnApp() = runTest {
        val repo = DotIslandNotificationRepository()
        val vm = viewModel(repo)
        repo.postNotification(music())
        runCurrent()
        assertEquals(1, vm.visibleNotifications.value.size)
        // Inside Spotify with music playing: the pill keeps showing music,
        // never a blank pill that is neither music nor idle.
        vm.foregroundPackage.value = "com.spotify.music"
        runCurrent()
        assertEquals(1, vm.visibleNotifications.value.size)
        assertEquals(IslandMode.Music, vm.mode.value)
        assertTrue(vm.hasSourceActivity.value)
        // Leaving the app changes nothing about visibility either.
        vm.foregroundPackage.value = "com.nothing.launcher"
        runCurrent()
        assertEquals(1, vm.visibleNotifications.value.size)
    }
    @Test fun lossOfLastActivityCollapsesAndNewActivityDoesNotAutoExpand() = runTest {
        val repo = DotIslandNotificationRepository()
        val vm = viewModel(repo)
        repo.postNotification(music(), autoExpand = true)
        runCurrent()
        assertFalse(vm.expanded.value)
        vm.expand()
        assertTrue(vm.expanded.value)
        repo.removeAllNotifications()
        runCurrent()
        assertFalse(vm.expanded.value)
    }
}

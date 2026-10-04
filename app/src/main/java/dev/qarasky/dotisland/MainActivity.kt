/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandPublisherStore
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.ui.DotIslandHomeScreen
import dev.qarasky.dotisland.ui.DotIslandTheme
import dev.qarasky.dotisland.util.SystemServiceRecovery
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: DotIslandSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    @Inject lateinit var publisherStore: DotIslandPublisherStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemServiceRecovery.requestRecovery(this)

        setContent {
            DotIslandTheme {
                DotIslandHomeScreen(
                    repository = settingsRepository,
                    notificationRepository = notificationRepository,
                    publisherStore = publisherStore
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SystemServiceRecovery.requestRecovery(this)
    }
}

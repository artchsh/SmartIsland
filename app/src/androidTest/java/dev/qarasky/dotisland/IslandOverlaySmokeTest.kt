/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.platform.LocalContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.ui.IslandCollapsedContent
import dev.qarasky.dotisland.ui.DotIslandTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IslandOverlaySmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun personal_screen_has_only_one_switch_and_no_old_feature_panels() {
        compose.setContent {
            val context = LocalContext.current
            DotIslandTheme {
                dev.qarasky.dotisland.ui.DotIslandHomeScreen(
                    dev.qarasky.dotisland.data.DotIslandSettingsRepository(context),
                    dev.qarasky.dotisland.data.DotIslandNotificationRepository(),
                    dev.qarasky.dotisland.data.DotIslandPublisherStore(context)
                )
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("Spotify").assertExists()
        compose.onNodeWithText("Dodo Pizza").assertExists()
        compose.onNodeWithText("Calls").assertExists()
        compose.onNodeWithText("UNVERIFIED").assertExists()
        compose.onNodeWithText("Color Studio").assertDoesNotExist()
        compose.onNodeWithText("Notification History").assertDoesNotExist()
    }

    @Test
    fun collapsed_renders_idle() {
        compose.setContent {
            DotIslandTheme {
                IslandCollapsedContent(
                    mode = IslandMode.Empty,
                    notification = null,
                    collapsedAlpha = 1f,
                    settings = dev.qarasky.dotisland.data.DotIslandSettings.Default,
                    showIdleIndicator = true
                )
            }
        }
        compose.onNodeWithText("IDLE").assertExists()
    }
}

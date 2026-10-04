/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.util.SystemServiceRecovery
import dev.qarasky.dotisland.util.runCatchingLogged
import dev.qarasky.dotisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutostartReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: DotIslandSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        runCatchingLogged("AutostartReceiver", "Autostart broadcast callback failed") {
            val action = intent.action ?: return@runCatchingLogged
            if (action != Intent.ACTION_BOOT_COMPLETED &&
                action != Intent.ACTION_MY_PACKAGE_REPLACED &&
                action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                action != Intent.ACTION_USER_PRESENT
            ) return@runCatchingLogged

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    runSuspendCatchingLogged(
                        "AutostartReceiver",
                        "Failed handling autostart broadcast"
                    ) {
                        val settings = settingsRepository.settings.first()
                        if (settings.enabled) SystemServiceRecovery.requestRecovery(context)
                    }
                } finally {
                    runCatchingLogged("AutostartReceiver", "goAsync finish failed") {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

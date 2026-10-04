/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.di

import android.content.Context
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DotIslandRepositoriesEntryPoint {
    fun settingsRepository(): DotIslandSettingsRepository
    fun notificationRepository(): INotificationRepository
}

object DotIslandRepositories {
    fun settingsRepository(context: Context): DotIslandSettingsRepository =
        entryPoint(context).settingsRepository()

    fun notificationRepository(context: Context): INotificationRepository =
        entryPoint(context).notificationRepository()

    private fun entryPoint(context: Context): DotIslandRepositoriesEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DotIslandRepositoriesEntryPoint::class.java
        )
}

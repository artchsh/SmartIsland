/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.di

import android.content.Context
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandNotificationRepository
import dev.qarasky.dotisland.data.DotIslandPublisherStore
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): DotIslandSettingsRepository = DotIslandSettingsRepository(context)

    @Provides
    @Singleton
    fun provideNotificationRepository(): INotificationRepository =
        DotIslandNotificationRepository()

    @Provides
    @Singleton
    fun providePublisherStore(
        @ApplicationContext context: Context
    ): DotIslandPublisherStore = DotIslandPublisherStore(context)

}

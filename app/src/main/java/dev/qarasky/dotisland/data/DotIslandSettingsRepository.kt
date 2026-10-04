/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.islandPower by preferencesDataStore(
    name = "dot_island_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class DotIslandSettingsRepository(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("enabled")
    val settings = context.islandPower.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { DotIslandSettings(enabled = it[enabledKey] ?: false) }

    suspend fun setEnabled(enabled: Boolean) {
        context.islandPower.edit {
            // No legacy configuration or backup format is carried forward.
            it.clear()
            it[enabledKey] = enabled
        }
    }
}

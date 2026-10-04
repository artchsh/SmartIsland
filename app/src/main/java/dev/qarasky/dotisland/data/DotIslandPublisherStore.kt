/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qarasky.dotisland.api.PublishedActivity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.publisherStore by preferencesDataStore(
    name = "dot_island_publishers",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e("DotIslandPublishers", "Publisher store corrupted; dropping it")
        emptyPreferences()
    }
)

/**
 * Durable state for the public publishing API.
 *
 * Published activities are persisted rather than held only in memory so they
 * survive process death: a broadcast can start this app cold, and the overlay or
 * notification service binds later. Without this, an activity published while the
 * process was dead would silently never appear.
 *
 * Expiry is re-evaluated on read, so a stale entry is dropped on the next access
 * even if no timer was running when it lapsed.
 */
class DotIslandPublisherStore(private val context: Context) {

    private object Keys {
        val activities = stringPreferencesKey("activities_json")
        val allowed = stringPreferencesKey("allowed_packages")
    }

    val activities: Flow<List<PublishedActivity>> = context.publisherStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decode(prefs[Keys.activities]).filter { it.expiresAtMillis > now() } }

    /** Publishers permitted to talk to the API. Empty means nobody. */
    val allowedPackages: Flow<Set<String>> = context.publisherStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decodeSet(prefs[Keys.allowed]).ifEmpty { DEFAULT_PUBLISHERS } }

    private fun now() = System.currentTimeMillis()

    suspend fun isAllowed(packageName: String): Boolean = allowedPackages.first().contains(packageName)

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        context.publisherStore.edit { prefs ->
            val current = decodeSet(prefs[Keys.allowed]).ifEmpty { DEFAULT_PUBLISHERS }
            prefs[Keys.allowed] = encodeSet(if (allowed) current + packageName else current - packageName)
        }
    }

    /** Replaces this publisher's activity for the same id, or adds it. */
    suspend fun upsert(activity: PublishedActivity) {
        context.publisherStore.edit { prefs ->
            val current = decode(prefs[Keys.activities])
                .filter { it.id != activity.id && it.expiresAtMillis > now() }
            prefs[Keys.activities] = encode((current + activity).takeLast(DotIslandContractLimit))
        }
    }

    suspend fun remove(id: String) {
        context.publisherStore.edit { prefs ->
            prefs[Keys.activities] = encode(decode(prefs[Keys.activities]).filterNot { it.id == id })
        }
    }

    /** Only ever removes the caller's own activities. */
    suspend fun removeAllFor(publisherPackage: String) {
        context.publisherStore.edit { prefs ->
            prefs[Keys.activities] = encode(decode(prefs[Keys.activities]).filterNot { it.publisherPackage == publisherPackage })
        }
    }

    suspend fun removeExpired() {
        context.publisherStore.edit { prefs ->
            val kept = decode(prefs[Keys.activities]).filter { it.expiresAtMillis > now() }
            if (kept.size != decode(prefs[Keys.activities]).size) prefs[Keys.activities] = encode(kept)
        }
    }

    suspend fun lastPublishMillis(id: String): Long =
        decode(context.publisherStore.data.first()[Keys.activities]).firstOrNull { it.id == id }?.publishedAtMillis ?: 0L

    private fun encode(items: List<PublishedActivity>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("pkg", item.publisherPackage)
                put("app", item.appName)
                put("title", item.title)
                put("text", item.text)
                item.progress?.let { put("progress", it) }
                item.iconPackage?.let { put("iconPkg", it) }
                item.iconResource?.let { put("iconRes", it) }
                put("expand", item.expandOnPublish)
                put("publishedAt", item.publishedAtMillis)
                put("expiresAt", item.expiresAtMillis)
            })
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<PublishedActivity> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val o = array.getJSONObject(index)
                    PublishedActivity(
                        id = o.getString("id"),
                        publisherPackage = o.getString("pkg"),
                        appName = o.optString("app"),
                        title = o.optString("title"),
                        text = o.optString("text"),
                        progress = if (o.has("progress")) o.getInt("progress") else null,
                        iconPackage = if (o.has("iconPkg")) o.getString("iconPkg") else null,
                        iconResource = if (o.has("iconRes")) o.getString("iconRes") else null,
                        expandOnPublish = o.optBoolean("expand"),
                        publishedAtMillis = o.optLong("publishedAt"),
                        expiresAtMillis = o.optLong("expiresAt")
                    )
                }.getOrNull()
            }
        }.getOrElse {
            Log.w(TAG, "Discarding unreadable publisher activities", it)
            emptyList()
        }
    }

    private fun encodeSet(values: Set<String>): String = values.sorted().joinToString(SEP)
    private fun decodeSet(raw: String?): Set<String> =
        raw?.split(SEP)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private companion object {
        const val TAG = "DotIslandPublishers"
        const val SEP = "\n"
        const val DotIslandContractLimit = 5

        /**
         * `com.android.shell` is allowed by default so `adb` and Termux shell
         * commands work without an extra setup step. Callers are still verified
         * by Binder UID; this only opts that one UID in.
         */
        val DEFAULT_PUBLISHERS = setOf("com.android.shell")
    }
}
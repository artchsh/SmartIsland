/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import dev.qarasky.dotisland.api.PublishedActivity
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification

/**
 * Turns a stored [PublishedActivity] into an island entry.
 *
 * The icon is resolved through PackageManager from a package + resource name, so a
 * publisher can hand over something scriptable from a shell instead of a Bitmap.
 * Anything unresolvable falls back to the publisher's own launcher icon, and then
 * to the generic glyph in the UI. A bad reference is never allowed to throw.
 */
internal fun publishedActivityToNotification(
    context: Context,
    activity: PublishedActivity,
    icon: Bitmap? = loadPublishedIcon(context, activity)
): IslandNotification = IslandNotification(
    key = activity.id,
    packageName = activity.publisherPackage,
    appName = activity.appName,
    title = activity.title,
    text = activity.text,
    timeMillis = activity.publishedAtMillis,
    icon = icon,
    largeIcon = icon,
    progress = activity.progress ?: 0,
    progressMax = if (activity.progress != null) 100 else 0,
    mode = IslandMode.Published
)

internal fun loadPublishedIcon(context: Context, activity: PublishedActivity): Bitmap? {
    activity.iconResource?.let { name ->
        activity.iconPackage?.let { pkg ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                val resources = context.packageManager.getResourcesForApplication(info)
                val id = resources.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: info.icon
                resources.getDrawable(id, null)?.toBitmap(96, 96)
            }.getOrNull()?.let { return it }
        }
    }
    return runCatching {
        context.packageManager.getApplicationIcon(activity.publisherPackage).toBitmap(96, 96)
    }.onFailure { Log.w(TAG, "No icon for ${activity.publisherPackage}", it) }.getOrNull()
}

private const val TAG = "DotIslandPublished"
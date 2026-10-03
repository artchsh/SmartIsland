/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats a timestamp as local 24-hour "HH:mm".
 *
 * SimpleDateFormat is not thread-safe and is comparatively expensive to build,
 * so a fresh instance per call allocated a formatter plus a Date on every
 * invocation. This is called from NotificationExpanded, DownloadExpanded and
 * HotspotExpanded, all of which sit behind recomposing overlays.
 *
 * ThreadLocal rather than a single shared instance because this is reachable
 * from the notification listener's Dispatchers.Default pool as well as the main
 * thread.
 */
private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

fun formatNotificationTime(timeMillis: Long): String {
    return timeFormatter.get()!!.format(Date(timeMillis))
}

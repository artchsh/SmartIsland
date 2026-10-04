/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.api

import android.content.Context
import android.content.Intent
import dev.qarasky.dotisland.data.DotIslandSettings

/**
 * Kotlin convenience wrapper over the publishing broadcasts.
 *
 * This is entirely optional: the actions are plain broadcasts, so Tasker, Termux
 * or a shell script can publish without this class. It exists so an app with a
 * compile-time dependency gets namespaced ids and typed arguments for free.
 *
 * ```kotlin
 * val island = DotIslandPublisher(context, "order-42")
 * island.publish("Order placed", "Arriving in 12 min", progress = 40)
 * island.update(text = "Courier assigned")
 * island.dismiss()
 * ```
 *
 * Every call is a fire-and-forget broadcast. Dot Island validates the caller by
 * Binder UID, so the namespace and label below cannot be forged by a third party.
 */
class DotIslandPublisher(context: Context, localId: String = DEFAULT_LOCAL_ID) {

    private val appContext = context.applicationContext

    /**
     * Namespaced to the *publisher*, never to Dot Island. The receiver validates
     * this prefix against the UID it sees, so a mismatch is rejected rather than
     * trusted — which is exactly why it must be derived from our own package.
     */
    val id = "${context.packageName}:$localId"

    fun publish(
        title: String,
        text: String = "",
        progress: Int? = null,
        timeoutMillis: Long? = null,
        expand: Boolean = false,
        iconPackage: String? = null,
        iconResource: String? = null,
        appName: String? = null
    ) = send(DotIslandContract.PUBLISH, id, title, text, progress, timeoutMillis, expand, appName, iconPackage, iconResource)

    /** Same entry, same slot; only the content changes. */
    fun update(
        title: String,
        text: String = "",
        progress: Int? = null,
        timeoutMillis: Long? = null,
        expand: Boolean = false
    ) = send(DotIslandContract.UPDATE, id, title, text, progress, timeoutMillis, expand, null, null, null)

    fun dismiss() {
        appContext.sendBroadcast(
            Intent(DotIslandContract.DISMISS)
                .setPackage(DotIslandSettings.PACKAGE_NAME)
                .putExtra(DotIslandContract.EXTRA_ID, id)
        )
    }

    /** Removes every activity this app published, not just [id]. */
    fun dismissAll() {
        appContext.sendBroadcast(Intent(DotIslandContract.DISMISS_ALL).setPackage(DotIslandSettings.PACKAGE_NAME))
    }

    private fun send(
        action: String,
        id: String,
        title: String,
        text: String,
        progress: Int?,
        timeoutMillis: Long?,
        expand: Boolean,
        appName: String?,
        iconPackage: String?,
        iconResource: String?
    ) {
        val intent = Intent(action)
            .setPackage(DotIslandSettings.PACKAGE_NAME)
            .putExtra(DotIslandContract.EXTRA_ID, id)
            .putExtra(DotIslandContract.EXTRA_TITLE, title)
            .putExtra(DotIslandContract.EXTRA_EXPAND, expand)
        if (text.isNotEmpty()) intent.putExtra(DotIslandContract.EXTRA_TEXT, text)
        progress?.let { intent.putExtra(DotIslandContract.EXTRA_PROGRESS, it) }
        timeoutMillis?.let { intent.putExtra(DotIslandContract.EXTRA_TIMEOUT_MS, it) }
        appName?.let { intent.putExtra(DotIslandContract.EXTRA_APP_NAME, it) }
        iconPackage?.let { intent.putExtra(DotIslandContract.EXTRA_ICON_PACKAGE, it) }
        iconResource?.let { intent.putExtra(DotIslandContract.EXTRA_ICON_RESOURCE, it) }
        appContext.sendBroadcast(intent)
    }

    companion object {
        const val DEFAULT_LOCAL_ID = "activity"
    }
}
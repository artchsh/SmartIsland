/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import dev.qarasky.dotisland.api.DotIslandContract
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_APP_NAME
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_EXPAND
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_ICON_PACKAGE
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_ICON_RESOURCE
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_ID
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_PROGRESS
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_TEXT
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_TIMEOUT_MS
import dev.qarasky.dotisland.api.DotIslandContract.EXTRA_TITLE
import dev.qarasky.dotisland.api.PublishRequest
import dev.qarasky.dotisland.api.PublishResult
import dev.qarasky.dotisland.api.PublishedActivity
import dev.qarasky.dotisland.api.parsePublishedActivity
import dev.qarasky.dotisland.data.DotIslandPublisherStore
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.util.runCatchingLogged
import dev.qarasky.dotisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for the public publishing API.
 *
 * Deliberately exported without a manifest-level permission: a custom permission
 * would lock out `adb shell` and Termux, which are the main callers here. Safety
 * comes from Binder instead. `Binder.getCallingUid()` is supplied by the kernel
 * and cannot be forged by the sender, so the packages resolved from that UID are
 * the real identity — a publisher cannot claim another app's namespace, label or
 * icon. That package is then checked against the user's allowlist.
 */
@AndroidEntryPoint
class DotIslandPublishReceiver : BroadcastReceiver() {

    @Inject lateinit var store: DotIslandPublisherStore
    @Inject lateinit var repository: INotificationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val callerPackage = resolveCallerPackage(context, callerUid)
        if (callerPackage == null) {
            Log.w(TAG, "Rejecting publish from unresolved uid $callerUid")
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                runSuspendCatchingLogged(TAG, "Publish request failed") {
                    handle(context.applicationContext, intent, callerPackage)
                }
            } finally {
                runCatchingLogged(TAG, "goAsync finish failed") { pending.finish() }
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, callerPackage: String) {
        if (!store.isAllowed(callerPackage)) {
            Log.w(TAG, "Publisher not allowed: $callerPackage")
            return
        }
        when (intent.action) {
            DotIslandContract.PUBLISH, DotIslandContract.UPDATE -> publish(context, intent, callerPackage)
            DotIslandContract.DISMISS -> {
                store.remove(intent.getStringExtra(EXTRA_ID).orEmpty())
                sync(context)
            }
            DotIslandContract.DISMISS_ALL -> {
                store.removeAllFor(callerPackage)
                sync(context)
            }
        }
    }

    private suspend fun publish(context: Context, intent: Intent, callerPackage: String) {
        val id = intent.getStringExtra(EXTRA_ID)
        val request = PublishRequest(
            id = id,
            appName = intent.getStringExtra(EXTRA_APP_NAME),
            title = intent.getStringExtra(EXTRA_TITLE),
            text = intent.getStringExtra(EXTRA_TEXT),
            progress = intent.getIntExtra(EXTRA_PROGRESS, -1).takeIf { it >= 0 },
            timeoutMillis = intent.getLongExtra(EXTRA_TIMEOUT_MS, 0L).takeIf { it > 0L },
            expand = intent.getBooleanExtra(EXTRA_EXPAND, false),
            iconPackage = intent.getStringExtra(EXTRA_ICON_PACKAGE),
            iconResource = intent.getStringExtra(EXTRA_ICON_RESOURCE)
        )
        val now = System.currentTimeMillis()
        val current = store.activities.first()
        when (val result = parsePublishedActivity(
            request = request,
            callerPackage = callerPackage,
            publisherLabel = labelFor(context, callerPackage),
            nowMillis = now,
            previousPublishMillis = current.firstOrNull { it.id == id }?.publishedAtMillis ?: 0L,
            activePublishers = current.map { it.publisherPackage }.distinct().size
        )) {
            is PublishResult.Rejected -> Log.w(TAG, "Rejected $callerPackage: ${result.reason.detail}")
            is PublishResult.Accepted -> {
                store.upsert(result.activity)
                val notification = withContext(Dispatchers.Main) {
                    publishedActivityToNotification(context, result.activity)
                }
                repository.postNotification(notification, autoExpand = result.activity.expandOnPublish)
            }
        }
    }

    /** Replaces the displayed set so nothing lingers after a removal. */
    private suspend fun sync(context: Context) {
        val remaining: List<PublishedActivity> = store.activities.first()
        val kept = remaining.map { publishedActivityToNotification(context, it) }
        repository.removeAllPublished(kept.map { it.packageName }.toSet())
        kept.forEach { repository.postNotification(it) }
    }

    private fun resolveCallerPackage(context: Context, uid: Int): String? = runCatching {
        context.packageManager.getPackagesForUid(uid)?.firstOrNull()
    }.getOrNull()

    private fun labelFor(context: Context, packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val TAG = "DotIslandPublishReceiver"
    }
}
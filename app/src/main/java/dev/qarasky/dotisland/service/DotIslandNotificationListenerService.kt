/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.service

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import dev.qarasky.dotisland.data.INotificationRepository
import dev.qarasky.dotisland.data.DotIslandPublisherStore
import dev.qarasky.dotisland.data.DotIslandCommand
import dev.qarasky.dotisland.data.DotIslandSettingsRepository
import dev.qarasky.dotisland.model.IslandMode
import dev.qarasky.dotisland.model.IslandNotification
import dev.qarasky.dotisland.model.IslandNotificationAction
import dev.qarasky.dotisland.util.PersonalActivityPolicy
import dev.qarasky.dotisland.util.bestSpotifyStateIndex
import dev.qarasky.dotisland.util.isSpotifyDeadState
import dev.qarasky.dotisland.util.isSpotifyPlayingState
import dev.qarasky.dotisland.util.runCatchingLogged
import dev.qarasky.dotisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Read-only system notifications. No cancellation, custom sound, history or cooldown. */
@AndroidEntryPoint
class DotIslandNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var repository: DotIslandSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    @Inject lateinit var publisherStore: DotIslandPublisherStore
    private var expiryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
        android.util.Log.e(TAG, "Listener failure", e)
    })
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var controller: MediaController? = null
    private var spotifyNotification: StatusBarNotification? = null
    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions -> selectSpotify(sessions.orEmpty()) }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishSpotify()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishSpotify()
        override fun onSessionDestroyed() {
            // No immediate removal: the replacement session often appears a beat
            // later, and publishSpotify's grace delay covers a real death.
            detachController()
            refreshSessions()
        }
    }
    private var spotifyRemovalJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repository.settings.collect {
                enabled = it.enabled
                if (!enabled) notificationRepository.removeAllNotifications()
                else if (isSystemConnected) restoreActivities()
            }
        }
        scope.launch {
            notificationRepository.commands.collect { command ->
                runCatchingLogged(TAG, "Media command failed") {
                    when (command) {
                        // Dismiss the island representation, never the original notification.
                        is DotIslandCommand.CancelNotification -> {
                            notificationRepository.removeNotification(command.key)
                            // A published activity is also durable state, so a
                            // swipe-dismiss must clear the stored copy too.
                            publisherStore.remove(command.key)
                        }
                        is DotIslandCommand.SeekTo -> if (command.packageName == PersonalActivityPolicy.SPOTIFY) controller?.transportControls?.seekTo(command.positionMs)
                        is DotIslandCommand.SkipNext -> if (command.packageName == null || command.packageName == PersonalActivityPolicy.SPOTIFY) controller?.transportControls?.skipToNext()
                        is DotIslandCommand.SkipPrevious -> if (command.packageName == null || command.packageName == PersonalActivityPolicy.SPOTIFY) controller?.transportControls?.skipToPrevious()
                        is DotIslandCommand.PlayPause -> if (command.packageName == null || command.packageName == PersonalActivityPolicy.SPOTIFY) {
                            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) controller?.transportControls?.pause()
                            else controller?.transportControls?.play()
                        }
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isSystemConnected = true
        runCatchingLogged(TAG, "Session listener registration failed") {
            manager?.addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(this, javaClass), handler)
        }
        scope.launch { restoreActivities() }
    }

    private fun restoreActivities() {
        if (!enabled) return
        runCatchingLogged(TAG, "Activity restoration failed") {
            activeNotifications.orEmpty().forEach(::ingest)
            refreshSessions()
        }
        scope.launch {
            runSuspendCatchingLogged(TAG, "Published activity restore failed") { restorePublished() }
            startExpiryWatch()
        }
    }

    /**
     * Republishes stored API activities. A broadcast can start this process cold,
     * so without this an activity published while nothing was running would never
     * appear once the overlay finally bound.
     */
    private suspend fun restorePublished() = withContext(Dispatchers.Main) {
        val stored = publisherStore.activities.first()
        val notifications = stored.map { publishedActivityToNotification(applicationContext, it) }
        notificationRepository.removeAllPublished(notifications.map { it.packageName }.toSet())
        notifications.forEach { notificationRepository.postNotification(it) }
    }

    /**
     * Drops lapsed activities. Expiry is also filtered on read, so this only has
     * to be frequent enough that an entry disappears while the user is watching.
     */
    private fun startExpiryWatch() {
        if (expiryJob?.isActive == true) return
        expiryJob = scope.launch {
            while (true) {
                delay(EXPIRY_POLL_MS)
                if (!enabled) continue
                runSuspendCatchingLogged(TAG, "Expiry sweep failed") {
                    val live = publisherStore.activities.first()
                    publisherStore.removeExpired()
                    withContext(Dispatchers.Main) {
                        notificationRepository.removeAllPublished(live.map { it.publisherPackage }.toSet())
                        live.forEach {
                            notificationRepository.postNotification(publishedActivityToNotification(applicationContext, it))
                        }
                    }
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { scope.launch { ingest(sbn) } }
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) = onNotificationPosted(sbn)
    private fun ingest(sbn: StatusBarNotification) {
        if (!enabled) return
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val max = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        val mode = PersonalActivityPolicy.mode(sbn.packageName, n.category,
            n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION), n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            title, text, max, n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        if (mode == IslandMode.Empty) {
            notificationRepository.removeNotification(sbn.key)
            return
        }
        if (mode == IslandMode.Music) {
            spotifyNotification = sbn
            refreshSessions()
            publishSpotify()
            return
        }
        val actions = n.actions.orEmpty().map { IslandNotificationAction(it.title.toString(), it.actionIntent) }
        val appName = if (mode == IslandMode.LiveActivity) "Dodo Pizza" else "Phone"
        notificationRepository.postNotification(IslandNotification(
            key = sbn.key, packageName = sbn.packageName, appName = appName,
            title = title, text = text, timeMillis = n.`when`.takeIf { it > 0 } ?: sbn.postTime,
            icon = runCatching { packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96) }.getOrNull(),
            largeIcon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(128, 128) }.getOrNull(),
            actionIntents = actions, category = n.category, mode = mode, contentIntent = n.contentIntent,
            progress = n.extras.getInt(Notification.EXTRA_PROGRESS), progressMax = max
        ))
    }

    private fun refreshSessions() {
        runCatchingLogged(TAG, "Spotify session refresh failed") {
            selectSpotify(manager?.getActiveSessions(ComponentName(this, javaClass)).orEmpty())
        }
    }
    private fun selectSpotify(sessions: List<MediaController>) {
        val matches = sessions.filter { it.packageName == PersonalActivityPolicy.SPOTIFY }
        // Keep the current controller while its session is still alive, whatever
        // transitional state a track switch puts it in. Only re-pick when it is
        // genuinely gone, so buffering/skipping never detaches us mid-switch.
        val current = controller
        if (current != null && matches.any { it.sessionToken == current.sessionToken }) {
            publishSpotify()
            return
        }
        val bestIndex = bestSpotifyStateIndex(matches.map { it.playbackState?.state })
        val best = bestIndex?.let { matches[it] }
        if (best != null && current?.sessionToken != best.sessionToken) {
            detachController()
            controller = best
            best.registerCallback(mediaCallback, handler)
        } else if (best == null) {
            detachController()
        }
        publishSpotify()
    }
    private fun publishSpotify() {
        if (!enabled) return
        val c = controller
        val state = c?.playbackState
        if (c == null || state == null || isSpotifyDeadState(state.state)) {
            scheduleSpotifyRemoval()
            return
        }
        val metadata = c.metadata
        val n = spotifyNotification?.notification
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // A blank title mid-switch means "not yet", not "gone": keep the old card.
        if (title.isNullOrBlank()) {
            spotifyRemovalJob?.cancel()
            return
        }
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        spotifyRemovalJob?.cancel()
        notificationRepository.postNotification(IslandNotification(
            key = SPOTIFY_KEY, packageName = PersonalActivityPolicy.SPOTIFY, appName = "Spotify", title = title,
            text = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: n?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            timeMillis = spotifyNotification?.postTime ?: System.currentTimeMillis(),
            largeIcon = artwork, mediaToken = c.sessionToken, mediaIsPlaying = isSpotifyPlayingState(state?.state),
            mediaPositionMs = state.position, mediaDurationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION),
            mode = IslandMode.Music, contentIntent = n?.contentIntent,
            actionIntents = n?.actions.orEmpty().map { IslandNotificationAction(it.title.toString(), it.actionIntent) }
        ))
    }
    /**
     * Removal grace: track switches (and session handovers) briefly look dead.
     * Only remove the island if there is still no usable session after the delay;
     * any successful publish in between cancels the pending removal.
     */
    private fun scheduleSpotifyRemoval() {
        if (spotifyRemovalJob?.isActive == true) return
        spotifyRemovalJob = scope.launch {
            delay(SPOTIFY_REMOVAL_GRACE_MS)
            val state = controller?.playbackState
            if (isSpotifyDeadState(state?.state)) {
                notificationRepository.removeNotification(SPOTIFY_KEY)
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scope.launch {
            if (sbn.key == spotifyNotification?.key) { spotifyNotification = null; refreshSessions() }
            else notificationRepository.removeNotification(sbn.key)
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) = onNotificationRemoved(sbn)
    override fun onListenerDisconnected() {
        isSystemConnected = false
        detachController()
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        notificationRepository.removeAllNotifications()
        runCatching { requestRebind(ComponentName(this, javaClass)) }
        super.onListenerDisconnected()
    }
    private fun detachController() { controller?.unregisterCallback(mediaCallback); controller = null }
    override fun onDestroy() {
        isSystemConnected = false
        detachController()
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        @Volatile var isSystemConnected = false
            private set
        private const val TAG = "IslandActivities"
        private const val SPOTIFY_KEY = "spotify_session"
        private const val EXPIRY_POLL_MS = 15_000L
        private const val SPOTIFY_REMOVAL_GRACE_MS = 2_000L
    }
}

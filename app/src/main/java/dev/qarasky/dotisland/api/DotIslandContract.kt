/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.api

/**
 * Stable, public contract for third-party activities.
 *
 * Everything here is parsed and clamped by pure functions so the rules are unit
 * testable without an Android runtime. `DotIslandPublisher` is a thin SDK over
 * these broadcasts, so a publisher can also shell out from Termux or Tasker.
 *
 * Identity is decided by Binder's caller UID, not by anything in the intent, so a
 * publisher can never claim another app's namespace or its launcher label.
 */
object DotIslandContract {
    const val PUBLISH = "dev.qarasky.dotisland.action.PUBLISH"
    const val UPDATE = "dev.qarasky.dotisland.action.UPDATE"
    const val DISMISS = "dev.qarasky.dotisland.action.DISMISS"
    const val DISMISS_ALL = "dev.qarasky.dotisland.action.DISMISS_ALL"

    const val EXTRA_ID = "id"
    const val EXTRA_APP_NAME = "app_name"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_TIMEOUT_MS = "timeout_ms"
    const val EXTRA_EXPAND = "expand"
    const val EXTRA_ICON_PACKAGE = "icon_package"
    const val EXTRA_ICON_RESOURCE = "icon_res"

    const val MAX_ID_LENGTH = 128
    const val MAX_TITLE_LENGTH = 200
    const val MAX_TEXT_LENGTH = 400
    const val MAX_APP_NAME_LENGTH = 64
    const val MAX_ICON_RESOURCE_LENGTH = 128

    /** A crashed publisher must not leave an island entry forever. */
    const val DEFAULT_TIMEOUT_MS = 30L * 60L * 1000L
    const val MIN_TIMEOUT_MS = 30L * 1000L
    const val MAX_TIMEOUT_MS = 24L * 60L * 60L * 1000L

    /** Guards against a publisher's update loop hammering the overlay. */
    const val MIN_UPDATE_INTERVAL_MS = 400L
    const val MAX_ACTIVE_PUBLISHERS = 5

    fun timeoutFor(requested: Long?): Long = when {
        requested == null || requested <= 0L -> DEFAULT_TIMEOUT_MS
        requested < MIN_TIMEOUT_MS -> MIN_TIMEOUT_MS
        requested > MAX_TIMEOUT_MS -> MAX_TIMEOUT_MS
        else -> requested
    }

    /**
     * Each publisher owns a namespace so one app cannot silently replace another
     * app's island activity. `id` must therefore be `<callerPackage>:<localId>`.
     */
    fun isIdOwnedBy(id: String, callerPackage: String): Boolean =
        id.length <= MAX_ID_LENGTH && id.startsWith("$callerPackage:") && id.length > callerPackage.length + 1
}

/** A validated activity ready to be shown. */
data class PublishedActivity(
    val id: String,
    val publisherPackage: String,
    val appName: String,
    val title: String,
    val text: String,
    val progress: Int?,
    val iconPackage: String?,
    val iconResource: String?,
    val expandOnPublish: Boolean,
    val publishedAtMillis: Long,
    val expiresAtMillis: Long
)

/** Raw intent contents, before validation. */
data class PublishRequest(
    val id: String?,
    val appName: String?,
    val title: String?,
    val text: String?,
    val progress: Int?,
    val timeoutMillis: Long?,
    val expand: Boolean?,
    val iconPackage: String?,
    val iconResource: String?
)

sealed interface PublishResult {
    data class Accepted(val activity: PublishedActivity) : PublishResult
    data class Rejected(val reason: RejectReason) : PublishResult
}

enum class RejectReason(val detail: String) {
    MISSING_ID("no id"),
    ID_NOT_OWNED("id must be \"<your.package>:something\""),
    MISSING_TITLE("no title"),
    TOO_FAST("updates faster than ${DotIslandContract.MIN_UPDATE_INTERVAL_MS}ms"),
    LIMIT_REACHED("too many active publishers")
}

/**
 * Validates one publish/update. [publisherLabel] is the real app label resolved
 * from the caller's UID, so it is used unless the publisher supplied a shorter
 * override for its own surface.
 */
fun parsePublishedActivity(
    request: PublishRequest,
    callerPackage: String,
    publisherLabel: String,
    nowMillis: Long,
    previousPublishMillis: Long,
    activePublishers: Int
): PublishResult {
    val id = request.id?.trim().orEmpty()
    if (id.isEmpty()) return PublishResult.Rejected(RejectReason.MISSING_ID)
    if (!DotIslandContract.isIdOwnedBy(id, callerPackage)) return PublishResult.Rejected(RejectReason.ID_NOT_OWNED)

    val title = request.title?.trim().orEmpty()
    if (title.isEmpty()) return PublishResult.Rejected(RejectReason.MISSING_TITLE)

    // Rate limit only once an activity already exists; the first publish is free.
    val isFirstPublish = previousPublishMillis <= 0L
    if (!isFirstPublish && nowMillis - previousPublishMillis < DotIslandContract.MIN_UPDATE_INTERVAL_MS) {
        return PublishResult.Rejected(RejectReason.TOO_FAST)
    }
    if (isFirstPublish && activePublishers >= DotIslandContract.MAX_ACTIVE_PUBLISHERS) {
        return PublishResult.Rejected(RejectReason.LIMIT_REACHED)
    }

    val label = request.appName?.trim()?.takeIf { it.isNotEmpty() }
        ?: publisherLabel.take(DotIslandContract.MAX_APP_NAME_LENGTH)

    return PublishResult.Accepted(
        PublishedActivity(
            id = id,
            publisherPackage = callerPackage,
            appName = label,
            title = title.take(DotIslandContract.MAX_TITLE_LENGTH),
            text = request.text?.trim().orEmpty().take(DotIslandContract.MAX_TEXT_LENGTH),
            progress = request.progress?.coerceIn(0, 100),
            iconPackage = request.iconPackage?.trim()?.takeIf { it.isNotEmpty() },
            iconResource = request.iconResource?.trim()?.takeIf { it.isNotEmpty() }
                ?.take(DotIslandContract.MAX_ICON_RESOURCE_LENGTH),
            expandOnPublish = request.expand == true,
            publishedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + DotIslandContract.timeoutFor(request.timeoutMillis)
        )
    )
}
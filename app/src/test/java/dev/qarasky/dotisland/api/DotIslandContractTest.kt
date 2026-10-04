/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.qarasky.dotisland.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DotIslandContractTest {

    private val publisher = "com.example.tracker"
    private val now = 1_000_000L

    private fun request(
        id: String? = "$publisher:order-1",
        title: String? = "On the way",
        text: String? = null,
        progress: Int? = null,
        timeoutMillis: Long? = null,
        expand: Boolean? = false,
        appName: String? = null,
        iconPackage: String? = null,
        iconResource: String? = null
    ) = PublishRequest(id, appName, title, text, progress, timeoutMillis, expand, iconPackage, iconResource)

    private fun parse(
        request: PublishRequest = request(),
        previousPublishMillis: Long = 0L,
        activePublishers: Int = 0
    ) = parsePublishedActivity(request, publisher, "Tracker", now, previousPublishMillis, activePublishers)

    private fun accepted(result: PublishResult): PublishedActivity {
        assertTrue("expected acceptance but was $result", result is PublishResult.Accepted)
        return (result as PublishResult.Accepted).activity
    }

    private fun rejected(result: PublishResult): RejectReason {
        assertTrue("expected rejection but was $result", result is PublishResult.Rejected)
        return (result as PublishResult.Rejected).reason
    }

    @Test fun `namespace must belong to the real caller`() {
        assertEquals(RejectReason.ID_NOT_OWNED, rejected(parse(request(id = "com.other.app:order-1"))))
        assertEquals(RejectReason.ID_NOT_OWNED, rejected(parse(request(id = publisher))))
        assertEquals(RejectReason.ID_NOT_OWNED, rejected(parse(request(id = publisher + "X:x"))))
        assertEquals(RejectReason.MISSING_ID, rejected(parse(request(id = null))))
        assertEquals(RejectReason.MISSING_ID, rejected(parse(request(id = "   "))))
    }

    @Test fun `title is required and whitespace is trimmed`() {
        assertEquals(RejectReason.MISSING_TITLE, rejected(parse(request(title = null))))
        assertEquals(RejectReason.MISSING_TITLE, rejected(parse(request(title = "  "))))
        assertEquals("On the way", accepted(parse(request(title = "  On the way  "))).title)
    }

    @Test fun `label comes from the system unless the publisher overrides it`() {
        assertEquals("Tracker", accepted(parse()).appName)
        assertEquals("Deliveries", accepted(parse(request(appName = "Deliveries"))).appName)
    }

    @Test fun `progress and long text are clamped rather than rejected`() {
        val activity = accepted(parse(request(progress = 5000, text = "x".repeat(5000))))
        assertEquals(100, activity.progress)
        assertEquals(DotIslandContract.MAX_TEXT_LENGTH, activity.text.length)
        assertNull(accepted(parse(request(progress = null))).progress)
    }

    @Test fun `timeout is clamped to a sane window and defaulted`() {
        assertEquals(DotIslandContract.DEFAULT_TIMEOUT_MS, accepted(parse()).expiresAtMillis - now)
        assertEquals(DotIslandContract.MIN_TIMEOUT_MS, accepted(parse(request(timeoutMillis = 1L))).expiresAtMillis - now)
        assertEquals(DotIslandContract.MAX_TIMEOUT_MS, accepted(parse(request(timeoutMillis = Long.MAX_VALUE))).expiresAtMillis - now)
    }

    @Test fun `updates are rate limited but a first publish is free`() {
        assertEquals(RejectReason.TOO_FAST, rejected(parse(previousPublishMillis = now - 100L)))
        assertEquals(RejectReason.TOO_FAST, rejected(parse(previousPublishMillis = now)))
        accepted(parse(previousPublishMillis = now - DotIslandContract.MIN_UPDATE_INTERVAL_MS))
    }

    @Test fun `publisher limit applies only to new publishers`() {
        assertEquals(
            RejectReason.LIMIT_REACHED,
            rejected(parse(activePublishers = DotIslandContract.MAX_ACTIVE_PUBLISHERS))
        )
        // An existing publisher updating must not be blocked by the cap.
        accepted(parse(previousPublishMillis = now - 10_000L, activePublishers = DotIslandContract.MAX_ACTIVE_PUBLISHERS))
    }

    @Test fun `icon is referenced by name so a shell can supply it`() {
        val activity = accepted(parse(request(iconPackage = "com.example.tracker", iconResource = "ic_delivery")))
        assertEquals("com.example.tracker", activity.iconPackage)
        assertEquals("ic_delivery", activity.iconResource)
        assertNull(accepted(parse(request(iconResource = "ic_delivery"))).iconPackage)
    }

    @Test fun `expansion is opt-in`() {
        assertTrue(!accepted(parse()).expandOnPublish)
        assertTrue(accepted(parse(request(expand = true))).expandOnPublish)
    }
}
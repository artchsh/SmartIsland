/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.content.ActivityNotFoundException
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the OEM autostart intent ladder.
 *
 * The previous version of this test was:
 *
 *     val result = OemAutostartUtil.openAutostartSettings(context)
 *     assertNotNull(result)
 *
 * `openAutostartSettings` returns a non-nullable `Boolean`, so `assertNotNull`
 * on it can never fail. It passed even when the function returned false on
 * every code path, which made it worthless as a regression guard.
 *
 * Scope note: this is a plain JVM unit test with no Robolectric, so
 * android.content.Intent is a stub. Intent fields are always null and
 * Intent.setComponent() returns null rather than `this`. These tests therefore
 * assert observable behaviour (how many launches are attempted, and what the
 * function returns) and the shape of the ladder, rather than inspecting intent
 * contents. Asserting on `intent.component` here would be asserting that the
 * mockable android.jar stub works.
 */
class OemAutostartUtilTest {

    private fun mockContext(): Context = mockk<Context>().also {
        every { it.packageName } returns "com.agupta07505.smartisland"
    }

    /**
     * The ladder must be fully walked before giving up, and must report
     * failure honestly when nothing on the device can handle any candidate.
     */
    @Test
    fun `returns false when every launch attempt fails`() {
        val context = mockContext()
        every { context.startActivity(any()) } throws ActivityNotFoundException("nothing installed")

        assertFalse(OemAutostartUtil.openAutostartSettings(context, deviceType = "XIAOMI_REDMI_POCO"))
    }

    /**
     * A restrictive OEM (locked-down ROM, work profile) throws SecurityException
     * rather than ActivityNotFoundException. That must not propagate.
     */
    @Test
    fun `returns false rather than throwing when every launch is blocked`() {
        val context = mockContext()
        every { context.startActivity(any()) } throws SecurityException("blocked by policy")

        assertFalse(OemAutostartUtil.openAutostartSettings(context, deviceType = "VIVO_IQOO"))
    }

    /**
     * Regression guard for the Motorola -> ASUS component bug.
     *
     * MOTOROLA previously resolved to com.asus.mobilemanager's autostart
     * activity, which cannot exist on a Motorola device. It is now the only
     * device type with no dedicated OEM screen, so it must fall through to the
     * single generic App Info intent and attempt exactly one launch.
     */
    @Test
    fun `motorola has no dedicated oem screen and uses only the app info fallback`() {
        val context = mockContext()
        val intents = OemDeviceRules.getAutostartIntents(context, OemDeviceType.MOTOROLA)

        assertEquals(
            "Motorola must not fabricate an OEM-specific intent",
            1,
            intents.size
        )

        every { context.startActivity(any()) } answers { }
        assertTrue(OemAutostartUtil.openAutostartSettings(context, deviceType = "MOTOROLA"))
        verify(exactly = 1) { context.startActivity(any()) }
    }

    /**
     * Every supported OEM must yield at least one launch candidate, otherwise
     * the "Fix Kills" button silently does nothing on that device.
     */
    @Test
    fun `every device type yields at least one launch candidate`() {
        val context = mockContext()
        for (device in OemDeviceType.entries) {
            val intents = OemDeviceRules.getAutostartIntents(context, device)
            assertTrue(
                "$device produced no autostart candidates",
                intents.isNotEmpty()
            )
        }
    }

    /**
     * The ladder stops as soon as one intent resolves instead of launching all
     * of them. Samsung defines three OEM candidates plus the App Info fallback,
     * so a clean first-attempt success must mean exactly one launch.
     */
    @Test
    fun `stops at the first intent that resolves`() {
        val context = mockContext()
        every { context.startActivity(any()) } answers { }

        assertTrue(OemAutostartUtil.openAutostartSettings(context, deviceType = "SAMSUNG"))
        verify(exactly = 1) { context.startActivity(any()) }
    }

    /**
     * A failure mid-ladder must not abort the walk.
     */
    @Test
    fun `continues the ladder after an earlier intent fails`() {
        val context = mockContext()
        var attempts = 0
        every { context.startActivity(any()) } answers {
            attempts++
            if (attempts < 3) throw ActivityNotFoundException("no such OEM activity")
        }

        assertTrue(OemAutostartUtil.openAutostartSettings(context, deviceType = "SAMSUNG"))
        assertEquals(
            "Should have stopped on the third candidate",
            3,
            attempts
        )
    }
}

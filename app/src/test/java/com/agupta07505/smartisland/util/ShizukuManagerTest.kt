/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuManagerTest {

    // ── isInstalled ────────────────────────────────────────────────────────
    // Previously only the negative branch was covered, so a regression that
    // made isInstalled() unconditionally return false would not have been
    // caught by this file.

    @Test
    fun `isInstalled returns false when the shizuku package is absent`() {
        val context = mockk<Context>()
        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        every { pm.getPackageInfo("moe.shizuku.privileged.api", 0) } throws RuntimeException("Not installed")

        assertFalse(ShizukuManager.isInstalled(context))
    }

    @Test
    fun `isInstalled returns true when the shizuku package resolves`() {
        val context = mockk<Context>()
        val pm = mockk<PackageManager>()
        every { context.packageManager } returns pm
        every { pm.getPackageInfo("moe.shizuku.privileged.api", 0) } returns mockk<PackageInfo>()

        assertTrue(ShizukuManager.isInstalled(context))
    }

    // ── Environment smoke checks ───────────────────────────────────────────
    // These assert that the binder/permission probes degrade to a safe false
    // on a device with no Shizuku service, instead of throwing. That is a real
    // contract (both are called during app startup), but it is a weak
    // regression guard: both would still pass if the functions were hardcoded
    // to `return false`. Treat them as "does not crash on a Shizuku-less
    // device", not as behavioural coverage.

    @Test
    fun `isBinderAvailable degrades to false when no shizuku service is running`() {
        assertFalse(ShizukuManager.isBinderAvailable())
    }

    @Test
    fun `hasPermission degrades to false when no shizuku service is running`() {
        assertFalse(ShizukuManager.hasPermission())
    }

    // ── mergeColonSeparated ────────────────────────────────────────────────
    // Real behavioural coverage: this builds the `--permission` argument for
    // the Shizuku shell call, and a malformed value silently revokes the very
    // permissions the 1-tap setup exists to grant.

    @Test
    fun `mergeColonSeparated handles an empty existing value`() {
        assertEquals(
            "com.agupta07505.smartisland/service",
            ShizukuManager.mergeColonSeparated("", "com.agupta07505.smartisland/service")
        )
    }

    @Test
    fun `mergeColonSeparated preserves existing services`() {
        val existing = "com.bitwarden.authenticator/service:com.lastpass.lpandroid/service"
        assertEquals(
            "com.bitwarden.authenticator/service:com.lastpass.lpandroid/service:com.agupta07505.smartisland/service",
            ShizukuManager.mergeColonSeparated(existing, "com.agupta07505.smartisland/service")
        )
    }

    @Test
    fun `mergeColonSeparated does not duplicate an entry that is already present`() {
        val existing = "com.bitwarden.authenticator/service:com.agupta07505.smartisland/service"
        assertEquals(
            "com.bitwarden.authenticator/service:com.agupta07505.smartisland/service",
            ShizukuManager.mergeColonSeparated(existing, "com.agupta07505.smartisland/service")
        )
    }
}

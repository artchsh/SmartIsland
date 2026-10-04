/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DotIslandApp : Application()

/*
 * REMOVED: hidden-API exemption.
 *
 * This class previously overrode onCreate() to reflectively call
 * dalvik.system.VMRuntime.setHiddenApiExemptions(["L"]), exempting the ENTIRE
 * hidden-API surface for the process. That call existed for one reason: to let
 * DotIslandOverlayService.setupTouchableRegion() reach
 * ViewTreeObserver.OnComputeInternalInsetsListener, a hidden framework callback
 * used to shrink the overlay window's touchable area down to the pill.
 *
 * It was removed because:
 *
 *  1. It no longer works. setHiddenApiExemptions is blocked for non-system apps
 *     on Android 14 and later. On Android 15/16 the call throws, the catch block
 *     swallowed it, and the app logged "Successfully bypassed Hidden API
 *     restrictions" anyway. The log line was a lie.
 *  2. It cost three reflective lookups plus a VMRuntime invocation on the main
 *     thread of every cold start, unconditionally, for a capability that
 *     almost never succeeded.
 *  3. It ran after super.onCreate(), so any class Hilt had already loaded was
 *     unaffected, which made the ordering counterproductive as well as wasteful.
 *  4. "L" is a Google Play policy violation and is reported by Play Integrity.
 *
 * The capability is no longer needed. Touch pass-through is achieved with the
 * public FLAG_NOT_TOUCH_MODAL, which the overlay window already sets: touches
 * outside a window's bounds are delivered to the window behind it. Combined with
 * sizing the collapsed window to the pill (rather than the whole display and
 * then carving out a region), no hidden API is involved at any point.
 *
 * See AUDIT.md section 4.1.
 */

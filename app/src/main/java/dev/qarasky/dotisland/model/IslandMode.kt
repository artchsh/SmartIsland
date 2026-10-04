/*
 * Dot Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.qarasky.dotisland.model

enum class IslandMode {
    Empty,
    IncomingCall,
    Music,
    LiveActivity,
    /** Published by a third-party app through the public API. */
    Published
}

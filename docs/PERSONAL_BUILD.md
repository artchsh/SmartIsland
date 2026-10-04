# Personal island build

For one user, one A069, Android 16 (API 36). There is no public-product settings
surface, migration system, updater, network client, history or app launcher.

## UI

`DotIslandHomeScreen.kt` is the entire app screen: enable switch, live compact
preview, source availability and contextual Android permission links. Preview
uses the compact renderer without creating fake activities or issuing playback
commands. Only the enable flag is persisted. Geometry and behavior are code
constants in `DotIslandSettings.kt`; dimensions remain density-independent.

Black/white/grey surfaces, fine borders, squircle artwork, monospaced microtype,
readable sans content. No theme/color/layout editor, decorative gradient or red
accent. `docs/ISLAND_STYLE.md` documents the underlying motion and camera geometry.

## Activity policy

`PersonalActivityPolicy.kt` is applied on live notification ingestion and initial
restoration. Spotify session discovery independently restricts package identity.

- Spotify: `com.spotify.music`, actual playing/paused media session.
- Phone: `com.google.android.dialer` or `com.android.server.telecom`, call category.
- Dodo: `ru.dodopizza.app`, conservative English/Russian order-state detection,
  excluding common promotional text. Real delivery integration remains unverified.
- Group summaries and all other notifications are ignored, not cancelled.

The listener never suppresses the system shade, replays notification sounds,
records history, invokes networking or sends other apps' media commands. User
actions can still invoke the original call answer/end PendingIntent. Dismissal
only removes an island representation. Calls have priority over delivery and
music; at most one representation per type is retained.

`LiveActivityParser.kt` does not infer order completion percentage from elapsed
time. No progress bar appears without a valid source progress maximum; no ETA
appears without a minutes value in source text. Use an actual order notification
to validate its wording, channel and lifecycle before claiming Dodo support.

## Idle and foreground behavior

True idle has a standby matrix and `IDLE`, not an expanded empty launcher.
Every 9 seconds the matrix can make a 600ms diagonal brightness wink. There is
no frame clock during the delay. Lifecycle pause cancels it and resets the mark;
screen-off, battery saving and disabled system animations suppress new bursts.

Music hidden while Spotify is foreground is not idle: the pill stays blank.
Leaving Spotify crossfades the same activity back in. Content identity is its
stable session key, not title/position. Expansion content exits with a zero-delay
100ms fade, preserving the smooth geometry transition. Collapsed windows remain
small for touch pass-through; shrink only after actual animation completion.

## Device access

Accessibility and notification access remain Android requirements, not product
configuration. Battery-exemption repair is offered when missing. Boot/package
replacement/unlock request service recovery without Shizuku or auto-granting
permissions. Screen-off pauses the overlay's lifecycle.

No Internet, usage-statistics, Bluetooth-event, draw-over-apps or Shizuku permissions
are requested by the personal manifest. Audio output retains its public system
switcher/Bluetooth-settings fallback; it does not scan Bluetooth devices.

## Testing scope

Obsolete history/OEM/backup/unused-mode tests were retired with their features.
Retained tests cover camera overlap, screen/window coordinate handoffs, compact
window bounds, fades, refresh hint, squircle geometry and gestures. Replacement
tests cover the fixed profile, strict sources, conservative Dodo parsing, activity
priority/deduplication, idle expansion prevention and foreground suppression.
Device smoke tests cover idle rendering and the one-switch personal screen.

## Verification — 2026-10-04, A069

- `assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug` passed
  (serial build; parallel lint/Kapt interleaving triggers a lint tool bug, so
  verification runs with `--no-parallel --max-workers=1`): 70 unit tests, 2
  device smoke tests, zero failures. Lint passes.
- Installed build screenshot: single screen shows ACTIVE, one enabled switch,
  Spotify READY, Calls READY, Dodo UNVERIFIED, no old feature panels.
- Idle recording: a single ~0.4s dot brightness burst ~5.6s in (the wink),
  resting otherwise. Verified by pixel luminance analysis of a screen recording.
- Accessibility config narrowed: window-state events only, no window content
  retrieval. Manifest permissions: foreground service, boot receiver, battery
  exemption, vibration — nothing else.
- Not verified on-device: real Spotify playback transitions (no active session
  at the time), real Dodo order, incoming call. The device test runner cleared
  app grants during `connectedDebugAndroidTest`; they were restored by script
  and the final screenshot confirms ACTIVE.
- `git commit` was not run; all changes are working-tree only.

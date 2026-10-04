# Transition investigation — 2026-10-03

Codex (`gpt-6.1-sol`, high reasoning, read-only) researched Android implementations
and reviewed the overlay code. Its findings are hypotheses about device-visible
causes unless explicitly backed by code or platform documentation.

## Confirmed code problems addressed

- Requested expansion was used as a proxy for actual window bounds. Collapse
  switched coordinate systems before the window shrank.
- Window Y and Compose Y traded responsibility at the boundary, changing the
  intermediate screen position.
- The fixed 220 ms shrink timer could run before springs and the 270 ms content
  fade finished. Cleanup now waits for `!transition.isRunning` and collapsed state.
- Expansion now waits for enlarged window layout. Targets stay screen-relative;
  layers convert them using the host window's current origin and width.
- API 34+ window move animation is disabled in both LayoutParams builders. Whether
  Android was actually applying that animation on this device was not traced.
- Window geometry now counts the same visible notifications that Compose uses.
- Expanded content measures at final expanded width instead of each morph width.
- Outside-tap pointer input no longer restarts on every animated height/Y change.
- An obsolete unit test referenced an already-deleted helper; it now verifies
  screen-centre preservation across small/full/off-centre window geometries.

## Device verification and limitations

Build, unit tests and lint passed; APK installed with `adb install -r`. No
accessibility-service settings were changed and Spotify was not force-stopped.
Screenshots verified the actual Spotify music card. A reduced-resolution recording
showed two centred expand/collapse cycles at sampled 20 fps, without the reported
large sideways teleport. This is not proof that every frame or mode is correct.

Six-cycle aggregate measurements (same long-press/outside-tap sequence):

| Run | Frames | Janky | p95 | p99 | Missed vsync |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before | 1198 | 135 (11.27%) | 26 ms | 48 ms | 38 |
| After | 734 | 46 (6.27%) | 25 ms | 73 ms | 17 |

Playback/track changed between these runs; the after run was paused. Therefore
these numbers do **not** establish a performance improvement or isolate a cause.
Earlier empty-card/card-sized comparisons were also confounded and are withdrawn.
There is no established evidence that 150 ms histogram buckets were allocations,
or that MusicExpanded alone caused the jank.

Raw stats, screenshots and recording remain under the harness temporary directory,
in `smartisland/spotify_*_framestats.txt`, `fixed_*.png`, and `handoff_small.mp4`.

Remaining work: root per-frame composition/layout profiling; premeasurement or
height caching (a first-expansion 150→160 dp deferred adjustment was observed);
controlled playing/paused benchmarks; multi-activity/rotation/reversal validation;
the remaining per-mode visual refit. Do not claim iOS-level smoothness yet.

## Useful research

- [Essentials IslandWindowHost](https://github.com/sameerasw/essentials/blob/main/app/src/main/java/com/sameerasw/essentials/island/service/IslandWindowHost.kt): stable drawing window plus small input window. Useful alternative if boundary stalls remain; costs another window and touch forwarding.
- [Expressive Cutout](https://github.com/EvanKoe/expressive-cutout/blob/dev/app/src/main/java/com/ekoehler/expressivecutout/overlay/IslandOverlayController.kt): documents centred-window resize drift; its hidden touch-insets implementation is not adopted.
- [Sileo](https://github.com/bikash1376/sileo-android/blob/listener/app/src/main/java/com/sileo/island/service/SileoNotificationListenerService.kt): fixed-width overlay; transparent parts of its top strip are still in the touch bounds.
- [Public move-animation switch, API 34+](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setCanPlayMoveAnimation(boolean)).
- [Public touchable region, API 33+](https://developer.android.com/reference/android/view/AttachedSurfaceControl#setTouchableRegion(android.graphics.Region)): distinct from removed hidden-insets reflection.
- [AOSP ViewRootImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/ViewRootImpl.java): layout updates schedule traversal; they are not a presented-frame acknowledgement.
- [AOSP WindowState](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/wm/WindowState.java): independent window movement may accompany origin changes.
- [Compose phases](https://developer.android.com/develop/ui/compose/performance/phases): defer animated reads into layout/drawing where possible.

No reviewed project provided published frame traces proving iOS-quality pacing
on this phone. A MATCH_PARENT surface has memory costs, but neither its size nor
an accessibility overlay is, by itself, proof of a slow rendering pipeline.

## Follow-up: silent expansion, camera-safe content, music refit

- Removed the hold vibration and the vibration for the configured Expand swipe.
- Read the display's public cutout safe top (162 px / 2.75 ≈ 59 dp here), reserving
  it plus 6 dp clearance inside the expanded card. The spacer stays outside page
  scrolling. Outer top/side margins and the existing morph stay unchanged.
- The 84–160 dp body budget excludes this camera reservation, so making content
  safe does not hide the playback buttons behind a shorter viewport.
- Music follows `IMG_4181.HEIC`: black background, 64 dp rounded artwork, muted
  artist/timing labels, remaining-time countdown, restrained activity bars,
  three main transport buttons and an audio-output shortcut. Like/repeat buttons
  and the full-card album-art wash are no longer part of this presentation.
- Progress polling is isolated from artwork/controls, using callback-cached
  playback state. Seeking retains the latest callback across song changes.
- Output uses the public system switcher where permitted, otherwise Bluetooth
  settings; service overlays may be classified as background by Android.
- Device screenshots verified content below the reported camera-safe boundary.
  A playback-button tap was observed to pause the real Spotify session; playback
  was then resumed. Physical camera occlusion/vibration still needs user confirmation.

## Follow-up: precise camera placement and plain surfaces — 2026-10-04

The full-width camera spacer is superseded **for music** by per-item header layout.
The device's public `DisplayCutout.cutoutPath` returned screen-pixel bounds
`[578.2, 47.6 – 645.8, 115.5]` (67.6 × 67.9 px, centre 612 × 81.55 px).
`boundingRects` returned the larger safety region `[559, 0 – 665, 162]`.
These are OS-reported display occlusion bounds, not a measurement of the optical lens.

Artwork and activity bars remain high when they do not intersect that region.
Overlapping text blocks are placed at least one physical pixel below it, rounded
outward to integer layout pixels. All calculations use final expanded geometry,
not per-frame onSizeChanged state feedback. Music's separate content slide/scale
is disabled so it does not invalidate this tight positioning during the morph.
Conservative rectangle/full-top padding remains the fallback on older devices.

Removed artwork washes, radial glows and gradient backdrops for every expanded
mode, including the empty launcher. Functional icon/button/progress backgrounds
and the plain island surface remain. Legacy backdrop preferences no longer affect
the expanded presentation.

Device geometry was logged and the compact music layout verified using the built-in
Starlight demo: no active Spotify session existed during this check. No Spotify
force-stop or playback command was issued. The final APK was reinstalled after
the check to discard the in-memory demo fixture. Six additional geometry tests cover overlapping text,
side artwork, side activity bars, absent/off-centre cameras and already-safe text.

## Follow-up: high-refresh motion and balanced artwork — 2026-10-04

Current stylistic decisions, measured pacing and optional Nothing-inspired ideas
are consolidated in `docs/ISLAND_STYLE.md`. That document supersedes the historical
silent-expansion / 160dp-music / 8dp-artwork-top choices above.

Animated width/height now read in a layout modifier, radius in drawing/layers,
and content visibility through derived thresholds. Clip, transform and optional
shadow share the main layer. Compact glyph translations no longer subscribe the
whole composition to every alpha update. Compact music bars are monochrome and
share a single clock, omitted while paused. Expanded windows request a public
same-resolution refresh-rate hint up to 120 Hz, released on final collapse.

Artwork uses a fixed squircle outline. Music's top/bottom/side content insets are
all 20dp, with a 180dp body budget to preserve artwork and 48dp playback targets.
Expansion haptics were reinstated at the user's request as a 12ms, low-amplitude
pulse, replacing the old 60ms default-amplitude vibration for expansion only.

## Follow-up: content fading and monochrome Nothing-inspired details

Expanded alpha was reusing the entry tween in both directions, including its
70ms delay. Collapse now uses a separate 100ms linear fade with zero delay;
all expanded children inherit it from the shared content layer. Entry keeps its
70ms delay / 200ms fade. Music does not gain an independent slide or scale.

Compact content now crossfades by stable notification identity (180ms in,
100ms out), retaining the outgoing snapshot rather than replacing it instantly.
Position, playback and title changes for the same key do not restart that fade.
This also covers foreground-source suppression and restoration when leaving
Spotify. This is a content animation, not another WindowManager animation.

Actual idle is distinguished from suppressed source content through a boolean
source-activity flow. True idle shows a static standby matrix and `IDLE`; music
suppressed inside Spotify remains blank. The existing hide-when-idle option
still overrides this presentation. Music uses smoothly dimmed white dot matrices,
times use system monospace, and names remain sans. No red theme accent added.

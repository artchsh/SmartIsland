# iOS Dynamic Island — Redesign Specification

Target: authentic Dynamic Island interaction and geometry, as documented in Apple's
Human Interface Guidelines for Live Activities. Nothing OS styling is deliberately
**deferred** — this document covers the iOS layer only.

Reference material:
- [Live Activities — Apple HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities)
- [Meet ActivityKit (WWDC23)](https://developer.apple.com/videos/play/wwdc2023/10184)
- [Live Activities essentials (WWDC26)](https://developer.apple.com/videos/play/wwdc2026/223)
- [Displaying live data with Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities)

---

## 1. Target device

Nothing Phone (4a), codename `Frogger`, model `A069`, Android 16 (API 36),
Nothing OS `B4.1-260808-1352`.

- Physical: 1224 × 2720 px
- Effective density: **2.75** (480dpi physical, 440dpi override)
- Logical screen: **445 × 989 dp**
- Front camera: centred punch-hole

The app already has `util/CameraCutoutDetector` for locating the cutout, which the
compact leading/trailing layout needs.

---

## 2. HIG reference dimensions

Measured from the HIG, portrait:

| Presentation | Dimensions |
| --- | --- |
| Compact leading | ≤ 62.33 × 36.67 pt |
| Compact trailing | ≤ 62.33 × 36.67 pt |
| Minimal | 36.67–45 × 36.67 pt |
| Expanded | ~371–408 wide × 84–160 tall pt |
| Corner radius | 44 pt, matched to the TrueDepth camera |

Dynamic Island hardware width: 230 pt (Pro), 250 pt (Pro Max).

Derived margin: `430 − 408 = 22`, i.e. **11 pt per side** on the widest supported
screen, and `393 − 371 = 22` on the narrowest. The HIG is internally consistent at
an 11 pt inset.

### 2.1 Colours sampled from a reference screenshot

Rather than eyeballed, values were sampled from a photograph of a real Dynamic
Island (1179 px wide capture, i.e. exactly 3 px per pt):

| Element | Sampled value |
| --- | --- |
| Island hairline border | `RGB(40,40,40)` = `#282828` on pure black |
| Border thickness | 2–3 px ≈ **1 dp** |
| Seek bar, played | `RGB(156,155,162)` = `#9C9BA2` |
| Seek bar, remaining | `RGB(36,36,37)` = `#242425` |
| Seek bar thickness | 21 px = **7 pt** |
| Seek bar end caps | fully rounded, **no thumb** |

The border is defined as `Color.White.copy(alpha = 40f/255f)` rather than a
literal grey so it stays neutral over both the black interior and album artwork.
Rendering was verified against the reference at `RGB(41,41,41)` before the alpha
was tightened to land exactly on 40.

### 2.2 Corner radius follows the display

The HIG's "its rounded corner shape matches the TrueDepth camera" means the
island's curvature should be **continuous with the screen's own**, not a fixed
constant. Smart Island hardcoded 34 dp for the expanded card and used a
user-configurable value for the pill, which looks subtly wrong on any device whose
corners are a different shape.

`util/DisplayCornerRadius.kt` reads the real value from
`WindowInsets.getRoundedCorner(WindowInsets.RoundedCorner.POSITION_TOP_LEFT)`
(API 31+), falling back to top-right, then to the HIG's documented 44 pt. Values
are reported in pixels and are converted to dp. The `matchDisplayCorners` setting
(default on) chooses between the reported radius and the user's manual
`cornerRadius`.

A one-line diagnostic log reports the resolved value so a mismatch can be checked
without guessing:

```
adb logcat -s SmartIslandOverlayView | grep "display corner radius"
```

### 2.3 What "Live Activity" actually means

From WWDC23 "Meet ActivityKit", the load-bearing reference for §3.3:

- A Live Activity **"has a discrete start and end"** and is begun by **explicit
  user action inside the app**. It is not notification-driven.
- It is **"user-moderated similar to Notifications"**.
- **"Show most essential content. Simple design. Show additional details in the
  application."**
- When one activity is active it renders in the **compact** presentation. When
  several apps have activities the system shows **up to two**, both in the
  **minimal** presentation, one attached to the camera and one detached.
- **"Long press a Live Activity to display its expanded presentation."**
- The expanded presentation is **divided into regions** (leading / trailing /
  centre / bottom).
- iOS 17 added **interactive** Live Activities: buttons and toggles directly in
  the expanded view.

The first two points are the justification for the allowlist: Smart Island cannot
know which apps have ongoing activities, so it approximates with an explicit
allowlist plus a set of modes that are inherently continuous.

The "minimal presentation for secondary activities" behaviour is also why the
companion bubble is a small circle rather than a second full card — which this app
already had right.

---

## 3. Decisions taken

### 3.1 Expand trigger — iOS-exact

| Gesture (compact) | Action |
| --- | --- |
| **Long press** | Expand — the pill morphs into the card |
| **Single tap** | Open the source app |
| **Swipe up** | Dismiss the current activity |
| **Swipe left / right** | Switch between activities |

| Gesture (expanded) | Action |
| --- | --- |
| Tap outside the card | Collapse |
| Swipe up | Dismiss the activity |
| Swipe left / right | Page through activities |

This is Apple's mapping exactly. Smart Island previously treated tap-to-expand as
primary and, because two gesture detectors were both live ancestors of the same
pointer, fired **both** tap-to-open and tap-to-collapse on one tap (AUDIT §5.1).

### 3.2 Card proportions — iOS, inset equally on top and sides

- Side and top inset: **12 dp**, matching the pill's current default y offset
  (33 px ÷ 2.75 = 12 dp) so the card's top edge lands exactly on the pill's and the
  morph begins with zero displacement.
- Card width: `screenWidth − 2 × 12dp` = **421 dp** on this device.
  HIG-equivalent: HIG yields 371–408 on 393–430 pt screens; ours is 421 on 445 dp.
- Card height: clamped to **84–160 dp** by content.

### 3.3 Live Activity allowlist — the island is not a second shade

A Dynamic Island is a glanceable surface for a small number of *ongoing*
activities. It is not a mirror for the notification shade.

Smart Island originally promoted every notification into the island and, because
`shouldBeIslandOnly` returns true for most modes, **cancelled it from the system
shade**. That turned a status surface into an inbox: a message arriving while
music played would displace the music and replace it with itself.

`SmartIslandSettings.liveActivityAppsOnly` (default **on**) restricts the island
to:

- an explicit package allowlist (`liveActivityPackages`), seeded with Spotify,
  Dodo Pizza and the common dialers; and
- modes that are inherently *ongoing* rather than discrete events — incoming
  calls (always, regardless of package), media playback, timers, stopwatches,
  navigation and screen recording.

Anything else is left completely alone: never shown in the island, and — critically
— never cancelled from the shade, so it arrives as an ordinary notification.
With music playing and a message arriving, the music stays and the message
appears normally.

Returning early in `handleNotificationPosted`, before `shouldBeIslandOnly` is
ever reached, is what guarantees the notification is not suppressed. Seven tests
cover this in `LiveActivityAllowlistTest`.

### 3.4 Battery island removed

The battery island mirrored charging state, low battery and battery saver into a
`system_battery` entry. That duplicated the status bar, which shows the same
information permanently and more accurately, and it occupied the one surface
meant for activities the user actually initiated.

Removed: `IslandMode.Battery`, `BatteryExpanded.kt`, `BatteryCollapsedGlyph`, the
demo fixture, the settings toggle and the `enableBatteryMode` preference, and the
battery branches of every dispatch block. Battery broadcasts are still consumed,
but only to clear a stale entry from an older install.

---

## 4. The core problem this redesign must solve

### 4.1 What is wrong today

Smart Island keeps the collapsed pill on screen **and** renders a second, visually
independent rounded panel below it. Two surfaces, two backgrounds, no continuity.
On iOS there is exactly **one** shape whose bounds grow.

### 4.2 The window-geometry tension

The touch model and the morph want opposite things:

| Window | Pass-through | Morph |
| --- | --- | --- |
| Pill-sized | Native, via `FLAG_NOT_TOUCH_MODAL` | Window jumps to full-screen on expand |
| Full-screen | Blocked; needs a carved touch region | Smooth in-Compose morph |

A carved touch region does not necessarily require hidden APIs: Android 13+
provides `AttachedSurfaceControl.setTouchableRegion`. The old reflection-based
implementation remains removed; the current implementation uses small collapsed
window bounds and public `FLAG_NOT_TOUCH_MODAL` instead.

### 4.3 Original proposal — animate the window bounds (not implemented)

The expanded card is only ~421 × 160 dp. It never needed to be full-screen *during*
the transition. So:

1. **Collapsed** — window is pill-sized. Native pass-through.
2. **Expanding** — window bounds animate pill → card over the morph duration, while
   Compose animates the shape and cross-fades the content. Pass-through stays native
   the whole time because the window is never larger than the shape.
3. **Expanded** — once the card has settled, the window snaps to `MATCH_PARENT`. This
   is a visual no-op, since the card is already inset and top-anchored. It exists
   only so that "tap outside to collapse" can intercept touches.
4. **Collapsing** — the window stays `MATCH_PARENT` for the duration of the collapse
   animation, then snaps back to pill-sized.

The original 220 ms collapse timeout was shorter than the content fade and was
not reliable under frame delays or animation-duration scaling. It has been
replaced with actual Compose transition completion. Per-frame window resizing
is deferred: it would add repeated system relayout to the coordinate handoff.
See `docs/TRANSITION_INVESTIGATION.md` for the implemented alternative and research.

---

## 5. Phases

### Motion

Springs are symmetric, so a single spec means the same feel in both directions.
The Dynamic Island overshoots slightly on the way in and **settles** on the way
out — a bounce on collapse reads as wrong.

| Direction | Damping ratio | Stiffness | Feel |
| --- | --- | --- | --- |
| Expanding | 0.86 | 520 | small overshoot |
| Collapsing | **1.0** (critically damped) | 780 | no overshoot, settles ~200 ms |

The expanded window now remains large until the transition is actually idle.
Expansion waits for enlarged window layout before starting the morph. Springs
use screen-space targets, converted to actual window-local coordinates in layers;
resizing no longer deliberately changes those targets. Android's independent
window-move animation is disabled on API 34+.

### Phase A — geometry and window-bounds morph
- `computeExpandedCardGeometry(...)` — pure, unit-tested, equal top/side inset
- `interpolateWindowBounds(collapsed, expanded, fraction)` — pure, unit-tested
- Service drives `updateViewLayout` across the transition, then snaps to
  `MATCH_PARENT` on settle
- Invariants: the window is never larger than the drawn shape, so the pill is never
  clipped and pass-through never regresses

**Status: partly done.** The *inset* half is complete — `calculateExpandedWidth`
now subtracts a fixed margin instead of scaling by 95%, and
`calculateExpandedTopOffset` returns that same margin, which removed the 30dp
downward jump. Per-frame *window-bounds animation* is not implemented. Instead,
the handoff now prepares `MATCH_PARENT` bounds before the Compose morph and
shrinks after completion. Smoothness is not yet established across all modes.

### Phase B — one continuous shape
- Single rounded-rect whose bounds and corner radius interpolate
  pill → card; corner radius morphs from capsule to card radius
- Compact content and expanded content cross-fade **inside** that one shape, with a
  short stagger
- Removes the second panel entirely

**Status: mostly done.** The container already interpolated its bounds and corner
radius; what made it read as two surfaces was that the top offset jumped, the
companion bubbles drew on top, and both content layers faded on the same curve.
All three are fixed. The compact content now also shrinks slightly as it fades, so
it reads as being absorbed into the growing shape.

### Phase B — one continuous shape
- Single rounded-rect whose bounds and corner radius interpolate
  pill → card; corner radius morphs from capsule to card radius
- Compact content and expanded content cross-fade **inside** that one shape, with a
  short stagger
- Removes the second panel entirely

### Phase C — gestures
- One gesture owner replacing the two competing detectors (AUDIT §5.1, §5.2)
- `rememberUpdatedState` for `isInputActive` and settings, fixing the stale-capture
  bug (AUDIT §5.3)
- Hold job moved into the pointer-input scope so an aborted gesture cannot vibrate
  (AUDIT §5.4)
- Pointer cancellation handled

**Status: done**, except that the two detectors have not been merged into one
owner. The double-fire is fixed by an explicit hit test against the card rect,
which is behaviourally equivalent and lower risk than a full rewrite, but the
structure still has two gesture layers.

### Phase D — per-mode cards at iOS proportions
- Re-fit all 13 expanded cards into 84–160 dp using the HIG region model
  (leading / trailing / centre / bottom)
- Long message bodies scroll or truncate rather than growing the card
- Consolidate the seven duplicated `IslandMode` dispatch blocks into one registry
  (AUDIT §5.10) as part of the rework

**Status: partly done.** The card height is now clamped to the HIG's 84–160dp
range, and page content is vertically scrollable so clamping cannot make overflow
content (long notification bodies, the inline reply field) unreachable. Measuring
natural height on an inner unconstrained element is what stops the clamp feeding a
capped viewport height back into the measurement.

The 13 cards themselves are not yet re-fitted. Most visibly, `BatteryExpanded`
floods the whole card with a green gradient where iOS keeps the card black with
green accents.

---

## 6. Constraints carried forward from the audit

These are settled and must not regress:

- No hidden API usage anywhere (§4.1).
- No animation value read during composition — read in the draw phase (§2.3).
- No per-composition binder IPC (§2.2).
- No polling that runs while the screen is off (§2.1).
- Window margin must exceed the collapsed drag clamp, or the pill clips mid-drag
  (§4.1, 28 dp margin vs 24 dp clamp).

---

## 7. Explicitly deferred

Nothing OS styling: dot-matrix glyphs, monospace labels, hard black/white with a
red accent, pixelated controls. Deferred until the iOS interaction and geometry are
correct, per the stated priority. Nothing about the information architecture or
interaction model above conflicts with that later work — only the surface treatment
changes.

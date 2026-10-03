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

A carved touch region requires the hidden `OnComputeInternalInsetsListener` API,
which AUDIT §4.1 removed because it is blocked on Android 14+ and is a Play policy
violation. That fix must not be undone.

### 4.3 Resolution — animate the window bounds

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

Step 4 is already how the code behaves today via the 220 ms
`AUTO_COLLAPSE_DELAY_MS` in `SmartIslandOverlayService`; that trick is being reused
for step 2.

---

## 5. Phases

### Phase A — geometry and window-bounds morph
- `computeExpandedCardGeometry(...)` — pure, unit-tested, equal top/side inset
- `interpolateWindowBounds(collapsed, expanded, fraction)` — pure, unit-tested
- Service drives `updateViewLayout` across the transition, then snaps to
  `MATCH_PARENT` on settle
- Invariants: the window is never larger than the drawn shape, so the pill is never
  clipped and pass-through never regresses

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

### Phase D — per-mode cards at iOS proportions
- Re-fit all 13 expanded cards into 84–160 dp using the HIG region model
  (leading / trailing / centre / bottom)
- Long message bodies scroll or truncate rather than growing the card
- Consolidate the seven duplicated `IslandMode` dispatch blocks into one registry
  (AUDIT §5.10) as part of the rework

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

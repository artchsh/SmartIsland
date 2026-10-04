# Dot Island visual and motion language

Current direction: iOS-like geometry and interaction adapted to this phone's
actual camera, with a monochrome, Nothing-inspired indicator/typography layer.
No added red accent, proprietary fonts or branded assets.

## Personal build — current scope

This is exclusively for the connected A069 on Android 16/API 36, not a public
product. The app has one monochrome control screen, one persisted enable flag,
a live compact preview and contextual permission-repair links. No settings tabs,
history/database, shortcuts/recent-app access, backup/import/export, update feeds,
network requests, Shizuku integration, OEM selectors or color/geometry editors.
The fixed code profile is 112 × 34dp, centred, 12dp from the top. Runtime density,
cutout and window-origin conversion are retained; fixed profile is not fixed pixels.
Lock-screen and landscape presentation remain disabled, as on this phone before
the redesign. Device access/background checks and reboot recovery remain.

Only Spotify sessions, native calls from Google Dialer/Telecom, and Dodo order
notifications (`ru.dodopizza.app`) enter the island. Notification ingestion never
cancels system notifications or replays their sounds. Call/delivery/music priority
is explicit, with at most one representation per activity type. No other activity
modes or regular notification cards remain. Original artwork/icons retain color.

Dodo filters common English/Russian order states and promotions, displays source
text and only source-provided ETA/progress, and is marked **UNVERIFIED** in the
app until a real delivery has been checked. This is not a claim of tested Dodo
integration. There is no invented 65% progress or estimated delivery time.

True idle has no expanded launcher/card. Its standby matrix does a 600ms diagonal
wink after 9 seconds of rest; no frame clock runs during the delay. Lifecycle
pause cancels it; a burst is skipped when the screen is off, battery saving is
active or system animations are disabled. `IDLE` and the pill remain stationary.

Older sections below record the evolution of geometry and motion. The personal
scope above supersedes suggestions for configurable shadows, optional shortcuts,
theme presets and static-only standby presentation.

## Surfaces and hierarchy

- One pill morphs into one card. No independently appearing popup surface.
- Plain black surfaces: no album-art washes, gradients or decorative colored glows.
- Subtle neutral 1 dp outline (`white × 40/255`); configurable shadow remains optional.
- Artwork/app icons retain their original colors. Functional status colors and
  button/progress backgrounds remain; “monochrome” does not erase useful state.
- Compact and expanded music activity use a white 5 × 7 dot matrix, smoothly
  dimmed rather than stepped. Paused dots remain static. Legacy visualizer-color
  preferences do not tint this matrix or the artwork fallback.
- Artist/timing labels use `#8E8E93`. Times use the system monospaced font;
  song/caller names retain readable sans text.
- Titles use readable semibold sans; supporting text is lighter and muted.
- Playback is previous / play-pause / next, with a small audio-output shortcut.
  Like/repeat controls are omitted from the compact expanded presentation.
- Elapsed time is on the left; remaining time counts down on the right.
  Seek track is 7 dp, played `#9C9BA2`, remaining `#242425`, with no thumb or wave.

## Artwork and spacing

- Expanded music artwork is **64 × 64 dp**.
- **20 dp content insets on all four sides**, measured from the card's outer bounds.
- Artwork uses a squircle: mathematical superellipse with exponent 4. This is
  not a claim to reproduce Apple's proprietary continuous-corner implementation.
- The static outline is sampled once when the clip size changes; no per-frame
  squircle path construction is required for the fixed artwork size.
- Header-to-timeline gap is 4 dp. Timeline is 24 dp tall; playback targets are 48 dp.
  Together with the 64 dp header and 20 dp top/bottom insets, the default body is 180 dp.
- Music deliberately has a 180 dp budget rather than the prior generic 160 dp cap:
  balanced whitespace, full-size artwork and 48 dp buttons take precedence over
  treating iOS points as a strict Android layout ceiling. Other modes retain their
  existing 84–160 dp budget.

## Camera-aware layout

- Outer top/side card margins remain 12 dp in portrait.
- Prefer public cutout-path bounds over the larger conservative inset rectangle.
  On the connected phone: path `[578.2, 47.6 – 645.8, 115.5]` px;
  inset rectangle `[559, 0 – 665, 162]` px, at effective density 2.75.
- Position header items independently. Side artwork and activity bars stay high
  unless their own bounds overlap a cutout. Overlapping text clears it by at
  least one physical pixel, rounded outward to integer layout pixels.
- Use final screen-space geometry, not per-frame state feedback from layout.
  Music content has no separate slide/scale that could undo its tight placement.
- Older-device/unknown-geometry fallback stays conservative. Other modes retain
  their non-scrolling full-top reservation. Extra camera reservation is outside
  their usable body-height budget.
- Large font sizes, split activity paging and rotation need continued device
  checks; a small camera gap must never take priority over readable content.

## Interaction and motion

- Single tap opens the source app; hold expands while the finger remains down.
- Expansion uses a light 12 ms pulse at amplitude 64/255, rather than the old
  60 ms default-amplitude buzz. Hardware without amplitude control may ignore
  strength, but the requested duration remains short. Outside tap collapses.
  Other swipe actions retain their configured behavior.
- Expansion spring has a small overshoot; collapse is critically damped, not bouncy.
- Enlarge and acknowledge window layout before starting the morph. Shrink only
  after actual transition completion, not a fixed timeout.
- Disable Android's independent window move animation on API 34+.
- Native collapsed pass-through uses small window bounds and `FLAG_NOT_TOUCH_MODAL`.
- All expanded contents fade out immediately on collapse, in 100 ms with no
  entry delay. Expansion keeps its 70 ms delay / 200 ms fade-in to allow room.
- Compact activity changes use a 180 ms fade-in / 100 ms fade-out, with no
  content slide or size animation. Identity is the notification key, not playback
  position/title, so routine metadata updates do not restart the fade.
- Leaving Spotify fades its content back in; entering fades it out. The pill
  remains blank inside Spotify, rather than incorrectly saying playback is idle.
- True idle uses a quiet, static standby dot mark and monospaced `IDLE` label
  in the side slots, clear of the central camera. No idle pulse, fake waveform,
  battery status or fabricated activity. Existing hide-when-idle takes precedence.

## 120 Hz target

- Target an 8.33 ms frame interval on 120 Hz hardware; do not change the user's
  display settings or promise that battery saver/thermal policy cannot override it.
- Expanded/transitioning window requests the highest same-resolution supported
  refresh rate up to 120 Hz. Release the hint once the window becomes small.
- Animated dimensions are read in layout; radius, transforms and fading in
  layers/drawing. Visibility thresholds are derived booleans rather than root
  composition subscriptions to every alpha update.
- Clip, transforms and optional shadow share one main graphics layer.
- Compact glyph motion reads alpha in layers. Expanded content is measured at
  final width instead of being rebuilt at every intermediate morph width.
- Music activity dots share one draw-phase clock per visible indicator; no animation clock is
  created while paused. They indicate playback activity, not sampled audio levels.
- Media-position telemetry stays at 10 Hz in its own composable; it is not the
  window animation clock. Do not poll media IPC at 120 Hz to animate a surface.
- Benchmark cold and warm runs separately. A debug APK, JIT warmup, artwork changes,
  refresh policy and other foreground/background work can all affect observations.

### Device observation, 2026-10-04

Same six-cycle long-press/outside-tap sequence; actual Spotify card verified in
screenshots, playing in both runs. Display was already 120 Hz before the changes.
Track/artwork changed naturally between runs, so this is not an isolated A/B or a
measurement of each optimization's contribution.

| Run | Rendered frames | Janky | p95 | p99 | Missed vsync |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before | 871 | 257 (29.51%) | 57 ms | 150 ms | 219 |
| Updated | 1409 | 63 (4.47%) | 19 ms | 32 ms | 25 |
| Final balanced insets + short haptic | 1382 | 74 (5.35%) | 20 ms | 38 ms | 33 |

The bounded latest overlay frame-history sample had 120 frames with an 8.33 ms
frame interval in both runs. Valid positive presentation gaps at most 9 ms:
**73/115 before**, **113/117 updated**. Updated gaps included two 25 ms, one
33.3 ms and one 16.7 ms interval: occasional misses remain. This sample includes
continuous music activity-bar frames and is not exclusively transition frames.
Do not describe it as a guaranteed, sustained locked-120 result.

Final installed build's latest sample had 112/115 valid positive presentation
gaps at most 9 ms, with median 8.333 ms. Build, lint and 196 unit tests passed.
Device vibrator history confirmed `Step=12ms(amplitude=0.25)` for expansion.
Its longer reported total session duration includes driver/framework handling;
it is not evidence that physical motor output lasted exactly 12 ms.

Raw stats/screenshots are retained in the harness temp directory under
`smartisland/fps_baseline*`, `fps_updated*` and `display_refresh_before.txt`.

### Dot-matrix/content-fade follow-up

Build, lint and **202 unit tests** passed. Device screenshots verified the music
matrix/monospaced timeline, blank content inside Spotify, and restored content
on the launcher. A recording of collapse and Spotify return was reviewed at
sampled 10fps for content sequencing, not as a 120Hz motion assessment. True
idle's presentation is implemented separately from foreground suppression; it
was not forced on the device by stopping Spotify or clearing the user's activities.

Six further real-Spotify cycles: 1370 frames, 78 janky (5.69%), p95 21ms, p99
44ms, 34 missed vsyncs. Latest valid presentation-gap sample: 113/117 at most
9ms, median 8.333ms. These are observational checks, not controlled A/B evidence
or a sustained locked-120 guarantee. Art/track can change naturally between runs.
Artifacts: `fps_dot_content.txt`, `dot_content_motion.mp4`, `dot_*music.png`,
`dot_spotify_suppressed.png` in the same harness temp directory.

## Nothing-inspired direction

1. **Glyph-like activity indicators:** white dot matrices now used for music and
   a static standby mark for idle. Surface motion remains smooth.
2. **Technical microtype:** system monospaced times for music, calls, timers and
   stopwatch, plus the idle label. Names and long content remain accessible sans.
3. **No red theme accent:** explicitly declined. Original artwork and existing
   functional state/control colors are not recolored as decoration.
4. **Hardware-like framing:** a fine neutral outline, optional minimal shadow, consistent
   circular/squircle glyph containers and disciplined spacing. No glass blur,
   gradient wash, fake screw heads or visual noise.
5. **Future optional preset:** these refinements apply to the current island;
   a separate user-selectable theme remains a future settings feature, sharing
   camera-safe geometry and motion. No proprietary Nothing assets are used.

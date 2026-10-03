# Smart Island — Engineering Audit

**Date:** 3 October 2026
**Scope:** full repository review — build, CI, data layer, service layer, UI layer, tests, resources, documentation
**Target device for this fork:** Nothing Phone (4a), codename `Frogger`, model `A069`, Android 16 (API 36), Nothing OS `B4.1-260808-1352`, 1224×2720 @ 480dpi (2.75 effective density)

This document is the register of everything found during the review. Items are
grouped by area and marked with their disposition. File and line references point
at the code as it stood at the start of this work unless noted.

Legend:

- **FIXED** — corrected in this change set
- **OPEN** — real problem, not yet addressed
- **DOCUMENTED** — recorded here for future work, not a defect
- **REJECTED** — investigated and found not to be a problem

---

## 1. Fixed in this change set

### 1.1 Privacy policy stated something false

`PRIVACY.md:30` claimed:

> Does not request the Android `INTERNET` permission.

`app/src/main/AndroidManifest.xml:11` declares it, and it is present in the
published APK's manifest. A published privacy policy that misdescribes the
app's own permissions is a compliance problem, not a documentation nit.

**FIXED.** Rewritten to include a permissions table covering all eleven declared
permissions, an explicit statement that `INTERNET` is used only for the opt-in
update check and is off by default, and an explanation of why the app runs an
`AccessibilityService`.

### 1.2 Security policy was four major versions stale

`SECURITY.md:5,9,11` stated that fixes covered "the current v3 release", with a
support table reading `3.x | Yes`, `< 3.0 | No`. The app is **v7.0.0**. The
policy actively told reporters that v4 through v7 were unsupported.

**FIXED.** Corrected to `7.x` / `main`, with a note about the historical
numbering scheme.

### 1.3 Notification history was being backed up to the cloud

`AndroidManifest.xml:36` set `android:allowBackup="true"` but referenced
**neither** `android:fullBackupContent` **nor** `android:dataExtractionRules`.
Both rule files existed and were unreferenced, so both were inert:

- `res/xml/backup_rules.xml` was `<full-backup-content />` — an *empty*
  element, which means "back up everything", not "back up nothing".
- `res/xml/data_extraction_rules.xml` was additionally **schema-invalid**: a
  `<data-extraction-rules>` document requires both a `<cloud-backup>` and a
  `<device-transfer>` child, and the `<device-transfer>` element was absent
  entirely, so the platform rejected the whole document.

Net effect: on Android 11 and below the SQLite database containing notification
titles, message bodies and app names was included in Google Drive backup. This
directly contradicted `PRIVACY.md:39` and the "100% on-device" claim repeated in
`README.md:40` and five other files.

**FIXED.** `allowBackup="false"`, both attributes wired up, and both rule files
rewritten to explicitly exclude every backup domain from both the cloud and
device-transfer paths. Explicit exclusion is kept even though `allowBackup=false`
already covers AOSP behaviour, because some OEM implementations honour the rules
in preference to the flag.

### 1.4 Releases could be silently overwritten

`.github/workflows/android.yml` ran the `publish` job on **every push to
`main`**, not just on tags:

```yaml
if: ${{ github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v') }}
```

`TAG` was reconstructed as `v$versionName` from `build.gradle.kts`, and the
upload used `--clobber`. Any commit landing on `main` without a `versionCode`
bump therefore replaced the already-published release asset. Users are told by
the in-app update checker that v7.0.0 is current, so nobody re-downloads, and
anyone who downloaded early ends up with a binary that does not match the tag.

**Verified not yet realised:** `git rev-list -n 1 v7.0.0` equals `HEAD`
(`aea9fb9`) and there are zero commits after the tag, so the published v7.0.0
asset does currently correspond to the tagged source.

**FIXED.** Publishing is now tag-only, `--clobber` no longer overwrites an
existing release, and a `.sha256` checksum file is generated and published
alongside every APK.

### 1.5 Tag pushes could be silently skipped

`paths-ignore` was applied to the `push` event, which includes tag pushes. A
release commit touching only markdown — a version bump plus a changelog entry,
no code change — caused the `v*` tag push to be filtered out, producing no
release, no failing job, and no log line explaining the absence.

**FIXED.** `paths-ignore` now applies to `pull_request` only. Every push is
verified.

### 1.6 ProGuard kept every member of two entire libraries

`app/proguard-rules.pro` contained:

```
-keep class androidx.compose.** { *; }
-keep class androidx.datastore.preferences.core.** { *; }
```

Neither library needs an app-level keep; both ship comprehensive consumer rules
that already tell R8 how to shrink them. Keeping every member of both packages
inflated the release APK and defeated R8 class merging and repacking for the two
largest dependency trees in the app.

**FIXED.** Both removed, with a comment explaining why they must not come back.
The genuinely reflective members (`OnComputeInternalInsetsListener`,
`ActivityOptions.setLaunchWindowingMode`, `MediaController` repeat mode,
`Rating.isHearted`, `MediaMetadata.getRating`, `PlaybackState` custom actions)
were already narrowly and correctly specified and are unchanged.

**Measured effect.** A/B tested with two release builds from the same tree,
unsigned so signing is not a variable:

| Build | Release APK |
| --- | --- |
| With the two blanket keeps (upstream behaviour) | 8,249,674 bytes |
| Without them (this fix) | 2,370,948 bytes |
| **Reduction** | **5,878,726 bytes — 71.3%** |

The with-keeps build also reproduces the published v7.0.0 asset (8,258,734 bytes)
to within 9 KB, the difference being the signing block, which confirms the
baseline is sound. This is the single highest-value change in the audit: it cuts
download size, install time and on-disk footprint by roughly three quarters.

### 1.7 A test that could not fail

`OemAutostartUtilTest` was:

```kotlin
val result = OemAutostartUtil.openAutostartSettings(context)  // returns Boolean
assertNotNull(result)
```

`assertNotNull` on a non-nullable primitive can never fail. The test passed even
when the function returned `false` on every code path.

**FIXED.** Replaced with six behavioural tests asserting the actual contract: the
ladder is walked in order, it stops at the first intent that resolves, a mid-ladder
failure does not abort the walk, and a device where nothing resolves reports
failure honestly.

### 1.8 A test that re-implemented the code it was testing

`IslandOverlayLayoutTest.splitModeCircleFitsWithinWindowBounds` duplicated the
layout arithmetic from `IslandOverlayView` inside the test file, because the
production math was embedded **inside a `@Composable`** and therefore unreachable
from a JVM test. The test asserted that its own copy agreed with itself and
stayed green regardless of what the composable did.

The copy had already drifted from production: production has a three-branch
`when` including a `!hasCompanion` case, the copy had two branches, so **the
single-notification case was never exercised at all**.

**FIXED.** The math was extracted into `internal fun calculateCollapsedLayout`
returning a `CollapsedLayoutGeometry`, taking and returning plain `Float` dp
values. The composable now calls it. The test calls the real function and covers
the previously-missing no-companion branch, extreme x offsets in both
directions, notch mode, and full-width mode. Test count for that class went from
4 to 7.

### 1.9 Environment-only tests

`ShizukuManagerTest` asserted that Shizuku was *absent*, which passes on any CI
machine and would still pass if the functions were hardcoded `return false`.

**FIXED.** `isInstalled` gained the missing positive-branch test (only the
negative branch was covered, so an unconditional `return false` regression would
not have been caught). The two binder probes are retained but explicitly
relabelled as "does not crash on a Shizuku-less device" smoke checks rather than
behavioural coverage, because that is all they are.

### 1.10 Motorola devices were sent to an ASUS settings screen

`OemDeviceRules.getAutostartIntents` mapped `OemDeviceType.MOTOROLA` to
`com.asus.mobilemanager.autostart.AutoStartActivity`. That component cannot exist
on a Motorola device, so the "Fix Kills" button silently did nothing.

**FIXED.** Removed. Motorola has no dedicated autostart screen and now falls
through to the generic App Info intent.

### 1.11 A null intent could disable the entire OEM ladder

Found while writing the replacement tests. `getAutostartIntents` built entries
with `Intent().setComponent(ComponentName(...))`. `Intent.setComponent()` returns
`Intent` *for chaining*, so any caller storing that return value stores `null`.
`safeStartActivity` then dereferences the intent to build its log message
(`"Failed to open ${candidate.action}"`), throws, and `openAutostartSettings`
catches it and returns `false` for the **entire device** — every OEM intent after
the bad entry is skipped.

**FIXED.** All 19 entries converted to the property-setter form
(`Intent().apply { component = ... }`), which has no such failure mode, plus a
`filterNotNull()` on the way out.

### 1.12 The overlay was never themed

`SmartIslandTheme` was referenced only from `MainActivity.kt:32` (the settings
screen) and two `@Preview`s. The overlay composition — `SmartIslandOverlayService`
→ `OverlayIsland` → `IslandOverlayView` → `IslandExpandedContent` — never
installed a theme.

The overlay contains exactly one `MaterialTheme.*` reference,
`NotificationExpanded.kt:281`:

```kotlin
if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary else Color(0xFF333333)
```

With no theme installed, `LocalColorScheme` falls back to the M3 baseline
**light** scheme, so the Send Reply button rendered **Material purple**
(`0xFF6750A4`) in a burnt-orange app. Everything else in the overlay bypasses the
theme and hardcodes literals.

**FIXED.** Added `ThemedOverlayIsland`, which wraps `OverlayIsland` in
`SmartIslandTheme(darkTheme = true)`. Dark is correct unconditionally: the
island is a black surface floating over arbitrary app content and does not
follow the system light/dark setting.

### 1.13 Every overlay control was invisible to assistive technology

`Modifier.bounceClick` was implemented as a bare
`pointerInput { detectTapGestures { ... } }` inside `Modifier.composed`. A raw
`pointerInput` emits no `SemanticsModifier`, so every node using it had **no click
action, no `Role`, and no bounds**. Verified counts across the codebase:

```
Modifier.semantics:  0 sites
bounceClick:        50 sites
derivedStateOf:      0 files
snapshotFlow:        0 files
```

Affected: media transport, timer pause/stop, stopwatch lap/pause/reset, all
notification action chips and inline reply, call answer/decline, hotspot toggle,
download/navigation/live-activity actions, and the primary controls on the
settings screens. It also used `Modifier.composed`, deprecated since Compose 1.2.

This is not a case of degrading a redundant path. The island is already
gesture-only: hold-to-clear-all, swipe-to-shade and swipe-to-floating-window have
no button equivalent anywhere, so before this fix a TalkBack or Switch Access user
could not perform those actions at all.

**FIXED.** Rebuilt on `Modifier.clickable`, which contributes proper semantics and
role, with the press animation driven from an `InteractionSource`. Added optional
`enabled`, `onClickLabel` and `role` parameters. `Modifier.composed` is gone.

### 1.14 Lock screen privacy leaked five of thirteen modes

`OverlayIsland` redacted only `IslandMode.Notification`. With lock screen privacy
set to "App icon only", the titles and bodies of **LiveActivity** (courier name,
order contents), **DownloadUpload** (file names), **Navigation** (street
addresses), **IncomingCall** (caller) and **Timer** (alarm label) remained fully
readable.

Worse, the redaction happened in the UI layer while
`SmartIslandOverlayService` read the **unredacted** list at three separate sites.

**FIXED.** All modes are now redacted. The `"AppIconOnly"` / `"FullContent"`
magic strings, previously duplicated across four files with the authoritative
validation set hidden in a private companion, are now
`SmartIslandSettings.LOCK_SCREEN_APP_ICON_ONLY` and `LOCK_SCREEN_FULL_CONTENT`.

### 1.15 `gradlew` was not executable

Committed with mode 644, so `./gradlew` failed with `permission denied` on any
POSIX clone.

**FIXED.** `chmod +x`, recorded in git.

---

## 2. Performance and battery

This was the stated priority for this fork. Findings are ordered by expected
impact.

### 2.1 FIXED — the overlay kept collecting and animating with the screen off

`OverlayIsland` used `collectAsState()` for six flows. That keeps the composition
live for the entire window lifetime. `SmartIslandOverlayService` already pauses
`overlayOwners` on `ACTION_SCREEN_OFF` and resumes on `ACTION_SCREEN_ON`, but
plain `collectAsState` ignores the lifecycle entirely. The overlay therefore kept
collecting flows — and running every `infiniteRepeatable` animation (audio
visualiser, battery dotted ring, transfer arrows, media waveform) — with the
screen off.

**FIXED.** All six now use
`collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)`.

`RESUMED` is required, not `STARTED`: pausing moves the registry to `STARTED`,
which is *at or above* the default threshold, so the default would have changed
nothing. All six are `StateFlow`s, so suspending collection loses nothing — the
latest value is replayed on resume.

The `minActiveState =` named argument is load-bearing. There are two overloads;
the plain-`Flow` one takes `initialValue` first, so passing
`Lifecycle.State.RESUMED` positionally selects it and infers `T` as
`Boolean & Lifecycle.State`.

### 2.2 FIXED — the music card recomposed 33 times a second, with a binder IPC per recomposition

`MusicExpanded` ran:

```kotlin
LaunchedEffect(controller, positionMs) {
    while (true) {
        if (...) livePositionMs = getEstimatedPosition(controller, positionMs)
        kotlinx.coroutines.delay(30)   // 33fps
    }
}
```

`livePositionMs` was read during composition, so the **entire music card
recomposed 33 times a second**, writing unconditionally even when the value had
not changed.

Worse, album art was resolved with:

```kotlin
remember(controller, controller?.metadata) { ... }
```

The second key is a **binder IPC call into the media session, evaluated on every
composition** — so roughly 33 cross-process calls per second for as long as music
played.

**FIXED.**
- Poll interval raised to 100ms (10fps). Indistinguishable from 30ms for a seek
  bar; ~70% fewer coroutine wakeups and recompositions.
- The position write is now guarded by an actual change check.
- Album art moved into snapshot state updated from the existing
  `MediaController.Callback.onMetadataChanged`, and the `remember` is keyed on
  that state. The per-composition IPC read is gone.

### 2.3 FIXED — animation values read in composition forced 60fps recomposition

Three places read an `infiniteTransition` animated value during composition and
then used it as a plain parameter, invalidating the whole composable every frame:

| Location | Problem |
| --- | --- |
| `IslandCollapsedContent` `AudioVisualizer` | `heightFraction` read via `by`, passed to `graphicsLayer` → `Row` recomposed at display rate |
| `IslandCollapsedContent` `DottedRing` caller | `rotationAngle` read via `by`, passed as a plain `Float`; `DottedRing` also recomputed 16 `sin`/`cos` pairs **per frame** |
| `IslandCollapsedContent` `CustomPremiumTransferIcon` caller | `motionFraction` and `arrowAlpha` read via `by`, passed as `Float` → whole transfer glyph plus its ring progress recomposed at display rate for a 1.4s loop |

**FIXED.** All three now pass the animation `State` (or a lambda reading it) so
the value is read in the **draw phase**. `DottedRing` additionally moved its
rotation into a `graphicsLayer` transform, since a rotation is a pure GPU
operation: the dots are now laid out once and rotated on the GPU, eliminating both
the recomposition and the per-frame trigonometry.

### 2.4 FIXED — no memory-pressure handling existed anywhere

`grep -rn "onTrimMemory\|onLowMemory" app/src/main` returned nothing. That matters
because:

- `SmartIslandNotificationListenerService` caches launcher icons in an
  `LruCache<String, Bitmap>(50)`.
- `SmartIslandNotificationRepository` retains up to 50 `IslandNotification`s in a
  process-lifetime singleton `MutableStateFlow`, each of which may hold a
  **full-resolution** album-art bitmap. Media session artwork is routinely
  1000×1000 or larger, so a handful is several megabytes.
- `Bitmap.recycle()` is never called anywhere in the project.

Nothing was ever released in response to the system asking for memory back, which
made this the most likely OOM path.

**FIXED.** `onTrimMemory` added to the listener service: `evictAll()` the icon
cache at `TRIM_MEMORY_RUNNING_LOW` and above, and additionally clear the
in-memory notification list at `TRIM_MEMORY_COMPLETE`. Both caches rebuild lazily.

### 2.5 FIXED — regexes compiled inside composable bodies

Eleven `Regex(...)` / `Pattern.compile(...)` calls were made **inside composable
functions**, i.e. on every recomposition:

`TimerExpanded.kt` (×2, in a composable with a 500ms ticker),
`BluetoothExpanded.kt`, `DownloadExpanded.kt` (×2), `HotspotExpanded.kt` (×2),
`LiveActivityExpanded.kt`, `NavigationExpanded.kt`,
`IslandCollapsedContent.kt` (×3).

Additionally `TimerStopwatchParser.parseTimeStringToSeconds` compiled **three**
`Pattern`s per call whenever the primary time pattern missed, and that function
runs for every timer and stopwatch the listener classifies.

**FIXED.** All hoisted to top-level `private val` constants.

### 2.6 FIXED — a `SimpleDateFormat` allocated per call

`formatNotificationTime` constructed a new `SimpleDateFormat` and `Date` on every
invocation, and is called from `NotificationExpanded`, `DownloadExpanded` and
`HotspotExpanded` — all behind recomposing overlays. `SimpleDateFormat` is also
not thread-safe, and this is reachable from the listener's `Dispatchers.Default`
pool as well as the main thread.

**FIXED.** `ThreadLocal.withInitial`, which is both cheaper and correct across
the listener pool.

### 2.7 OPEN — the battery glyph toggles on a permanent 3-second timer

`IslandCollapsedContent` contains an unconditional:

```kotlin
LaunchedEffect(Unit) { while (true) { delay(3000); showBattery = !showBattery } }
```

This runs forever in the always-composed collapsed layer and drives an
`AnimatedContent` that **tears down and rebuilds its subtree every 3 seconds**,
for as long as a battery notification is in the island. The battery level and
charging state are already pushed in as parameters, so the alternation does not
need to poll at all.

Recommended fix: drive the alternation from the actual battery value or charging
state transition, or move it behind an explicit user setting.

### 2.8 OPEN — `WavyMusicSeekBar` allocates a `Path` vertex per pixel of progress

`WavyMusicSeekBar.kt:150-165` walks the progress width one pixel at a time,
evaluating `sin` + `cos` and calling `lineTo` for each vertex — roughly 1000
vertices for a 360dp bar at 2.75 density. It is redrawn on every frame of both
its own 667ms `infiniteRepeatable` and the position poll.

Phase is correctly read inside `Canvas {}`, so it does not cause recomposition,
but the per-frame trigonometry and path re-allocation remain. `endX.toInt()` on a
`Float` derived from `progress` makes the vertex count jitter.

Recommended fix: sample at a fixed step (for example every 3–4dp) rather than per
pixel, or precompute the wave shape and translate it.

### 2.9 OPEN — the height feedback loop re-animates on every layout pass

```
IslandOverlayView      onSizeMeasured  -> writes expandedHeight
IslandExpandedContent  onSizeChanged   -> writes pageHeights
IslandExpandedContent  reads pageHeights in composition
```

`pageHeights` is read in composition and fed back into `expandedHeight`, which is
read inside an `animateDp` transition. There is an equality guard so it converges
in the steady state, but any `Dp` jitter — font-scale change, or the soft-keyboard
resize triggered by inline reply — creates a measure → set-state → re-animate
cycle. `pageHeights.toMutableMap()` also allocates a fresh map on every update.

Worse, `pagerState.currentPageOffsetFraction` is read in composition at
`IslandExpandedContent.kt:162`, and it changes on every pixel of scroll, which
invalidates the whole `Column`/`Box`/`HorizontalPager` on every scroll frame.

Recommended fix: hoist both behind `derivedStateOf`.

### 2.10 OPEN — zero use of `derivedStateOf` or `snapshotFlow`

Confirmed across the entire main source set: `derivedStateOf` 0 files,
`snapshotFlow` 0 files. Every piece of derived UI state is computed eagerly on
every recomposition. This is the root enabler for most of the items above, and it
is the single highest-leverage structural change available.

### 2.11 OPEN — the service uses `resources.displayMetrics`, not `WindowMetrics`

`SmartIslandOverlayService` reads geometry from `resources.displayMetrics`, which
is wrong under split-screen, foldables and multi-display. It also means the
overlay will mis-position on the Nothing Phone (4a) if the user ever uses
split-screen or a folded posture.

### 2.12 OPEN — unguarded `Log.d` in hot paths

`AppLogRecorder.record` is correctly gated on `isRecording`, but direct
`android.util.Log.d(...)` calls are not, and several interpolate strings eagerly:

- **Every** `onComputeInternalInsets` invocation, i.e. on every touch-event
  dispatch through the overlay window: `SmartIslandOverlayService.kt:517` and `:560`.
- Ten sites in `SmartIslandNotificationListenerService`.

These run on the main thread of the listener service and of the touch pipeline.

---

## 3. Correctness and concurrency

### 3.1 OPEN — lost-update race on the notification history

`NotificationHistoryRepository` reads and writes `_history.value` **non-atomically**
from multiple threads. Every method runs on the multi-threaded `Dispatchers.IO`:

```kotlin
val current = _history.value                          // READ
_history.value = (listOf(savedEntry) + current).take(1000)   // WRITE
```

Two concurrent `saveEntry` calls interleave so one entry disappears from the
in-memory list **even though it is durably in SQLite**. The listener launches
`serviceScope.launch` per notification, and the UI can also write. The identical
pattern appears in `markAsOpened`, `deleteEntry`, `deleteByPackage`, `clearAll`
and `cleanupOldEntries`.

`SmartIslandNotificationRepository` already does this correctly with `.update {}`,
so the inconsistency is local.

Recommended fix: use `_history.update { }` throughout. Low effort, real bug.

### 3.2 OPEN — events silently dropped by `SharedFlow`

`SmartIslandNotificationRepository` has three `MutableSharedFlow`s created with
`extraBufferCapacity = 16`, no replay, and `BufferOverflow.SUSPEND` semantics.
Every emit uses `tryEmit` and **ignores the result**.

`commands` has exactly one collector, in the notification listener service — which
is not guaranteed to be alive, since the user can revoke listener access. When it
is dead, every `SkipNext` / `SeekTo` / `PlayPause` / `CancelNotification` emitted
from the UI is discarded without a trace.

Recommended fix: `Channel(Channel.BUFFERED)` or add `replay = 1`, and log drops.

### 3.3 OPEN — captured `var` mutated inside a CAS `update {}` transform

```kotlin
var isNewNotification = false
_notifications.update { existing ->
    ...
    isNewNotification = true   // inside a lambda update{} may re-run
}
if (autoExpand && isNewNotification) { ... }
```

`MutableStateFlow.update` documents that the transform **may be invoked multiple
times** under contention. A retried lambda leaves the flag latched `true` from the
aborted attempt, so `autoExpand` can fire for what is actually an update.

### 3.4 OPEN — non-atomic read-then-update in `removeNotificationsForPackage`

A notification posted between the read at `:68` and the `update` at `:70` is
dropped from the list **without a `CancelNotification` command**.

### 3.5 OPEN — data races on plain `var` fields from `Dispatchers.Default`

`serviceScope` uses `Dispatchers.Default`, which is multi-threaded, but these
fields are plain non-`@Volatile` `var`s mutated from it:

- `lastSoundPlayedTimeMs`
- `lastAutoExpandTimeMs`
- `lastHistoryCleanupTime`

Lost updates defeat the debounce windows — two racing notifications can both pass
the 1200ms sound gate and produce a double sound. `currentSettings` in the same
class *is* `@Volatile`, so the inconsistency is local.

### 3.6 OPEN — `NotificationCooldownManager.getOrPut` is not atomic

```kotlin
val timestamps = recentTimestamps.getOrPut(pkg) { ArrayDeque() }  // not atomic
synchronized(timestamps) { ... }                                   // locks the winner
```

Kotlin's `MutableMap.getOrPut` is documented as non-atomic on a `ConcurrentMap`.
Two concurrent notifications for the same package can create two deques; the
loser's timestamps are discarded and its `synchronized` block mutates an object
no longer in the map.

### 3.7 OPEN — `pendingRemovals` job-identity race

An older job for the same key that is already past its `delay` can remove a newer
job's map entry when it finishes, leaving the newer job orphaned and no longer
cancellable by `onNotificationPosted`.

### 3.8 OPEN — a non-atomic one-shot recovery flag

`SystemServiceRecovery.accessibilityRefreshAttempted` is a process-lifetime
`AtomicBoolean` that is **never reset**. `refreshAccessibilityComponent` catches
all exceptions internally, so a transient failure still consumes the single
attempt, and the workaround is dead for the rest of the process.

### 3.9 OPEN — no `addView` retry, and the failure is terminal

If `windowManager.addView` throws (a common OEM race right after an accessibility
service restart), `runCatchingLogged` swallows it, `islandView` is set to null, and
nothing retries. `startOverlaySession` is only reached from the
`repository.settings.collect` coroutine, which re-emits **only on change**. So a
single transient failure disables the island for the lifetime of the service
session, with no user-visible error — and logs only under `BuildConfig.DEBUG`.

This is a plausible explanation for "the app says everything is enabled but
nothing appears".

### 3.10 OPEN — inverted success check in Shizuku command execution

```kotlin
if (exitCode == 0 || output.isNotBlank() || error.isBlank()) {
    "Permissions auto-granted successfully via Shizuku."
} else { throw RuntimeException(...) }
```

`output.isNotBlank()` alone marks a **failed** command as a success. Also
`inputStream` is read to EOF before `errorStream`, a textbook pipe-buffer deadlock
if a child ever writes more than 64KB to stderr.

### 3.11 OPEN — `GitHubApiService.isNewerVersion` mishandles prereleases

`GitHubApiService.kt:250-251` does `split("-").firstOrNull()`, stripping
prerelease tags entirely, so `isNewerVersion("v5.1.0-beta", "5.0.0") == true`.
A user on stable 5.1.0 will be told a 5.1.0 **beta** is newer.

`GitHubApiServiceTest.kt:49` currently **asserts this buggy behaviour as
correct**, so the tests would block the fix rather than catch the bug.

### 3.12 OPEN — Shizuku runs on every screen unlock

`AutostartReceiver` is registered for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`,
`MY_PACKAGE_REPLACED` **and `USER_PRESENT`**. Every screen unlock therefore
executes 14 shell commands via Shizuku, and a fresh un-cancellable
`CoroutineScope` each time.

For a battery-focused fork this is worth gating: `USER_PRESENT` should be
rate-limited or dropped, since `BOOT_COMPLETED` already covers restart.

### 3.13 OPEN — validation gaps in settings persistence

- `notificationHistoryRetentionHours` is clamped in `fromJson` but **not** in the
  setter, `restoreSettings`, or on read.
- `restoreSettings` skips the `coerceIn` that individual setters apply, so a
  backup/JSON import can persist out-of-range cooldown values that the UI cannot.
- `notificationCooldownExcludedPackages` has no validation, unlike the other
  `Set<String>` settings.
- The nine swipe-action strings and `deviceType` are stored verbatim with **zero**
  validation, so a hand-edited backup can inject arbitrary strings into gesture
  dispatch.
- `resetPosition()` resets 1 of 14 colours and silently leaves the rest.
- `safeColor` rejects only `0L`, so a legitimately fully-transparent colour
  becomes the default.
- All write failures are swallowed by `editSafely` and every setter returns
  `Unit`, so a failed write is invisible.

### 3.14 OPEN — the three-way `autoHideTimeoutSeconds` mismatch

- UI slider range: **1–60** (`NotificationsAndPrivacySection.kt:479`)
- Backup/JSON clamp: **1–120** (`SmartIslandSettings.kt:293`)
- Test asserts: **120** (`SmartIslandSettingsTest.kt:260`)
- README claims: "1s to 60s"

The test asserts a state the UI cannot produce.

---

## 4. Security and privacy

### 4.1 FIXED — hidden-API exemption for the entire process

`SmartIslandApp.bypassHiddenApits()` reflectively invoked
`dalvik.system.VMRuntime.setHiddenApiExemptions(["L"])`, exempting the **entire**
hidden-API surface for the process. It existed for one reason: to let
`SmartIslandOverlayService.setupTouchableRegion()` reach
`ViewTreeObserver.OnComputeInternalInsetsListener`, a hidden framework callback
used to carve the overlay window's touchable area down to the pill while the
window itself spanned the whole display.

**It was very likely already dead code on the target OS.** The mechanism is:
`setHiddenApiExemptions(["L"])` is blocked for non-system apps on **Android 14
and later**. It throws, the `catch` swallows it, and the app logs
`"Successfully bypassed Hidden API restrictions"` anyway. Without the exemption,
`Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener")`
on a hidden framework class is itself subject to hidden-API enforcement and
throws, so `isTouchableRegionSupported` was left `false` and the app fell back to
the small-window path.

> **Correction on the evidence.** I initially wrote that this was *proven* by
> observing that `ViewTreeObserver$OnComputeInternalInsetsListener` and
> `View$InternalInsetsInfo` are absent from `android-36/android.jar`. That check
> is **invalid**: the SDK's `android.jar` ships only the *public* API surface and
> strips all hidden classes by design, so they are absent regardless of whether
> they exist in the framework on a device. The correct evidence is the blocked
> `setHiddenApiExemptions` call above, not the jar contents.
>
> What can be stated with confidence: the exemption is blocked on Android 14+, so
> on the fork's target (Android 16) the reflection had no exemption to work with.
> Whether the classes themselves still exist in the Android 16 framework is
> unverified. The `catch`-and-log-success behaviour means the app could not have
> reported this either way.

That made the exemption pure dead weight regardless:

- Blocked on Android 14+, with the failure silently swallowed and mislogged.
- Three reflective lookups plus a `VMRuntime` invocation on the **main thread** of
  `Application.onCreate`, on every cold start, unconditionally.
- Ran *after* `super.onCreate()`, so classes Hilt had already loaded were
  unaffected.
- Undocumented in every markdown file despite being the most platform-sensitive
  thing in the codebase.
- `"L"` is a Google Play policy violation and is reported by Play Integrity.

**The fix.** Touch pass-through never needed a hidden API. The window has always
set `FLAG_NOT_TOUCH_MODAL`, which is public API and means touches landing outside
a window's bounds go to the window behind it. So the collapsed window is now simply
*sized to the pill plus a margin*, rather than spanning the display and then
carving a hole in it. Consequences:

- `SmartIslandApp.bypassHiddenApits()` deleted; `onCreate` override gone.
- `setupTouchableRegion()` deleted, along with `isTouchableRegionSupported` and its
  `StateFlow`.
- `isFullWidth` now derives from `expanded`, which is what the flag effectively
  always was.
- The three ProGuard keeps for `ViewTreeObserver`/`InternalInsetsInfo` removed.
- The geometry arithmetic — previously duplicated in **three** places, one of them
  inside the deleted reflection callback — is now a single pure, unit-tested
  `computeCollapsedWindowGeometry()`.

**Risk assessment.** Low, because the small-window path is the one that survives
any hidden-API failure, and that is the path the app falls back to on Android 14+.
The one behavioural difference on a device where the reflection *did* work is that
the collapsed window is now pill-sized instead of full-width, which is the
intended design and covered by 12 new geometry tests. **On-device verification of
touch pass-through is still outstanding** — see §4.8.

One deliberate sizing decision: the collapsed window's horizontal margin is 28dp
per side, which must exceed the collapsed drag clamp (`PILL_DRAG_MAX_OFFSET_DP`,
24dp) or the pill clips against the window edge at the extremes of a drag. The old
fallback used 16dp and would have clipped. The 32dp collapsed
expand/collapse slide does *not* need headroom here, because it only runs while
the window is `MATCH_PARENT`.

### 4.2 OPEN — Bluetooth battery broadcast is exported and unauthenticated

`SystemEventReceiver` registers with `RECEIVER_EXPORTED` for
`android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`. Any installed app can
broadcast that action and spoof the Bluetooth battery level shown in the island.

### 4.3 DOCUMENTED — the overlay is an AccessibilityService, and the docs never say so

`SmartIslandOverlayService` extends `AccessibilityService` and draws with
`TYPE_ACCESSIBILITY_OVERLAY`. `accessibility_service_config.xml` requests
`flagRetrieveInteractiveWindows` and `canRetrieveWindowContent="true"` — a
maximally-privileged accessibility configuration.

It is correctly guarded by `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`,
so it is not directly exploitable. But it is the single most important
architectural fact for a security reviewer, and `analysis.md` /
`codebase_analysis_report.md` describe the service purely as a WindowManager
overlay without ever mentioning the accessibility-service role.

`PRIVACY.md` now explains it. The two analysis documents still do not.

### 4.4 DOCUMENTED — `SYSTEM_ALERT_WINDOW` is declared but never used to create a window

The only use of `canDrawOverlays` is a fallback check in
`ensureOverlayServiceRunning()`, which returns true if **either** accessibility
**or** `SYSTEM_ALERT_WINDOW` is granted:

```kotlin
return isAccessibilityServiceEnabled() || Settings.canDrawOverlays(this)
```

`canDrawOverlays` is irrelevant to `TYPE_ACCESSIBILITY_OVERLAY`. The app can
therefore report "ready" while being structurally incapable of drawing anything.
The in-app `overlayReady` value is computed and then only logged.

### 4.5 OPEN — CI release has no certificate-continuity check

`android.yml` runs `apksigner verify --print-certs` but never compares the
fingerprint against the previously published APK. If the keystore were ever
regenerated, CI would happily publish a release signed by a different key that
users cannot upgrade to. Pinning the cert digest in the repo would catch this.

### 4.6 OPEN — key password equals store password

`android.yml` sets `SIGNING_KEY_PASSWORD=$ANDROID_KEYSTORE_PASSWORD`. One leaked
secret compromises both. Documented as intentional in `docs/RELEASE_SIGNING.md`,
but it remains a single point of failure for the entire update chain.

### 4.7 OPEN — on-device verification of the new window model is outstanding

§4.1 replaced the hidden touchable-region reflection with a pill-sized collapsed
window relying on `FLAG_NOT_TOUCH_MODAL`. Verified so far:

- `compileDebugKotlin`, `lintDebug` and 167 unit tests pass.
- No `setHiddenApiExemptions`, `VMRuntime`, `OnComputeInternalInsets`,
  `touchableRegion` or `InternalInsetsInfo` reference remains in any `.kt` or
  `.pro` file (comment-only mentions aside).
- 12 new tests cover the geometry, including that the visible group is never
  clipped and never pushed off-screen for x offsets from -1000dp to +1000dp with
  the companion circle on either side.

**Not yet verified on hardware.** The USB device disconnected before the debug
build could be installed, so the following still need checking on the Nothing
Phone (4a):

1. Tapping the status bar to either side of the pill reaches the app underneath.
   This is the behaviour `FLAG_NOT_TOUCH_MODAL` is supposed to provide and is the
   entire point of the change.
2. The pill does not clip at the extremes of a horizontal drag — this is what the
   28dp margin was chosen for.
3. Expand/collapse has no visible pop now that the window resizes between
   pill-sized and `MATCH_PARENT` on every transition, where previously it stayed
   `MATCH_PARENT` in both states whenever the reflection had succeeded.
4. The companion circle and the auto-hide animation still behave at the new
   window size.

---

## 5. UI and interaction

### 5.1 FIXED — one tap fired two conflicting actions

`IslandOverlayView.kt:395-408` (outer `detectTapGestures`, expanded only) is an
**ancestor** of the raw gesture loop at `:479-792`. The inner loop uses
`awaitFirstDown(requireUnconsumed = false)` and only consumes on up, so the outer
detector also sees a clean tap.

Tapping the expanded pill body runs **both** `currentOnOpenNotification` and
`currentOnToggle()`, so it opens the source app *and* collapses. The comment at
`SmartIslandOverlayService.kt:523` shows the intent was outside-taps-only, but
there is no hit-test exclusion.

### 5.2 FIXED — horizontal drag in the expanded card fired the tap action

`IslandOverlayView.kt:547` guards on `abs(dragOffset) < 10f * density`, but
`dragOffset` accumulates **vertical** movement only. A purely horizontal 60dp drag
leaves `dragOffset ≈ 0`, passes the guard, and opens the app. There is no
horizontal gesture handling in the expanded state at all, despite the README
documenting "Swipe Left / Right: Switch Stack".

### 5.3 PARTIALLY FIXED — stale captures inside `pointerInput`

Nine callbacks are correctly wrapped in `rememberUpdatedState`, but some plain
parameters were read directly from a coroutine that never restarts.

**Corrected:** the audit previously claimed `isInputActive` was stale at the
gesture guard. That was wrong. `isInputActive` is a **key** of that
`.pointerInput(...)` block, so the block is cancelled and restarted when it
changes and the captured value is current. The real consequence of keying on it is
the opposite problem — an in-flight gesture is cancelled whenever the reply field
opens or closes — which is what motivated moving the hold job into the
pointer-input scope (see 5.4).

**Genuinely stale, now fixed:** `settings.autoHidePill` and
`settings.enableAppShortcuts` were read directly inside a
`.pointerInput(Unit)` block in the auto-hidden pill's tap target, which never
restarts, so they captured whatever was current at first composition. Both now
read `currentSettings`.

### 5.4 FIXED — hold gesture job no longer outlives the gesture

`IslandOverlayView` launched the 300ms hold-detection job in the **composition**
scope (`rememberCoroutineScope`), not the pointer-input scope, so if the
pointer-input coroutine was cancelled mid-gesture the job survived and produced a
spurious haptic pulse after the user had already lifted their finger.

Now wrapped in `coroutineScope { }` inside the `pointerInput` block, making the
job a child of that block and therefore cancelled with it.

### 5.6 FIXED — gesture cancellation is now handled

Compose has no public "pointer cancel" event type; a pointer that stops being
pressed without producing an up event is how cancellation surfaces. The gesture
loop only tested `changedToUp()`, so a stolen gesture still ran the entire
up-branch and fired a tap action for something the user never completed.

### 5.5 OPEN — divergent idle-hide predicates

- `IslandOverlayView.kt:215` — `hideWhenIdle && notifications.isEmpty()`
- `SmartIslandOverlayService.kt:516,619` — the same **plus** `&& !enableAppShortcuts`

With `hideWhenIdle` on and app shortcuts enabled, Compose animates the pill to
zero size while the window stays `VISIBLE` and touchable, leaving an invisible
34dp-tall tappable strip on the status bar.

### 5.7 OPEN — dead settings

`swipeDownCollapsedAction` and `swipeHorizontalCollapsedAction` are persisted,
backed up, restored and offered in the UI, but **never read**. Only `pillSwipe*`
is wired.

### 5.8 OPEN — two colliding `AUTO_COLLAPSE_DELAY_MS` constants

`IslandViewModel.kt:214` = 5000ms (actually collapses);
`SmartIslandOverlayService.kt:963` = 220ms (re-applies layout params). Same name,
unrelated meanings.

### 5.9 OPEN — duplicated gesture resolution

The entire four-way pill-swipe resolution block is written twice in
`IslandOverlayView.kt` — once on finger-up (`:558-646`) and once in the mid-drag
snappy path (`:676-756`), roughly 70 lines of copy-paste. The
`executeSwipeAction` call itself is repeated ten times with identical arguments.

### 5.10 OPEN — per-mode state is not hoisted, so the auto-collapse round-trip resets it

`MusicExpanded`, `TimerExpanded`, `StopwatchExpanded` and `NotificationExpanded`
all own transient state in local `remember` with no hoisting and no
`rememberSaveable`. The 5-second auto-collapse round trip therefore discards timer
progress, like state and lap count. `HorizontalPager` also disposes off-screen
pages, so swiping past the last notification and back re-creates the composable.

### 5.11 OPEN — the mode-to-appearance mapping is duplicated seven ways

Seven separate `when` blocks map `IslandMode` to presentation: two in
`IslandCollapsedContent`, one in `IslandOverlayView` for the secondary bubble,
one in `IslandExpandedContent` for the card, plus backdrop accent, height estimate
and height clamp. Three of them end in `IslandMode.Empty -> Unit`, which
**silently swallows** a new mode instead of failing the build.

### 5.12 OPEN — colour bugs

- **Navigation colour is ignored in the collapsed layer.**
  `IslandCollapsedContent.kt:661,709` uses `settings.liveActivityColor` for
  `IslandMode.Navigation`, while the expanded layer correctly uses
  `settings.navigationColor`. Changing the Navigation colour has zero effect on
  the collapsed pill.
- **Flashlight and ScreenRecording colours are ignored** in the collapsed layer
  and secondary bubble — hardcoded `0xFFFACC15`, `0xFFEF4444`.
- **Battery low/saver colours are not user-configurable** — hardcoded ahead of
  `settings.batteryColor`.
- Default colours collide: `batteryColor` and `navigationColor` both default to
  `0xFF10B981`; `hotspotColor`, `flashlightColor` and `timerColor` all default to
  `0xFFF59E0B`. Modes cannot be distinguished by colour alone.
- The 13 per-mode colours are resolved as `Color(settings.xColor)` at ~35 call
  sites with no `CompositionLocal` and no value class.

### 5.13 OPEN — accessibility, beyond `bounceClick`

`bounceClick` (§1.13) was the large one and is fixed. Remaining:

- **No semantics anywhere.** `Modifier.semantics` appears 0 times. The collapsed
  pill has no label; a screen reader announces only whichever stray
  `contentDescription` happens to be in the current branch, and those are
  inconsistent — `IncomingCall`, `Music`, `Notification` and `DownloadUpload` pass
  `null`.
- **~62 hardcoded `contentDescription` literals** across 22 files. These are
  TalkBack-visible accessibility bugs as well as localisation debt, and Compose's
  `HardcodedText` lint does not catch `contentDescription`.
- **Touch targets below 48dp:** notification down-arrow 24dp, action chips 28dp,
  send/cancel 36dp, timer pause/stop 40dp, stopwatch controls 36dp, the collapsed
  pill itself 34dp by default, and the seek bar 24dp.
- **The seek bar is a bare `Canvas`** with no `progressBarRangeInfo`,
  `setProgress` or `stateDescription`, so it cannot be operated by accessibility
  service, keyboard or D-pad.
- `indication = null` on the page-level click, both bubbles and the seek bar means
  no ripple and no visual press feedback on those surfaces.

### 5.14 OPEN — the overlay service is a leaky god object

`expanded`, `selectedIndex`, `isLocked`, `foregroundPackage` are exposed as public
**mutable** `StateFlow`s and written directly by the service. The service also
reads `viewModel.notifications.value` (unfiltered) while Compose uses the filtered
`visibleNotifications`, producing a concrete divergence: when a foreground-package
Music notification is filtered out, the service reserves a companion-circle slot
and a wider window while Compose renders no circle.

`IslandViewModel.mode` is a `combine` + `stateIn` that **nothing consumes** —
`IslandOverlayView.kt:161` recomputes it locally.

---

## 6. Parsers and classification

These are heuristic and will misfire. Worth knowing before extending them.

### 6.1 OPEN — loose substring matching causes real misclassification

The highest-consequence case: `shouldBeIslandOnly` cancels the notification from
the **system shade** for everything except ringing calls, Music, Navigation,
Timer and Stopwatch. Combined with unbounded substring matching, that means:

- A WhatsApp message containing the word **"hotspot"** or **"tethering"** is
  classified `Hotspot` → island-only → **cancelled out of the user's shade**.
- A message containing **"recording audio"** is classified `ScreenRecording` →
  cancelled.
- Any app with a progress bar whose text contains "media", "file", "apk", "pdf",
  "mp4" or "zip" is classified `DownloadUpload` → cancelled. `DownloadUpload` is
  checked **before** `Music`, so a "Downloading song" notification is treated as a
  transfer and cancelled.

Substring matching without word boundaries also means `"ended"` matches
*extended*, *attended*, *recommended*; `"saved"` matches *unsaved*; `"done"`
matches *undone*.

### 6.2 OPEN — fabricated data presented as real

- `NavigationParser.kt:111` shows **"In 200 m"** for a recognised nav app whose
  notification contains no distance. The user sees a distance the navigation app
  never reported.
- `LiveActivityParser` synthesises a progress bar as `1f - mins/30f` clamped to
  `[0.15, 0.95]` — unrelated to anything real.

### 6.3 OPEN — fragile turn-direction parsing

`NavigationParser.parseTurnDirection` falls through to regex branches evaluated in
order. "Keep left then head right onto Oak Ave" matches the `\bleft\b` branch
first and returns **LEFT**, which is wrong. The `"all right"` exclusion at `:154`
means an address like "Allright Ave" breaks it. Two `Regex` objects are
constructed per call.

### 6.4 OPEN — `isTimer` false positives

`TimerStopwatchParser.kt:205` returns true for any notification whose `when` is in
the future. A calendar, reminder or agenda notification is therefore classified
as a Timer and pops the island.

Also: any package whose name contains `"clock"` gets clock-app privileges; any
action labelled "Lap" or "Split" makes a notification a Stopwatch; and because
`isAlarm` returning true excludes a notification from **both** Timer and
Stopwatch, a timer notification whose text happens to contain "snooze" is dropped
from both.

### 6.5 OPEN — `LiveActivityParser` is a 9-app allowlist

Zomato, Swiggy, Blinkit, Zepto, Uber, Rapido, Ola, Dunzo, Domino's. Any other
delivery or rideshare app is unsupported by construction, and `etaText` is
non-null for **any** progress notification from those apps, so a promo with a
progress bar becomes a LiveActivity.

### 6.6 OPEN — `OemDeviceRules.detectCurrentDevice()` re-runs per classification

It is re-invoked on every `toIslandMode` call, and `toIslandMode` itself runs 3–5
times per notification — including on the **main thread** of the listener service.
Each run rebuilds a concatenated title string and performs ~10 regex and list
scans, with `NavigationParser` compiling two `Regex` per call.

### 6.7 OPEN — `isInCallPackage` has no MOTOROLA branch

Motorola falls to `else`. Minor, since `GENERIC_SCREEN_RECORDER_PACKAGES` covers
recording, but inconsistent with the rest of the enum.

---

## 7. Build and supply chain

### 7.1 OPEN — no version catalog, no Dependabot

`gradle/libs.versions.toml` does not exist. Every dependency version is a
hardcoded string in `app/build.gradle.kts` — 20+ versions, no single point of
upgrade. There is no `.github/dependabot.yml`. Dependency maintenance is entirely
manual and invisible.

### 7.2 OPEN — 22 lint rules globally disabled

`abortOnError = true` and `warningsAsErrors = true` are set, which is good intent,
but 22 rules are then muted globally. The damaging ones:

- `MissingTranslation` — directly contradicts the "100% Localization Parity"
  claims in `CHANGELOG.md` and `ROADMAP.md`
- `GradleDependency` — the rule that tells you about new library versions
- `UnusedResources` — hides dead resources permanently
- `AndroidGradlePluginVersion` — combined with
  `android.suppressUnsupportedCompileSdk=36` in `gradle.properties`, there is now
  **zero signal** anywhere that the toolchain is out of date

The correct approach is per-site `@Suppress` on the ~15 offending lines.

### 7.3 OPEN — undocumented AGP 9 escape hatches, and Kotlin support is now redundant

`gradle.properties` sets `android.builtInKotlin=false` and `android.newDsl=false`
with no comment. A future contributor will have no idea why they exist.

AGP itself now reports this as a deprecation on every build:

```
WARNING: The option setting 'android.newDsl=false' is deprecated.
The current default is 'true'.
It will be removed in version 10.0 of the Android Gradle plugin.

w: Deprecated 'org.jetbrains.kotlin.android' plugin usage
   The 'org.jetbrains.kotlin.android' plugin in project ':app' is no longer
   required for Kotlin support since AGP 9.0.
   Solution: Remove both `android.builtInKotlin=true` and `android.newDsl=false`
   from `gradle.properties`, then migrate to built-in Kotlin.
```

So the project is carrying two suppression flags to opt out of a Kotlin support
path that AGP 9 provides natively, plus the `org.jetbrains.kotlin.android` plugin
declaration in `app/build.gradle.kts:10` that the warning says is no longer
needed. `android.newDsl=false` is removed in AGP 10, so this is a hard blocker on
the next toolchain bump rather than optional cleanup.

The warning's suggested text mentions `android.builtInKotlin=true`, but this
repository sets it to `false` — the correct action is to **delete both lines**
entirely, which restores the AGP 9 defaults, and drop the now-redundant plugin
declaration.

### 7.4 OPEN — no build or configuration cache

`org.gradle.caching`, `configuration-cache` and `parallel` are all absent, and CI
runs `--no-daemon`, so every run pays full cold configuration cost.

### 7.5 OPEN — GitHub Actions are tag-pinned, not SHA-pinned

All five actions across both workflows use floating major tags
(`actions/checkout@v4`, `setup-java@v4`, `upload-artifact@v4`,
`download-artifact@v4`, `github-script@v7`). Any can be re-pointed by a
compromised upstream tag.

Note that `.github/copilot-instructions.md:174` explicitly recommends "official
stable GitHub Actions versions" as best practice, which is what produced this.
For a project whose entire security posture is "we don't exfiltrate your
notifications", that is the wrong guidance to encode.

`checkout@v4` and `setup-java@v4` are also both outdated.

### 7.6 OPEN — `pull_request_target` combined with `actions/checkout`

`auto-review.yml:8` uses `pull_request_target` with `actions/checkout` at `:29`
against `github.event.pull_request.head.sha`. **Not currently exploitable** —
permissions are narrow and no untrusted code is executed, since `github-script`
only calls the REST API. But `actions/checkout` leaves the token in `.git/config`
on the runner, and this is one step from disaster. The checkout appears to be
unused by the script and should simply be deleted.

### 7.7 OPEN — instrumented tests never run in CI

No `connectedAndroidTest` anywhere in `android.yml`. The single androidTest is
therefore dead code, and `assembleReleaseAndroidTest` is never built, so a broken
androidTest source set would not even be caught.

### 7.8 OPEN — dead dependencies

- `app.cash.turbine:turbine:1.1.0` is never imported anywhere.
- `espresso-core:3.6.1` is unused; the single androidTest uses Compose test only.
- `kotlinx-coroutines-test:1.8.0` (Jan 2024) predates Kotlin 2.3.21 and the
  `TestScope` semantics `IslandViewModelTest` relies on.

### 7.9 OPEN — `isReturnDefaultValues = true` masks unimplemented stubs

Combined with heavy `mockk`, any test that accidentally reaches a real Android
framework method gets `null`/`0`/`false` instead of a loud `RuntimeException`.
Tests can pass for reasons unrelated to what they assert. There is no Robolectric
anywhere, so no test can ever exercise a real Android implementation — which is
exactly why the layout test in §1.8 had to duplicate production math.

### 7.10 OPEN — `checkDependencies = true`

Lints the dependencies of dependencies. With a single module this accomplishes
almost nothing while making the lint step materially slower.

### 7.11 OPEN — a redundant `buildscript {}` block

The root `build.gradle.kts` uses the legacy `buildscript { }` idiom with
hardcoded classpath versions, on AGP 9. No `plugins {}` block, no `clean` task
registration.

### 7.12 DOCUMENTED — `.gitignore` oddities

`install.ps1` and `ISLAND_HORIZONTAL_ALIGNMENT_FIX.md` are gitignored under a
comment reading `# Critical README.md`, which is meaningless. `.vscode` lacks a
trailing slash, inconsistent with `.idea/`. `.DS_Store` is not ignored, and the
maintainer works on macOS.

### 7.13 DOCUMENTED — vestigial git-lfs configuration

`.git/config` has an `[lfs]` section, but `.gitattributes` has no `filter=lfs`
directive and no LFS patterns. The 13 screenshots are ordinary blobs. This gives a
false impression that assets are LFS-tracked.

---

## 8. Tests

### 8.1 Current state

155 unit tests across 20 classes, all passing. One instrumented test.

Genuinely good coverage exists for the pure logic: `TimerStopwatchParserTest`
(15 tests with real multi-OEM fixtures), `IslandModeMappingTest` (19 tests across
5 OEM screen recorders), `AppNotificationFilterTest` (16),
`NotificationCooldownManagerTest` (5, against the real `DETECTION_WINDOW_MS`),
`SmartIslandSettingsTest` (7, including clamp bounds and JSON round-trip),
`CameraCutoutDetectorTest`, `PillGestureActionTest`, `IslandOverlayLayoutTest`.

That is better than most hobby Android projects, and the parser tests would catch
real regressions.

### 8.2 OPEN — the entire risk surface is untested

Zero tests for:

| Area | Why it matters |
| --- | --- |
| `SmartIslandOverlayService` (965 lines) | WindowManager lifecycle, the `OnComputeInternalInsetsListener` reflection, IME flag switching, freeform launch. **The highest-risk file in the repo.** |
| `SmartIslandNotificationListenerService` (1100+ lines) | `cancelNotification` suppression, `suppressedKeys` bookkeeping, media-session binding. Only 3 shallow tests touch it. |
| `NotificationHistoryDbHelper` / `NotificationHistoryRepository` | The whole SQLite layer, including the race in §3.1 |
| `SmartIslandSettingsRepository` | The whole DataStore layer; only the pure data class is tested |
| All 15 `ui/expanded/*.kt` | Inline reply, media controls, timer rendering |
| `AutostartReceiver`, `AppLogRecorder`, `LogUtils`, `TimeUtils`, `IntentUtils`, `SystemServiceRecovery` | — |
| `SmartIslandApp.bypassHiddenApits()` | 44 lines of platform-sensitive reflection, silently failing on the target OS |

Adding Robolectric would unlock most of this and would have prevented the
duplicated-math test in §1.8.

### 8.3 OPEN — schema migration is a no-op

`NotificationHistoryDbHelper.onUpgrade` is an empty function with the comment
"Future database migrations". There is no `onDowngrade` and no destructive
fallback. When `DATABASE_VERSION` is finally bumped, the old schema stays in place
and every new query fails at runtime.

### 8.4 OPEN — missing index on the hot update path

`markAsOpened` runs `UPDATE ... WHERE notification_key = ?` with **no index** on
`notification_key`, so every "opened" event is a full table scan. `searchEntries`
uses `LIKE '%q%'` across four columns, which can never use an index. There is no
FTS table.

### 8.5 OPEN — all database errors are invisible in release

Every helper method wraps work in `runCatchingLogged` and returns a sentinel
(`-1L`, `emptyList()`, `0`). `runCatchingLogged` routes to `android.util.Log`
**only when `BuildConfig.DEBUG`**, and `AppLogRecorder` returns immediately unless
the user has enabled log recording, which defaults to off.

Net effect: in a release build with default settings, every SQLite failure is
completely silent. `insertEntry` returning `-1` leaves `entry.id == 0L`, and a
later `deleteEntry(0)` silently deletes nothing.

### 8.6 OPEN — `SQLiteOpenHelper.close()` is never called

Combined with a duplicate-repository fallback in
`NotificationHistorySection.kt:180` that constructs a **second**
`NotificationHistoryRepository` with the raw Activity `Context`, each navigation to
the history screen can allocate another `SQLiteOpenHelper` and another
`CoroutineScope` that is never cancelled.

### 8.7 OPEN — `GitHubApiService` HTTP path is untested

All 6 tests hit only `isNewerVersion`. The HTTP path, JSON parsing, download-count
aggregation, contributors, commits and repo-stats parsing are untested — despite
this being the only network-touching code in the app.

### 8.8 OPEN — `IslandViewModelTest` mixes two schedulers

`testDispatcher = StandardTestDispatcher()` is a field, but `runTest {}` creates
its own `TestScope` with a **different** scheduler. It works only because
`Dispatchers.setMain(testDispatcher)` routes `viewModelScope` to the field
dispatcher. The idiomatic form is `runTest(testDispatcher)`.

Three of its five tests also use `relaxed = true` without stubbing `.settings`, so
they validate the ViewModel against a mock whose `Float` fields are all `0f`.

---

## 9. Documentation

The documentation is heavily AI-generated and the fabrications have been promoted
into load-bearing claims. `analysis.md` and `codebase_analysis_report.md` are
approximately **85% duplicates** of each other, including two near-identical
Mermaid diagrams, and should be merged.

### 9.1 FIXED — the license typo claimed as fixed but never fixed

`CHANGELOG.md:473` states:

> **Fixed**: Corrected `GNU GPL v3License` to `GNU GPL v3 License` in workflow,
> configuration, ignore, and helper files.

The typo is still present in `.editorconfig:3` and `.gitattributes:3`. A changelog
entry claiming an unperformed fix is worse than no entry.

### 9.2 OPEN — 61 broken absolute-path links

`file:///a:/SmartIsland/...` links pointing at the original author's Windows `A:`
drive: 32 in `codebase_analysis_report.md`, 17 in `analysis.md`, 10 in `5W2H.md`.
All broken for every reader, and they leak the author's local directory layout.
`scripts/generate_icons.py:4-5` has the same problem in hardcoded `r"A:\..."`
paths, making the script unusable by anyone else.

### 9.3 OPEN — phantom files referenced as if they exist

- `ui/sections/AppUpdatesSection.kt` — referenced by `analysis.md`,
  `codebase_analysis_report.md`, `5W2H.md` and `CHANGELOG.md`. The real file is
  `UpdatesAndDownloadsSection.kt`.
- `ui/Color.kt` — referenced in `CHANGELOG.md:56`. Does not exist.
- `PillGestureActionTest` path in `codebase_analysis_report.md:174` omits the
  `util/` segment.

### 9.4 OPEN — fabricated test metrics

`CHANGELOG.md:246-252` lists per-file test line counts. Five of seven are wrong;
`NavigationParserTest` is listed as 32 lines and is 109. `:245` and
`ROADMAP.md:132` claim "52+ unit test cases" when there are now 155.
`codebase_analysis_report.md:172-180` calls the suite "extensive" while listing 7
files out of 21.

### 9.5 OPEN — contradictory roadmap numbering

`ROADMAP.md:64-74` tables the next releases as v7.1 / v7.2 / v8.0;
`:172-195` calls the **same three milestones** "Phase 5.2", "Phase 5.3" and
"Phase 6.0". Phase 5.2 also collides with an already-shipped 5.2.0.

### 9.6 OPEN — duplicate `versionCode` in the changelog

5.0.0 and 5.1.0 both recorded as `versionCode 5`; 3.2 and 3.2.1 both as
`versionCode 3`.

### 9.7 OPEN — hardcoded download count in six files

`"15,648+"` appears in `README.md:87`, `ROADMAP.md:23,71,85`, `5W2H.md:29`,
`analysis.md:171`, `codebase_analysis_report.md:161` and `CHANGELOG.md:13,38`.
`README.md:47` correctly uses a live shields.io badge; the rest are guaranteed rot.

### 9.8 OPEN — user-visible mode count is wrong

`README.md` says **13** dynamic modes. The in-app string
`values/strings.xml:208` says **"for all 11 dynamic island modes"**, and all five
translations repeat 11. The code has 14 `IslandMode` entries (13 real plus
`Empty`).

### 9.9 OPEN — stale release-signing doc

`docs/RELEASE_SIGNING.md` references `git tag v3.2.1` and an output filename of
`SmartIsland-v3.0.apk`. It also omits that `assembleRelease` without the signing
secrets silently produces an **unsigned** APK, so the CI check
`test -f app/build/outputs/apk/release/app-release.apk` fails with the misleading
message "Release APK was not produced" rather than "secrets are missing".

### 9.10 OPEN — four near-identical AI instruction files

`.github/copilot-instructions.md` (205 lines),
`.github/copilot-code-review-instructions.md` (55),
`.github/instructions/code-review.instructions.md`,
`.github/instructions/*.instructions.md` (3 more) — all covering the same ground
at different verbosity, plus `CONTRIBUTING.md`, `PULL_REQUEST_TEMPLATE.md` and
`instructions/pull-requests.instructions.md` repeating the same PR checklist three
times.

`copilot-instructions.md:126-136` encodes very specific implementation details
("must synchronize against `pagerState.settledPage` and guard programmatic
scrolling with `!pagerState.isScrollInProgress`") as permanent review rules. It
reads like an LLM distilled a PR diff into policy, and it will block a legitimate
refactor of `IslandExpandedContent` for a reason no human remembers.

---

## 10. Localization

604 strings with near-complete parity across `values/`, `values-zh`,
`values-zh-rCN`, `values-pt` and `values-pt-rBR`. `locales_config.xml` is wired
for Android 13 per-app language. This is genuinely above average.

### 10.1 OPEN — `values-en/` is byte-identical to `values/`

Verified with `diff -q`: identical. 604 lines of pure duplication that must be
kept in sync forever. `values/` **is** already English. Delete it.

### 10.2 OPEN — divergent duplicate locales

`values-zh` and `values-zh-rCN` are different Chinese translations of the same
language, and already disagree on at least three strings (including quoting style
and which delivery services are listed). `values-pt` and `values-pt-rBR` have the
same problem. Two translations per language means double maintenance for no
benefit; the region variants should be folded in or dropped.

### 10.3 OPEN — no Traditional Chinese

There is no `values-b+zh+Hant` or `values-zh-rTW`. A device set to zh-TW resolves
`values-zh` and shows Simplified content.

### 10.4 OPEN — ~90 hardcoded English strings in Compose code

~62 `contentDescription` literals and ~28 visible `Text` literals, plus service
toast and notification strings. Compose's `HardcodedText` lint does not catch
`contentDescription`, and `MissingTranslation` is disabled. This is what falsifies
the "100% Localization Parity" claim.

Two of them are actively misleading: `NotificationExpanded.kt:216` and `:344`
fire a success toast ("Reply sent: …", "Clicked: …") when **no `PendingIntent`
exists** and nothing was sent.

### 10.5 OPEN — adaptive icon has no monochrome layer

`mipmap-anydpi-v26/ic_launcher.xml` has no `<monochrome>`, so there is no themed
icon on Android 13+. Hidden by the `MonochromeLauncherIcon` suppression.

---

## 11. Fork-specific direction

Recorded for the redesign discussion. **Nothing in this section is implemented
yet** — it is here so the findings that constrain the redesign are not lost.

### 11.0 Progress

See [REDESIGN.md](REDESIGN.md) for the full specification. Implemented and verified
on hardware so far:

- Expanded card geometry switched to the HIG's fixed inset (equal top/side margin)
  instead of a 95% width ratio, which removes the downward jump that made the
  expansion read as a separate panel. §5.1's double-fire and §5.2's
  horizontal-drag-fires-tap are fixed as part of the same gesture work.
- Long-press now expands and a single tap now opens the source app, matching iOS.
  Expansion fires while the finger is still down rather than on release.
- Companion and tertiary bubbles now fade out as the card takes over, instead of
  drawing on top of it.
- §5.3, §5.4 and §5.6 fixed.

Still outstanding: the 84–160dp height clamp, cross-fading the compact and
expanded content inside the single shape rather than swapping them, the
window-bounds animation described in REDESIGN.md section 4.3, and re-fitting the
13 per-mode cards.

### 11.1 Target device characteristics

Nothing Phone (4a), `Frogger`, A069. Android 16 / API 36, Nothing OS
`B4.1-260808-1352`. 1224×2720 physical at 480dpi, with a **density override to
440dpi (2.75)**. Punch-hole front camera, so a centred island is the natural
default.

Two consequences already visible in the code:

- `NotificationHistorySection` and other screens use
  `LocalConfiguration.screenWidthDp`. With the density override in play, dp
  arithmetic and screenshot-derived pixel values can disagree.
- `expansionRatio` in `SmartIslandNotificationRepository` derives screen width from
  `resources.displayMetrics`, which is wrong for split-screen and would need
  `WindowMetrics` (§2.11).

### 11.2 Nothing OS design language

Nothing's visual identity is: heavy use of pure black and white, a single red
accent (`#D71921`-ish), dot-matrix / LED-strip motifs, monospace technical
typography, and a deliberately industrial, unpolished feel with visible grid and
label affordances.

The current app is the opposite: Material 3 Expressive, burnt orange
(`#D84315`), rounded, soft springs (`stiffness 520`, `dampingRatio 0.72`),
gradient-heavy. That is an iOS-derivative aesthetic, not a Nothing one.

This is a genuine fork point and worth deciding explicitly before any UI work:

- **Option A — full iOS Dynamic Island fidelity.** Keep the current spring physics
  and card language, polish the interactions. Closest to the README's promise and
  to what users expect from "Dynamic Island".
- **Option B — Nothing-native reinterpretation.** Same information architecture and
  interaction model, but Nothing's visual grammar: monospace labels, dot-matrix
  glyphs, hard black/white with red accent, less rounded, no gradients.

Option B is the more distinctive outcome and the more defensible reason to fork.
They are not mutually exclusive for the *information architecture* — only for the
surface treatment.

### 11.3 Structural prerequisites for either option

The UI is currently overloaded in a way that will make a redesign expensive:

- Seven duplicated `when (IslandMode)` dispatches (§5.10). A redesign that changes
  one mode's presentation has to find all seven.
- Per-mode state is unhoisted (§5.9), so any transition work fights the pager.
- Two competing gesture layers that both fire (§5.1, §5.2).
- ~35 scattered `Color(settings.xColor)` call sites (§5.11) with no
  `CompositionLocal`, so a theme change is a sweep.

Recommended order before visual work:

1. Extract a single `IslandMode -> ModePresentation` registry (glyph, label,
   accent colour, collapsed/expanded renderers, height estimate) and drive all
   seven dispatches from it.
2. Introduce a `LocalIslandPalette` CompositionLocal so colour resolution is one
   lookup rather than 35.
3. Consolidate the two gesture layers into one owner that dispatches tap/hold/
   swipe exactly once, with `rememberUpdatedState` for `isInputActive` and
   settings.
4. Hoist per-mode state with `rememberSaveable`, keyed on notification key.
5. Only then change the surface treatment.

### 11.4 Battery budget to protect

Whatever the visual direction, these should be treated as invariants:

- No animation value read during composition (all of §2.3 stays fixed).
- No per-composition binder IPC (§2.2 stays fixed).
- No polling loop that runs when the screen is off (§2.1 stays fixed).
- The 3-second battery alternation (§2.7) must not become more aggressive.
- The `USER_PRESENT` Shizuku run (§3.12) should be rate-limited.

---

## 12. Rejected findings

Investigated and dismissed, recorded so they are not re-litigated.

- **"The call timer never ticks."** `IslandCollapsedContent.kt:252` computes
  `notification?.timeMillis ?: System.currentTimeMillis()` and passes it as the
  key to `CallTimer`'s `remember(postTimeMillis)` and `LaunchedEffect`. If
  `notification` were null this would produce a fresh key every recomposition and
  the timer would never advance. But reaching that branch requires
  `mode == IncomingCall` with a null notification, and `mode` is derived from an
  actual notification. **Practically unreachable.** It is a defensive-robustness
  nit, not a live bug.
- **"MediaController leaks and should be released."** Verified against
  `android-36`: `MediaController` exposes no public `release()` or `close()`, only
  `registerCallback`/`unregisterCallback`. There is no supported way to dispose of
  a controller created from a token. The instances are keyed by `mediaToken`, so
  the count is bounded by distinct media sessions, not by play count. Not
  actionable.
- **"`values-en` might serve a purpose."** It is byte-identical to `values/`, which
  is already English, so it is pure duplication with no behavioural effect.

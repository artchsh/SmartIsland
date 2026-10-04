# Dot Island — publishing API

Any app on the device can put an activity in the island. Dot Island also watches
Spotify, Dodo and phone calls on its own; this API is for everything else.

## Quick start (no code)

`com.android.shell` is allowlisted by default, so this works out of the box:

```bash
# Publish
adb shell am broadcast -a dev.qarasky.dotisland.action.PUBLISH \
  --es id "com.android.shell:job1" \
  --es title "Build finished" \
  --es text "12 tests, 0 failures"

# Update in place (same id)
adb shell am broadcast -a dev.qarasky.dotisland.action.UPDATE \
  --es id "com.android.shell:job1" \
  --es title "Uploading" --ei progress 60

# Remove just that activity
adb shell am broadcast -a dev.qarasky.dotisland.action.DISMISS \
  --es id "com.android.shell:job1"

# Remove everything this caller published
adb shell am broadcast -a dev.qarasky.dotisland.action.DISMISS_ALL
```

The same actions work from Termux, Tasker or any app.

## Quick start (Kotlin)

`DotIslandPublisher` is a thin wrapper — it exists so ids get namespaced and
arguments stay typed. Add `implementation(project(":dotisland"))` or copy the
class; nothing is required.

```kotlin
val island = DotIslandPublisher(context, "order-42")

island.publish("Order placed", "Arriving in 12 min", progress = 20)
island.update("Order placed", "Courier assigned", progress = 60)
island.update("Order placed", "Delivered", progress = 100)
island.dismiss()
```

## Actions and extras

| Action | Suffix | Purpose |
| --- | --- | --- |
| `dev.qarasky.dotisland.action.PUBLISH` | — | Create or replace an activity |
| `dev.qarasky.dotisland.action.UPDATE` | — | Same slot; content only |
| `dev.qarasky.dotisland.action.DISMISS` | — | Remove one by `id` |
| `dev.qarasky.dotisland.action.DISMISS_ALL` | — | Remove every activity this caller owns |

| Extra | Type | Notes |
| --- | --- | --- |
| `id` | String | **Required.** Must be `<your.package>:<local>` |
| `title` | String | **Required.** Trimmed, max 200 chars |
| `text` | String | Optional, max 400 chars |
| `progress` | Int | Optional 0–100; clamped, shows a bar and `%` |
| `timeout_ms` | Long | Optional; clamped to 30s–24h, default 30min |
| `expand` | Boolean | Optional; expand the island on arrival |
| `app_name` | String | Optional label override, max 64 chars |
| `icon_package` | String | Optional; package owning `icon_res` |
| `icon_res` | String | Optional drawable **name** in that package |

Icons are referenced by name rather than as a Bitmap so a shell can supply one.
An unresolvable icon falls back to the publisher's launcher icon, then to a
generic glyph — it never fails the publish.

## Rules

- **Identity comes from Binder.** The receiver reads `Binder.getCallingUid()`,
  resolves the real package behind it and requires that package in the allowlist
  managed from the Dot Island app. Nothing in the intent is trusted for identity,
  so a publisher cannot claim another app's namespace, label or icon.
- **One slot per publisher.** Two publishers can be visible at once, each with its
  own island entry. Re-publishing the same `id` replaces that entry.
- **Updates are rate limited** to one per 400 ms. The first publish is never
  rate limited.
- **At most 5 publishers** may hold an activity at once. Existing publishers can
  always keep updating.
- **Safety timeout.** An activity expires on its own (default 30 min, max 24 h),
  so a crashed publisher cannot leave a permanent entry. Dismiss it sooner for a
  prompt exit. Expiry is enforced on read *and* by a 15-second sweep.
- **Durable.** Published activities are persisted, so they survive process death
  and reappear when the overlay next binds. A swipe-dismiss clears the stored copy.
- **Ranking.** Published entries sit below calls, deliveries and music.
- **Tapping** a published card opens the publisher app.

## Adding a publisher

Open **Dot Island → Publishers**, then add the package name. `com.android.shell`
is already allowed. No permission dialog is involved; the allowlist is the gate.

## Not supported (deliberately)

- Custom tap actions or buttons — the card is plain text plus optional progress.
- Publisher-chosen colours. The surface stays monochrome.
- Overriding the island's surface, geometry or motion.
- Anonymous publishing: the caller is always identified.

## Files

- `api/DotIslandContract.kt` — actions, extras, clamps, pure validation
- `api/DotIslandPublisher.kt` — optional Kotlin wrapper
- `service/DotIslandPublishReceiver.kt` — broadcast entry, UID verification
- `data/DotIslandPublisherStore.kt` — persistence, allowlist, expiry
- `ui/expanded/PublishedExpanded.kt` — the card
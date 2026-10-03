# Privacy Policy

Last updated: October 3, 2026

Smart Island is designed to run locally on your Android device. This document explains what the app can access and how that information is used.

## Permissions The App Requests

| Permission | Why it is needed |
| --- | --- |
| `INTERNET` | **Opt-in only.** Used solely for the "check for updates" feature, which queries the public GitHub Releases API for the app's own version number. Disabled by default via the `allowNetworkChecks` setting. No notification data, metadata, or telemetry is ever transmitted. |
| `SYSTEM_ALERT_WINDOW` | Draws the floating island overlay. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Signature permission required by the notification listener service to read notification metadata. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the overlay service alive in the background. |
| `PACKAGE_USAGE_STATS` | Resolves launcher app names and icons for notifications. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets the user exempt the app from aggressive OEM background killing. |
| `RECEIVE_BOOT_COMPLETED` | Restarts the overlay after reboot. |
| `VIBRATE` | Haptic feedback for the hold-to-clear gesture. |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | Reads connected-device battery levels for the Bluetooth island mode. |
| `moe.shizuku.manager.permission.API_V23` | Optional Shizuku integration for one-tap permission granting. |

The app also runs an `AccessibilityService`. This is required because the overlay is drawn as a `TYPE_ACCESSIBILITY_OVERLAY` window, which is the only window type available to an always-on floating overlay that survives app backgrounding without an activity. The service requests only window-state-changed events and does not read page content, inspect text of other apps, or perform input automation.

## Information The App Processes

When you enable notification listener access, Smart Island can process notification metadata from other apps so it can display the floating island experience. This may include:

- App name and package name
- Notification title and text
- Notification icons or large images
- Notification action labels and action intents
- Call and media notification categories
- Media metadata such as artwork, playback state, duration, and position when available

The app also stores local island settings, such as enabled state, size, position, and corner radius.

## How Information Is Used

Notification data is used to render the island UI, show demo or real notification states, open notification content, dismiss notifications, and update media/call presentation.

Settings are used to remember your preferred island appearance and position.

## Data Sharing

At the time of this policy, Smart Island:

- **Declares the Android `INTERNET` permission, but uses it only for the opt-in update check.** The permission is required so the update check can work when you enable it. The network code path is gated behind the `allowNetworkChecks` setting, which is **off by default**. When disabled, the app performs no network requests at all.
- Does not include analytics, advertising, or crash-reporting SDKs.
- Does not send notification content to any remote server.
- Does not sell or share user data.

The only outbound request the app can ever make is a `GET` to `api.github.com` for the public repository `agupta07505/SmartIsland`, requesting release metadata and contributor information. No notification, settings, or device identifier is included in that request.

External links in the app may open GitHub, email, or social/profile pages in another app or browser. Those external apps and services have their own privacy practices.

## Data Retention and Backup

Notification details are kept in app memory while Smart Island is running and while the notification is active in the island. Local settings are stored on your device using AndroidX DataStore Preferences. The notification history log is stored in a local SQLite database.

**Backup:** Cloud backup and device-to-device transfer are **disabled**. The manifest sets `android:allowBackup="false"` and excludes all app data from both the legacy `fullBackupContent` path (Android 11 and below) and the `dataExtractionRules` path (Android 12 and above). Your notification history and settings stay on this device and are not copied to Google Drive or to a new phone. Uninstalling the app or clearing its storage removes all local data permanently.

## Your Controls

You can:

- Turn off Smart Island in the app.
- Disable the opt-in update check, which guarantees the app never touches the network.
- Revoke accessibility service access in Android settings.
- Revoke notification listener access in Android settings.
- Clear the notification history from within the app.
- Clear app storage or uninstall the app to permanently remove all local settings and history.

## Changes

If the app adds network features, analytics, crash reporting, accounts, cloud sync, or any other data sharing behavior, this policy must be updated before release. Any change to the set of requested permissions must be reflected in the permissions table above.

## Contact

For privacy questions, open an issue in this repository without including private notification content.

# 轻计 TapCount

**One tap. One small win.** A lightweight offline Android counter that records a small achievement every time you launch it.

## Features
- Tap the app icon to increment the active project's count and return to the launcher.
- Tap the icon again within 5 seconds to open the dashboard, without an extra increment.
- Daily totals, yesterday's comparison, 7/30-day bar charts and activity history.
- Folders, independent named projects, manual adjustments and daily reset with retained history.
- Optional vibration, sound and notifications.
- Light/dark/system themes; English, Simplified Chinese, or system language.
- Data export/import (JSON) and GitHub Releases update check.
- Optional **pinned numeric shortcut** updated using Android ShortcutManager.

**Android limitation:** Normal launcher app icons cannot be rewritten to arbitrary numbers. TapCount's regular icon counts immediately after installation. Pin the optional numeric shortcut once from Settings to display a mutable count icon. Launcher caches and Android rate limits mean **not every tap can be reflected instantly**; counting itself is always saved.

## Build with GitHub Actions
Open [Actions](https://github.com/XiaoyuZhuang/TapCount/actions). Each push to main builds an installable **debug APK**, downloadable from the **tapcount-debug-apk** artifact. No local Android installation is needed.

## Releases and signatures
Pushing a tag such as `v0.1.0` runs the workflow and publishes an APK in GitHub Releases. For installable updates across versions, set a permanent release signing key in GitHub Actions secrets:
- `TAPCOUNT_KEYSTORE_BASE64` - base64 of the Java Keystore (.jks).
- `TAPCOUNT_KEYSTORE_PASSWORD`
- `TAPCOUNT_KEY_ALIAS`
- `TAPCOUNT_KEY_PASSWORD`

Without those secrets, builds upload only **debug APK artifacts for testing** and do not create official releases. GitHub-hosted runners may generate different debug signatures, so reinstalling (possibly uninstalling first) may be necessary. Keep permanent signing secrets safe and never commit the keystore.

## Privacy
All counters and activity records live locally in SQLite. No account, analytics, or tracking. Internet access is used only on request for the GitHub Releases update check.

## Requirements
Android 8.0+ (API 26). Kotlin and Android Gradle Plugin, built with JDK 17.

## Notes
Android 12+ can show a short system splash when launching the app even though the app itself doesn't present a counting screen. Different launchers may also update numeric shortcut icons at different rates.

## v0.2.0 fix release
- Separate no-UI counter task: standard launcher icon and number shortcut both increment and finish immediately, except second tap in five seconds to open management.
- Static +1 launcher icon; live numeric count is available via pinned shortcut only. Android does not allow the normal installed launcher icon to change arbitrarily.
- Configurable 65ms vibration, tone and high-importance short-lived notifications. System permission and phone notification channel settings determine whether a heads-up banner appears.
- Two-column statistics and compact daily history.
- Before updating a v0.1.0 debug APK, export a JSON backup from Settings. Runner-generated debug signing certificates may vary; uninstalling the old build deletes its local data.

## v0.3.0
- Modern rounded theme-aware dialogs and hourly breakdown (first recorded hour to last, zero-filled gaps).
- Adjustable double-launch window (1–60 seconds, default 1 second).
- Optional continuous counting mode (open dashboard after every Nth tap, N = 2–1000); all taps count.
- Random reward draws with 1/N per-tap probability, a guaranteed win on the Nth unsuccessful tap, configurable N and reward points, and a distinct double-vibration/notification.
- Optional low-priority ongoing notification showing the current counter, updated when the counter changes.
- Persistent reward/periodic state stored in SQLite; v0.2.x data upgraded in place when signatures match.

## Private signing (IMPORTANT)
The signing keystore is intentionally **not committed**. The owner holds a private TXT containing a base64 PKCS12 keystore and its passwords.
Go to **Settings → Secrets and variables → Actions → New repository secret**, and create:
- `TAPCOUNT_KEYSTORE_BASE64`
- `TAPCOUNT_KEYSTORE_PASSWORD`
- `TAPCOUNT_KEY_ALIAS`
- `TAPCOUNT_KEY_PASSWORD`

Once all four are configured, go to **Actions → Build Android APK → Run workflow (main)**.
Only successfully signed builds will be uploaded as `tapcount-release-signed-apk` and published in GitHub Releases. No debug APK is published as an official release when secrets are absent.

The **first switch** from a v0.2.x debug APK to the new production certificate cannot be installed over the old certificate. Export a JSON backup from the old app, uninstall it, install v0.3.0 signed, then import the backup. Later signed APKs using the *same* private key can update in place.

Keep the private TXT and its contents out of issues, Git commits, logs, chat screenshots, and repository files. The private key must remain private.

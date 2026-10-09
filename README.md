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

Without those secrets, tag builds publish **debug APKs for testing only**. GitHub-hosted runners may generate different debug signatures, so reinstalling (possibly uninstalling first) may be necessary. Keep permanent signing secrets safe and never commit the keystore.

## Privacy
All counters and activity records live locally in SQLite. No account, analytics, or tracking. Internet access is used only on request for the GitHub Releases update check.

## Requirements
Android 8.0+ (API 26). Kotlin and Android Gradle Plugin, built with JDK 17.

## Notes
Android 12+ can show a short system splash when launching the app even though the app itself doesn't present a counting screen. Different launchers may also update numeric shortcut icons at different rates.

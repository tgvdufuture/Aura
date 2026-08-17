# Aura — Custom notification LED for POCO X8 Pro (HyperOS)

[![Build](https://github.com/tgvdufuture/Aura/actions/workflows/build.yml/badge.svg)](https://github.com/tgvdufuture/Aura/actions/workflows/build.yml)

Open-source Android app that drives the **RGB LED rings** on the back of the **POCO X8 Pro** (Xiaomi / **HyperOS 3**) to show a different **color** and **animation** per **app**, per **contact** and per **group** — **while the screen is off**. It replaces HyperOS's default notification LED, which is limited to a single global color with no sender distinction.

> Notification LED · RGB LED · light ring · POCO X8 Pro · Xiaomi · HyperOS · Shizuku · Android · custom notification LED · RGB notification ring — **no root required**.

- 100% local: no network permission, no backend, no telemetry.
- No root: hardware control via **Shizuku** (ADB privileges).
- Resolution priority: **contact > group > app**.

> See [`phase0/REPORT.md`](phase0/REPORT.md) for the feasibility spike (discovery of the `miui.lights.ILightsManager` service).

## Features

- **Color per app**: each enabled app has a default LED color.
- **Color + animation per contact/group**: messages (WhatsApp…) and calls can be distinguished by sender (breathing, flashing, rainbow, alert).
- **Lock screen aware**: the LED drives when the screen is off **or** the device is locked; it stays off only while you're actively using the phone.
- **"Last one wins" with smart fallback**: a new notification replaces the previous state, and dismissing it falls back to the previous one.
- **Auto-restart on boot**: the service and notification listener reconnect automatically after a reboot.
- **Light / dark / auto theme**: manual toggle in Settings (follows the system by default).
- **System LED disabled** on HyperOS to avoid double lighting (single driver).
- **Configurable light duration** (1–30 s, default 10 s).
- **English / French UI**: language picker on first launch (choice persisted, switchable from Settings).
- Works even when **notification content is hidden on the lock screen** (content recovered via Shizuku — see *How it works*).

## Screenshots

| Main screen | Settings |
|-------------|----------|
| ![](screenshots/main.png) | ![](screenshots/settings.png) |

## Download

Get the latest signed APK from the [releases page](https://github.com/tgvdufuture/Aura/releases/latest):

- **[v0.2.0](https://github.com/tgvdufuture/Aura/releases/tag/v0.2.0)** — `app-release.apk`

## Prerequisites

### Hardware / system
- **POCO X8 Pro** running **HyperOS 3** (Android 16). This is the only targeted device; other models are not supported.
- **Shizuku** installed and enabled (see below).

### Build environment
- JDK **17**
- **Android SDK** (compileSdk 36, minSdk 26)
- Gradle **8.13+** (the wrapper is provided, nothing to install)

## Build

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing on the device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Getting started

1. **Enable Shizuku** on the phone (ADB startup via a PC, or wireless startup), then install the APK.
2. Open **Aura**, choose your language (**English** / **Français**) on first launch, then expand **Settings** and grant:
   - the **notification access**;
   - the **Shizuku permission** (button in the app).
3. Enable the apps you want and choose their **default color**.
4. For messaging/calls, enable **"Identify sender"** then add **contact** / **group** rules (color + animation).
5. Disable the **HyperOS system LED** from **Settings** ("System LED") to avoid double lighting.
6. Recommended: **exclude Aura from battery optimization** (**Settings** → "Robustness") so the service survives in the background.

## How it works

- **`notification/AuraNotificationListener`**: detects notifications (`NotificationListenerService`) and only emits a LED command while the device is not actively used (screen off or locked).
- **`engine/RuleEngine`**: resolves the rule according to the contact → group → app priority.
- **`led/ShizukuLEDController`**: drives the rings via the `miui.lights.ILightsManager` system service (`setCustomLight`), called through Shizuku (allowed shell UID). The color is arbitrary (RGB) and the LED turns off automatically after the timeout.
- **`notification/FullNotificationReader`**: when Android hides notification content on the lock screen, the listener only receives a redacted version (empty title/text). Aura then recovers the full content via `dumpsys notification --noredact` (run through Shizuku) so contact/group rules — and their animations — keep working while the screen is off.
- **`data/`**: local persistence of rules and settings with **Room**.
- **`ui/`**: **Jetpack Compose** UI.

## Tech stack

- Kotlin 2.1, Jetpack Compose (Material 3)
- Room (local persistence)
- Kotlin Coroutines / Flow
- [Shizuku](https://github.com/RikkaApps/Shizuku) (API 13.1.5)

## License

[MIT](LICENSE) — © 2026 Aura Contributors

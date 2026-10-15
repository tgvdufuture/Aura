# Aura — Custom LED and notification light for POCO X8 Pro (HyperOS)

<a href="https://github.com/tgvdufuture/Aura/actions/workflows/build.yml"><img align="middle" alt="Build" src="https://github.com/tgvdufuture/Aura/actions/workflows/build.yml/badge.svg"></a> <a href="https://ko-fi.com/tgvdufuture"><img align="middle" alt="Support me on Ko-fi" src="https://ko-fi.com/img/githubbutton_sm.svg"></a>

Aura is an open-source Android app for **custom LEDs on the POCO X8 Pro**. It drives the **RGB LED rings** on the back of the phone (Xiaomi / **HyperOS 3**) and shows a different **color** and **animation** per **app**, per **contact** and per **group** — **while the screen is off**. It replaces HyperOS's default notification light, which is limited to a single global color with no sender distinction.

If you are looking for a **custom notification LED**, **RGB notification ring**, **back LED effect** or a way to customize the **rear lights on a POCO X8 Pro**, Aura provides per-app and per-sender rules without root. The **POCO X8 Pro Max** (also known as **Redmi Turbo 5 Max**) is supported by the same LED hardware and has been confirmed working by users.

En français : Aura permet de personnaliser la **LED arrière**, la **lumière de notification** et les **anneaux RGB** du POCO X8 Pro sous HyperOS, avec une couleur différente pour chaque application, contact ou groupe.

> Notification LED · RGB LED · light ring · POCO X8 Pro · Xiaomi · HyperOS · Shizuku · Android · custom notification LED · RGB notification ring — **no root required**.

- **Local-first**: every rule stays on your device, there is no backend and no account. Optional anonymous usage statistics are **off by default** and revocable at any time (see [Privacy](#privacy)).
- **In-app updates**: Aura checks GitHub Releases once a day and installs a new version in one tap.
- Hardware control via **Shizuku** (ADB privileges) or, for rooted devices, directly through `su`.
- Resolution priority: **contact > group > app**.

## Features

- **Color per app**: each enabled app has a default LED color.
- **Live LED preview**: tap a color in the picker to preview it on the ring in real time before saving.
- **Color + animation per contact/group**: messages (WhatsApp…) and calls can be distinguished by sender (breathing, flashing, rainbow, alert).
- **Lock screen aware**: the LED drives when the screen is off **or** the device is locked; it stays off only while you're actively using the phone.
- **"Last one wins" with smart fallback**: a new notification replaces the previous state, and dismissing it falls back to the previous one.
- **Auto-restart on boot**: the service and notification listener reconnect automatically after a reboot.
- **Light / dark / auto theme**: manual toggle in Settings (follows the system by default).
- **System LED disabled** on HyperOS to avoid double lighting (single driver).
- **Configurable light duration** (1–30 s, default 10 s).
- **English / French / Russian UI**: language picker on first launch (choice persisted, switchable from Settings).
- Works even when **notification content is hidden on the lock screen** (content recovered via Shizuku — see *How it works*).

## Screenshots

| Main screen | Settings |
|-------------|----------|
| ![](screenshots/main.png) | ![](screenshots/settings.png) |

## Download

Get the latest signed APK from the [releases page](https://github.com/tgvdufuture/Aura/releases/latest):

- **[v0.6.1](https://github.com/tgvdufuture/Aura/releases/tag/v0.6.1)** — `app-release.apk`

> **POCO X8 Pro and X8 Pro Max (Redmi Turbo 5 Max) only.** Aura drives the rear RGB ring, which only
> the Xiaomi/HyperOS family exposes. On any other phone no notification lights anything up, with or
> without Shizuku — the app says so on its first screen and stays usable, but it has no light to drive.

> Already installed? Aura 0.6.0+ tells you when a new version is out and installs it in one tap. Older versions (0.1.0–0.5.1) have no update check, so they need one manual download from the releases page.

## Prerequisites

### Hardware / system
- **POCO X8 Pro** running **HyperOS 3** (Android 16). This is the only device it has been developed and tested on. The **POCO X8 Pro Max** (a.k.a. Redmi Turbo 5 Max) shares the same RGB ring hardware and HyperOS version, and has been **confirmed working** by users.
- **Shizuku** installed and enabled (see below), unless the device has working root access. Root fallback is experimental.

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

Aura needs a few one-time permissions to take control of the ring. Do these in order.

> **Enable the notification LED on the phone first:** turn on the back light in **Settings → Additional settings → Back light effects** (French: *Paramètres supplémentaires → Effets de lumière arrière*). Without it the ring won't light up, even though Aura is running.

### 1. Install Aura

1. Download the latest `app-release.apk` from the [releases page](https://github.com/tgvdufuture/Aura/releases/latest).
2. Open the APK and allow the install when your browser / file manager asks ("Install unknown apps").

### 2. Set up Shizuku or root

Aura drives the LED through **Shizuku** or a root shell. Root users do not need Shizuku; Aura detects root automatically and shows it in Settings.

> **Root support status — not tested:** the root fallback (`su`) is implemented but has not yet been tested on a physically rooted phone. The development phone used for this release has no `su`, so Shizuku is the only validated privilege path. If you test root, grant Aura permanent access in Magisk/KernelSU/APatch and use **Test the LED** first.

**Root:** grant Aura permanent root access in Magisk/KernelSU/APatch, then continue with step 3.

**Shizuku:**

1. Install **Shizuku** from the [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [shizuku.rikka.app](https://shizuku.rikka.app/).
2. Start it, one of two ways:
   - **Wireless (recommended, no PC):** enable **Developer options** (Settings → About phone → tap the **HyperOS version** 7 times), then turn on **Wireless debugging**. In Shizuku, tap **Start → Wireless debugging** and follow the pairing prompts.
   - **Via a PC (ADB):** connect the phone to a computer that has `adb`, then run the command Shizuku shows under **"Start via computer connection" → "Show command"**.
3. Wait until Shizuku shows **"Shizuku is running"**.

### 3. Grant Aura's permissions

Open **Aura**, pick your language, then open **Settings**:

1. **Notification access** — in **Settings → Robustness**, the **notification listener** row shows its status; tap it to open the system screen and enable Aura.
2. **Privilege access** — with Shizuku, tap **"Request authorization"**; with root, grant Aura access in your root manager. Then tap **"Test the LED (red)"** to confirm the ring lights up.

### 4. Let it survive HyperOS (important)

HyperOS aggressively kills background apps, so do both of these in **Settings → Robustness**:

1. Tap **"Open Autostart settings"** and **enable Aura** in the MIUI autostart manager.
2. Tap **"Exclude from battery optimization"** and choose **"No restrictions"**.

> Without autostart, HyperOS may block the notification listener after a reboot.

### 5. Choose your colors

1. On the main screen, toggle the apps you want and pick a **default color** (tap a swatch, or the **+** for a custom color — the ring shows a live preview).
2. For messages and calls, turn on **"Identify sender"** and add **contact** / **group** rules with their own color and animation.

### 6. (Optional) Avoid double lighting

HyperOS lights the ring itself too. In **Settings**, disable **"System LED"** so only Aura drives the ring.

## How it works

- **`notification/AuraNotificationListener`**: detects notifications (`NotificationListenerService`) and only emits a LED command while the device is not actively used (screen off or locked).
- **`engine/RuleEngine`**: resolves the rule according to the contact → group → app priority.
- **`led/ShizukuLEDController`**: drives the rings via the `miui.lights.ILightsManager` system service (`setCustomLight`), using Shizuku when authorized or `su` on rooted devices. The color is arbitrary (RGB) and the LED turns off automatically after the timeout.
- **`notification/FullNotificationReader`**: when Android hides notification content on the lock screen, the listener only receives a redacted version (empty title/text). Aura then recovers the full content via `dumpsys notification --noredact` (run through Shizuku or root) so contact/group rules — and their animations — keep working while the screen is off.
- **`data/`**: local persistence of rules and settings with **Room**.
- **`ui/`**: **Jetpack Compose** UI.

## Frequently asked questions

### Can I customize the LEDs on a POCO X8 Pro?

Yes. Aura customizes the back RGB LED rings of the POCO X8 Pro and can assign a color and animation to each app, contact or group notification.

### Does Aura need root?

No. The validated setup uses Shizuku with ADB-level privileges. An experimental root fallback is also included.

### Does it work on the POCO X8 Pro Max or Redmi Turbo 5 Max?

The POCO X8 Pro Max, also called Redmi Turbo 5 Max, shares the same RGB ring hardware and has been confirmed working by users. Other POCO models are not guaranteed.

### Which HyperOS notification light settings should I use?

Enable the phone's back light effects first, then disable HyperOS's System LED in Aura so that only Aura controls the notification ring and double lighting is avoided.

### Recherches en français

Les termes **LED personnalisée POCO X8 Pro**, **lumière arrière POCO**, **anneau LED de notification HyperOS** et **couleur LED par application POCO** décrivent le même usage pris en charge par Aura.

## Tech stack

- Kotlin 2.1, Jetpack Compose (Material 3)
- Room (local persistence)
- Kotlin Coroutines / Flow
- [Shizuku](https://github.com/RikkaApps/Shizuku) (API 13.1.5)
- [PostHog](https://posthog.com) Android SDK (optional, opt-in anonymous statistics; EU region)

## Privacy

Aura is built to keep your data on your phone, and nothing is collected unless you explicitly agree to it.

- **Off by default.** On first launch Aura shows a short screen explaining what it would send. Choosing *No thanks* means no analytics data ever leaves the device.
- **Anonymous only.** Statistics are tied to a random install identifier generated by the analytics SDK, plus a short-lived session identifier that only groups the events of one visit. No account, no e-mail, no advertising ID, no hardware identifier, and no user profiles are created.
- **Nothing else leaves the device.** Every outgoing event is filtered against a fixed allow-list (`AnalyticsEventFilter`), so the fields the analytics SDK would add on its own — timezone, mobile carrier, user-agent/ROM build string, device codename, screen metrics, internal SDK counters — are dropped before the request is sent. This is enforced in code, not trusted to the SDK's defaults: a key a future SDK version adds is dropped unless it is named in that list.
- **Never collected:** notification content, notification titles or text, contact or group names, sender names, message text, your contacts, or the list of apps installed on your phone.
- **What is collected when you agree:** which features you used (apps you enabled, colours chosen, sender rules created, animations, light duration, reminder settings), each setup step and whether it succeeded (notification access, Shizuku/root, LED test, autostart, battery exemption), your app/Android version, device model, language and theme, plus update checks and download results.
- **Where it goes:** PostHog Cloud in the **EU** (`eu.i.posthog.com`), with IP anonymisation and 12-month retention.
- **Revocable:** the *Anonymous statistics* toggle in Settings stops collection immediately and resets the anonymous identifier. Analytics can be turned off independently of the update check, and the update check can be turned off too if you want no network request at all.

### Permissions added in 0.6.0

| Permission | Why |
|---|---|
| `INTERNET` | Send the optional statistics, and ask GitHub if a newer version exists. |
| `ACCESS_NETWORK_STATE` (normal, added by the analytics SDK) | Skip a report when the phone is offline instead of retrying blindly. |
| `REQUEST_INSTALL_PACKAGES` | Install the update you explicitly asked for. Android still shows its own confirmation, and the APK must be signed with Aura's key to be accepted. |

## Support

Aura is developed on my own time and given away for free, with no ads. If it's useful to you, you can buy me a coffee: **[ko-fi.com/tgvdufuture](https://ko-fi.com/tgvdufuture)** ☕

## License

[MIT](LICENSE) — © 2026 Aura Contributors

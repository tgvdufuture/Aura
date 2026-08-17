# Phase 0 — Feasibility spike (Aura)

**Verdict: GO ✅** — The LED rings (strip light) of the POCO X8 Pro can be driven without root via ADB/Shizuku.

Date: 2026-08-16
Device: POCO X8 Pro — `klee_eea`, model `2511FPC34G`, Android 16 (API 36), HyperOS `OS3.0.303.0.WPJEUXM`.

---

## 1. Summary

The back rings are controlled through a dedicated Miui system service:
**`miui.lights.ILightsManager`** (implemented by `com.android.server.lights.HyperLightsService`).

- The **shell** (uid 2000) — hence **Shizuku** — is explicitly allowed (`checkCallerVerify`).
- The `setCustomLight(...)` method accepts an **arbitrary RGB color** + an animation mode + durations, and **turns off automatically** after `onMs+offMs` (capped at 30 s).
- The LEDs are addressable RGB: **not limited to 8 colors** (the list of 8 is only a UI palette).

---

## 2. F0.1 — Command that lights up the LED ✅

AIDL interface `miui.lights.ILightsManager` (4 methods, transaction codes):

| Code | Method | Signature | Usage |
|---|---|---|---|
| 1 | `setColorfulLight` | `(String pkg, int styleType, int userId)` | Call/alarm effect (color via Settings, no color argument) |
| 2 | `setColorCommon` | `(int color, String pkg, int styleType, int userId)` | Music only (styleType 3) |
| 3 | `setColorLed` | `(int color, String pkg, int styleType, int userId, int category)` | **Empty body** (reserved) |
| 4 | `setCustomLight` | `(int color, int flashMode, int onMs, int offMs, int brightNessMode, String pkg, int styleType, int userId)` | **The way to go**: arbitrary color + animation + auto-off |

### Documented ADB command (reproducible 2/2)

```
adb shell service call miui.lights.ILightsManager 4 \
  i32 <COLOR> i32 <MODE> i32 <ON_MS> i32 <OFF_MS> i32 0 \
  s16 "com.android.camera" i32 12 i32 0
```

- `<COLOR>`: 24-bit RGB int (e.g. `16711680` = red `#ff0000`, `255` = blue `#0000ff`).
- `<MODE>`: `0` = steady, `1` = flash, `2` = breathing.
- `<ON_MS>/<OFF_MS>`: durations; auto-off happens after `onMs+offMs`.
- `s16 "com.android.camera"` and `i32 12`: required (the only pkg accepted for `setCustomLight`); it's a filter on the *string* passed, not on the calling UID.

> ⚠️ **`service call` gotcha**: on this Android (16), `service call` **automatically writes the interface token**. Do NOT pass it as `s16` (otherwise `BadParcelableException: Parcel data not fully consumed`).

Verified in logcat:
```
setCustomLight callingPkg: com.android.camera color:16711680 mode:0 onMs:8000 offMs:0, styleType: 12
realSetLightLocked, # mId: 20, curPriority: 1 color: 32ff0000
```
(`32ff0000` = high byte `0x32` = brightness 50, `0xff0000` = red.)

---

## 3. F0.2 — Inventory of colors / effects / latency ✅

### Colors
- **Arbitrary RGB confirmed** (red, blue, green all accepted and rendered). The "8 colors" limit does not exist at the hardware level.
- UI palette (`light_color_list_new`, `/product/etc/device_features/klee.xml`): 8 presets — `#a5976e #ff0000 #dc2100 #ff8f04 #0aff10 #26ff67 #006c9a #5500f2`.
- UI settings palette (`light_color_setting_list`): 8 light/dark pairs.

### Effects (flashMode)
| flashMode | Effect | Style file | Confirmed |
|---|---|---|---|
| 0 | Steady (continuous color) | — | ✅ red 5 s |
| 1 | Flash (on/off) | `lightstyle_camera.xml` (on 500 / off 500) | ✅ blue 1.5 s/1.5 s |
| 2 | Breathing (ramp) | — | ✅ green 2 s/2 s |

Predefined styles (`/product/etc/lights/lightstyle_*.xml`): notification, phone, alarm, camera, battery, game, game_colorful. Most use `flashMode 0` with long `onMS/offMS` (≈ continuous color).

### Latency
Command → `realSetLightLocked`: **≈ 43–46 ms** (logcat). Well under the 500 ms NFR threshold.

### "Charging" animation (sequence of colors)
Possible by **emulation**: a software loop of `setCustomLight` (the service already does this for game light via `playColorfulGameLightLocked`). Flash mode (1) gives native blinking. → **charging = native flash or emulated sequence**, breathing = flashMode 2 (native). Both are feasible.

---

## 4. F0.3 — Disabling the system LED ✅

| Key | Namespace | Effect | Shell/Shizuku write |
|---|---|---|---|
| `notification_light_pulse` | system | `0` = disables the notification pulse (which maps to the strip) | ✅ **yes** (tested, reversible) |
| `settings_strip_light_enable` | global | `0` = disables the whole strip (battery/notification/call/**our app**) | ❌ **no** — requires `WRITE_SECURE_SETTINGS` |

**Retained disable key: `notification_light_pulse = 0`** (system), reversible (`= 1`), accessible to Shizuku.

> ⚠️ Identified risk: `settings put global` is **blocked for the shell** on HyperOS. If a *global* key must be written (e.g. `settings_strip_light_enable`), it will require either "USB debugging (Security settings)" on the phone, or another route (to investigate in Phase 1). Our MVP does *not* need to write any global key: `setCustomLight` is enough.

### "Single driver" note (US-05)
Our light goes through the **urgent** strip (id 20, priority 1), which **overrides** the system notification strip (id 18, priority 2). Even if the system tries to light its notification LED, ours wins. Combined with `notification_light_pulse=0`, we avoid any double lighting.

---

## 5. Control architecture (for Phase 1)

- Service: `HyperLightsService` (extends `LightsService`), published as `miui.lights.ILightsManager`.
- Strip IDs: `18` notification, `19` music, `20` urgent (all mapped onto the type 4 notification light hardware).
- Priorities: urgent(1) > notification(2) > battery(3) > music(4).
- Permission: `checkCallerVerify` accepts `uid==2000` (shell/Shizuku), `1000`, `1001`, `1013`, `0`. `setCustomLight` has **no UID check** (filters on the `pkg` string).
- Color: `#RRGGBB` → RGB int; high byte = brightness (`lamp_effect_brightness`, default ~50/168 barpos).
- Auto-off: `onMs+offMs` (capped at 30 s).

### LEDController interface (frozen after Phase 0)

```kotlin
interface LEDController {
    // #RRGGBB -> Result<Boolean>
    fun setColor(colorHex: String): Result<Boolean>
    // animationId in {"charging","breathing"}, colorHex #RRGGBB
    fun startAnimation(animationId: String, colorHex: String): Result<Boolean>
    fun stop(): Result<Boolean>
}
```

Mapping to `setCustomLight(color, flashMode, onMs, offMs, brightnessMode=0, pkg="com.android.camera", styleType=12, userId=0)`:
- `setColor(c)`: mode 0 (steady), onMs=`ledTimeoutMs`, offMs=0.
- `startAnimation("breathing", c)`: mode 2.
- `startAnimation("charging", c)`: mode 1 (or emulated color sequence).
- `stop()`: color 0.

Errors:
- `SHIZUKU_UNAVAILABLE` — Shizuku absent / permission revoked.
- `LED_UNAVAILABLE` — strip disabled (`settings_strip_light_enable=0`) or service missing.
- `COLOR_UNSUPPORTED` — not applicable (arbitrary RGB); debug log.
- `ANIMATION_UNSUPPORTED` — fall back breathing → static.

---

## 6. Validation steps (performed)

1. `adb devices` → `BEQWMFQWGMMV8HKZ device`.
2. Identification of Settings keys (`settings_strip_light_enable`, `back_strap_app_notification_color`, `notification_light_pulse`).
3. Decompilation (jadx) of `miui-framework.jar` → AIDL `miui.lights.ILightsManager` + transaction codes.
4. Decompilation of `miui-services.jar` → `HyperLightsService` (permissions, ids, HW mapping, auto-off).
5. Test `service call ... setCustomLight`: steady red 2/2, blue flash, green breathing → confirmed in logcat.
6. Disable test: `notification_light_pulse=0` (write + reversible OK); `settings_strip_light_enable` (blocked for shell).

## 7. Risks

1. **`settings put global` blocked for shell/Shizuku** (WRITE_SECURE_SETTINGS). Impacts any *global* key write. Mitigation: the MVP doesn't need it; otherwise enable "USB debugging (Security settings)".
2. **Unreliable sender extraction** (Snap/Instagram) — to validate in Phase 1 (app fallback).
3. **Shizuku must be re-enabled after reboot** — guide the user (onboarding).
4. **Service killed by HyperOS** — battery exclusion + persistent notification (Phase 1).
5. The **strip is shared** with battery/call/music: our priority 1 overrides everything, but we must ensure auto-off so we don't mask the battery indefinitely.

## 8. Suggested tests

- Unit test of the `#RRGGBB` → int color mapper (and back).
- RuleEngine test: contact > group > app priority; "last one wins".
- Real `LEDController` integration test: setColor/startAnimation/stop on the device (2/2).
- Full flow test: notification with screen off → correct color (≥95% / 50 notifications criterion).
- Shizuku reconnection test after reboot.

## 9. Files changed

- `phase0/REPORT.md` (this report).
- `phase0/dex/jadxout/sources/miui/lights/ILightsManager.java` (decompiled AIDL, Phase 1 reference).
- `phase0/dex/jadxout/sources/com/android/server/lights/HyperLightsService.java` (decompiled implementation, reference).

(No app code produced — Phase 0 = spike. The framework jars/dex extracted were removed.)

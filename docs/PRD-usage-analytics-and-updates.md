# PRD — Opt-in Usage Analytics + In-App Update

**Version:** 1.0  
**Date:** 2026-10-14  
**Status:** Draft (decisions locked by the project owner on 2026-10-14; see §8)

---

## 0. Executive Summary

Aura ships with no network permission and no telemetry, so after 1357 GitHub release downloads the project owner has **zero** usage data: no active install count, no idea which features are used, no idea whether anyone would pay for a premium version. This feature adds (a) an **explicitly opt-in, anonymous usage analytics** pipeline built on PostHog Cloud (EU region) and (b) an **in-app update flow** (GitHub Releases check → dialog → in-app APK download → one-tap install) so that future releases actually reach the installed base.

Primary success metric:
- 30 days after the first release that contains this feature, the PostHog dashboard reports ≥ 150 distinct active installs, a setup funnel with ≥ 200 measurements per step, and a measurable D7 retention rate — enough to decide whether to keep investing or to build a premium tier.

Secondary success metric:
- ≥ 50 % of active installs run the newest release within 30 days of its publication (measured as the version distribution of `app_opened`).

---

## 1. Strategic Context

### 1.1 Problem Statement

- Current situation: `AndroidManifest.xml` declares no `INTERNET` permission; `app/build.gradle.kts` has no analytics dependency; README states "100% local: no network permission, no backend, no telemetry". The only available numbers are GitHub release asset `download_count` values (v0.5.1 = 171, v0.5.0 = 543, v0.4.2 = 484, older = 159, **total 1357**), which count downloads, not users or usage.
- User pain point (project owner): the decision "keep developing free vs. build a premium tier" cannot be made without knowing the active install base, the setup drop-off, feature adoption and retention.
- Why this matters now: the app is distributed as a sideloaded APK, so **every data point requires a new release that users install**. The longer telemetry is deferred, the later the decision can be made. Release download curves show users do re-download (543 in 26 days on v0.5.0; 171 in 5 days on v0.5.1), so an update path exists — it is currently manual and invisible.

### 1.2 Goals

- G1: Measure the active install base (DAU / WAU / MAU on distinct anonymous install IDs) and D1/D7/D30 retention.
- G2: Measure the complete first-run setup funnel and identify the step where users abandon.
- G3: Measure feature adoption: enabled apps, sender rules (contact/group), animations, colors, LED duration, system-LED switch, service state, persistent reminder, theme, language.
- G4: Get ≥ 50 % of active installs onto the newest release within 30 days, via an in-app update prompt with one-tap install.
- G5: Collect a direct monetization-intent signal from consenting users.
- G6: Keep the privacy promise: nothing is collected or sent unless the user explicitly agrees, and the agreement is revocable at any time from Settings.

### 1.3 Non-Goals

This phase will NOT:

- Instrument or otherwise reach users still running v0.1.0–v0.5.1. Those APKs contain no analytics code and no `INTERNET` permission; Android cannot add a permission retroactively. Only a new install produces data.
- Add accounts, logins, emails, push notifications, or remote config.
- Publish to Google Play, or use the Play in-app update API.
- Identify users: `personProfiles = NEVER`, no `identify()` call, no cross-device identity, no advertising ID.
- Send notification content, notification titles, sender names, contact names, group names, message text, `matchKey` values, or a list of installed apps. Only the package name of an app the user explicitly configured may be sent, as an app identity, never as content.
- Change LED behaviour, rule resolution, the reminder loop, the foreground-service lifecycle or the notification listener's behaviour.
- Add crash-reporting SDKs other than PostHog's built-in exception autocapture.
- Build a self-hosted dashboard or backend.

---

## 2. Users & Context

### 2.1 Primary Personas

**P1 — Project owner (analytics consumer)**

- Role: sole developer and maintainer of Aura, distributing signed APKs through GitHub Releases.
- Technical level: developer (Kotlin/Android), reads PostHog dashboards.
- Context: decides alone whether to keep investing free time or start a premium tier; has no revenue today (Ko-fi donations only).
- Tools/environment: GitHub repository + releases, PostHog Cloud (EU) web dashboard, local Android build with a release keystore.
- Main job-to-be-done: answer "is this app worth my time, and would users pay?" from real usage data.

**P2 — Aura end user (analytics subject / updater)**

- Role: POCO X8 Pro (or X8 Pro Max) owner using Aura as their notification LED replacement, installed by sideloading an APK.
- Technical level: non-developer (per the persistent-reminder PRD persona).
- Context: privacy-minded — they chose a sideloaded, no-root, no-ads, no-backend app over a store app; HyperOS 3, Shizuku or root.
- Tools/environment: HyperOS 3, Aura installed, notification access granted, Shizuku running.
- Main job-to-be-done: pick up notifications from the rear LED without handing data to a third party, and get fixes without hunting for the APK by hand.

### 2.2 User Stories

| ID | User Story | Priority |
|----|------------|----------|
| US-01 | As the project owner, I want to see how many installs are active daily/weekly/monthly so that I can judge whether the app has a real user base worth investing in. | P0 |
| US-02 | As the project owner, I want the first-run setup funnel (language → notification access → privilege → LED test → autostart → battery exemption) so that I can find and fix the step where users give up. | P0 |
| US-03 | As the project owner, I want to know which apps and features users actually configure (sender rules, animations, colors, reminder) so that I can prioritise the roadmap and scope a premium tier. | P0 |
| US-04 | As the project owner, I want retention cohorts (D1/D7/D30) so that I can tell whether the app is sticky or a one-week toy. | P0 |
| US-05 | As an Aura user, I want nothing to be collected unless I explicitly agree, to see exactly what would be sent, and to revoke that agreement at any time, so that the app's local-first promise stays true. | P0 |
| US-06 | As an Aura user, I want the app to tell me when a new version exists, and to install it in one tap from inside the app, so that I get fixes without manually browsing GitHub Releases. | P0 |
| US-07 | As the project owner, I want to see the distribution of app versions among active installs so that I can tell whether the update prompt actually works. | P1 |
| US-08 | As the project owner, I want a direct monetization-intent signal (would you pay, for what) so that I can decide whether a premium tier is viable. | P1 |
| US-09 | As an Aura user, I want to disable the update check so that the app makes no network request at all if I ask it not to. | P2 |

### 2.3 User Story Acceptance Criteria

#### US-01 — Active install base

- [ ] AC1: `app_opened` carries the app version and is sent at most once per foreground session; PostHog shows distinct-count insights by day, week and month.
- [ ] AC2: The distinct ID is a randomly generated install UUID with no relation to any hardware, account or advertising identifier.

#### US-02 — Setup funnel

- [ ] AC1: Each of the six setup steps emits exactly one event with the step result, in the order the user performs them.
- [ ] AC2: A PostHog funnel over those events renders step-by-step conversion and drop-off without custom code changes.

#### US-03 — Feature adoption

- [ ] AC1: Enabling/disabling an app, changing its colour, toggling sender parsing, creating/deleting a sender rule, changing an animation, changing the LED duration, toggling the system LED, toggling the foreground service, toggling the reminder and changing its parameters each emit one event.
- [ ] AC2: No event property contains notification content, a sender/contact/group name, message text or `matchKey`.

#### US-04 — Retention

- [ ] AC1: PostHog's retention insight on `app_opened`, weekly and daily, shows D1/D7/D30 cohorts.

#### US-05 — Explicit opt-in

- [ ] AC1: On a fresh install, before the consent screen is answered, zero analytics events leave the device (verified by inspecting PostHog's live event stream while using the app).
- [ ] AC2: The consent screen lists exactly what is collected and links to the plain-language list of events; it is shown once after the language picker, before the main screen.
- [ ] AC3: Settings shows the current state with a toggle; turning it off stops all capture immediately and is persisted across process death and reboot.

#### US-06 — In-app update

- [ ] AC1: With the update check enabled, when the published release is newer than the installed version, a dialog appears showing the new version, the APK size and the release notes, with the actions Update / Later.
- [ ] AC2: Tapping Update downloads the APK with a visible progress indicator and, once the download is complete, launches the system installer for this exact package; if the "install unknown apps" grant is missing, the app explains it and opens the right settings screen.
- [ ] AC3: A corrupt or wrong-package/wrong-version APK is rejected before the installer is launched, with an in-dialog error and no installer intent.

#### US-07 — Version distribution

- [ ] AC1: `app_opened` includes `aura_version` and `aura_version_code`, so a PostHog breakdown shows the share of each version over time.

#### US-08 — Monetization intent

- [ ] AC1: A one-time Settings card lists what a paid version could add, states that the project is funded by donations today with a way to donate, and then asks a single multiple-choice question; the answer is sent as one event property (`answer`) with no free text.

#### US-09 — Update check opt-out

- [ ] AC1: With the update-check toggle off, no request to any host is made by the update flow; the toggle is independent of the analytics consent.

---

## 3. Technical Specification

### 3.1 Architecture Overview

- Stack: Kotlin 2.1, Jetpack Compose (Material 3), Room (existing), coroutines; minSdk 26, targetSdk 36, release builds **not** minified (`isMinifyEnabled = false`).
- New dependency: `com.posthog:posthog-android:3.72.2` (MIT; AAR 349 KB; transitively pulls `com.posthog:posthog:6.47.1`, OkHttp, Gson, Okio, `androidx.lifecycle:lifecycle-process`). Release APK is currently 7.51 MB; expected growth ≈ +2–3 MB unminified.
- GitHub Releases check: `java.net.HttpURLConnection` + `org.json` (both in the Android platform) — no additional HTTP dependency for the update path.
- Database: existing Room `settings` table untouched; new analytics/consent/update state lives in `SharedPreferences` (same convention as `ThemeManager` and `LanguageManager`, and readable synchronously from `Application.onCreate` where Room is not). DB version stays 1, no migration.
- External service: PostHog Cloud **EU** (`https://eu.i.posthog.com`) and `https://api.github.com`. No other host is contacted.
- New manifest permissions: `android.permission.INTERNET`, `android.permission.REQUEST_INSTALL_PACKAGES`.
- Deployment target: existing `app` module; version bumped to `versionCode 10`, `versionName "0.6.0"`.

### 3.2 DO NOT CHANGE

The coding agent must not change:

- `engine/RuleEngine.kt` resolution logic and its API.
- `notification/SenderParser.kt` and `notification/FullNotificationReader.kt`.
- `led/Led.kt` protocol code, `led/SystemLedManager.kt` behaviour, `ledTimeoutMs` semantics (1–30 s clamp).
- `AuraNotificationListener`'s serialized single-threaded emission ordering; the "first emission first" rule; the reminder loop's tick sequence and gates (PRD `docs/PRD-persistent-reminder.md`).
- `AuraForegroundService` lifecycle and New Year scheduling.
- `AppDatabase` schema, entities, version.
- Signing configuration and `keystore.properties` handling.
- Existing UI behaviour of `LanguageToggle`, `ThemeToggle`, `ShizukuCard`, `SystemLedCard`, `HealthCard`, `ReminderCard` beyond adding analytics calls inside their existing callbacks.
- Existing behaviour when the user declines analytics: the app must be byte-for-byte functionally identical to today apart from the update flow.

### 3.3 Data Models

No Room entity or schema change. New `SharedPreferences` file `aura_prefs` (via a new `AnalyticsPrefs` object) with these keys:

No custom install identifier is stored: the SDK's own per-install anonymous ID is used (see D-08), and it is regenerated when the user withdraws consent.

| Store | Key | Type | Notes |
|-------|-----|------|-------|
| SharedPreferences | `analyticsConsent` | String: `"granted"` / `"denied"` / absent | Absent = never asked → consent screen shown. |
| SharedPreferences | `updateCheckEnabled` | Boolean, default `true` | Independent of analytics consent. |
| SharedPreferences | `lastUpdateCheckMs` | Long, default 0 | Throttles the GitHub check. |
| SharedPreferences | `lastPromptMs` | Long, default 0 | Throttles the dialog after "Later". |
| SharedPreferences | `dismissedVersion` | String, default `""` | Release tag the user dismissed permanently. |
| SharedPreferences | `supportAnswer` | String, default `null` | Currently selected support answer; `null` until the user answers. Editable. |
| SharedPreferences | `lastSeenVersionCode` | Int, default 0 | Lets the app emit `app_updated` itself (SDK-independent) and detect a first install. |

### 3.4 API / Interface Contracts

#### `analytics/Analytics.kt` (new, singleton object)

- `fun init(context: Context)` — evaluates consent; if `granted`, sets up the PostHog SDK with `PostHogAndroidConfig(projectToken = BuildConfig.POSTHOG_PROJECT_TOKEN, host = "https://eu.i.posthog.com")`, `optOut = false`, `personProfiles = PersonProfiles.NEVER`, `preloadFeatureFlags = false`, and all automatic capture disabled (`captureApplicationLifecycleEvents`, `captureDeepLinks`, `captureScreenViews`, `capturePushNotification*`); registers super properties (`aura_version`, `aura_version_code`, `android_sdk`, `device_model`). If the token is blank or consent is not `granted`, **the SDK is never initialised** and every subsequent call is a no-op. Safe to call more than once.
- `fun grantConsent(context: Context)` / `fun revokeConsent(context: Context)` — persist the choice; `grantConsent` initialises the SDK, `revokeConsent` calls `PostHog.optOut()` and `PostHog.reset()` so the anonymous ID does not survive.
- `fun onForeground(context)` / `fun onBackground()` — one `app_opened` per foreground session (see F2.2); `onBackground` flushes the queue.
- `fun track(event: String, properties: Map<String, Any?> = emptyMap())` — no-op if not consented; sanitises properties (§ F2.1); never throws.
- `fun screen(name: String)` — no-op if not consented.
- Error cases: any SDK failure is swallowed and logged at `Log.w` with the `AuraAnalytics` tag; analytics never blocks or crashes the LED path.
- Threading: PostHog's SDK is already asynchronous; `track` may be called from any thread.

#### `analytics/AnalyticsEvents.kt` (new)

- Central `object` holding the event-name constants and the allowed property keys, so no call site can invent a property name containing content.
- The allowed property key set is the sanitizer's allow-list (§ F2.1).

#### `analytics/AnalyticsPrefs.kt` (new)

- Mirrors the `ThemeManager`/`LanguageManager` style: `getConsent(context): String?`, `setConsent(context, value: String)`, `getInstallId(context): String?`, `ensureInstallId(context): String`, `isUpdateCheckEnabled(context): Boolean` (default `true`), `setUpdateCheckEnabled(context, Boolean)`, plus the update-bookkeeping getters/setters listed in §3.3.

#### `update/VersionComparison.kt` (new, pure)

- `fun normalize(tag: String): IntArray?` — parses `"v0.6.0"`, `"0.6.0"` and `"v0.6"` into `[0, 6, 0]`; returns `null` for anything else.
- `fun isNewer(current: String, candidate: String): Boolean` — component-wise comparison; missing components count as 0; `null` on either side returns `false` (fail-closed: never prompt on an unparsable tag).
- Error cases: returns `false`, never throws.

#### `update/UpdatePolicy.kt` (new, pure)

- `data class PromptDecision(val shouldPrompt: Boolean, val reason: Reason)`.
- `fun shouldCheck(nowMs: Long, lastCheckMs: Long, checkIntervalMs: Long = 86_400_000L): Boolean` — gates the *HTTP request* itself, so a screen that recomposes often cannot multiply requests; a backwards clock counts as "never checked".
- `fun decide(nowMs: Long, lastPromptMs: Long, dismissedTag: String, latestTag: String, checkIntervalMs: Long = 86_400_000L, rePromptIntervalMs: Long = 259_200_000L): PromptDecision` — suppresses the prompt when `latestTag == dismissedTag`, when less than 24 h passed since the last check, or less than 72 h since the last prompt.
- Error cases: negative/zero timestamps behave as "never happened" (prompt allowed).

#### `update/UpdateChecker.kt` (new)

- `suspend fun check(installedVersionName: String, userAgentVersion: String): CheckResult` on `Dispatchers.IO`.
- `sealed interface CheckResult`: `UpToDate`, `UpdateAvailable(tag: String, versionName: String, apkUrl: String, sizeBytes: Long, notes: String)`, `Failed(reason: String)`.
- Behaviour: `GET https://api.github.com/repos/tgvdufuture/Aura/releases/latest` with `User-Agent: Aura/<versionCode>`, `Accept: application/vnd.github+json`; 10 s connect/read timeouts; parses `tag_name` and the first asset whose name ends in `.apk` (`browser_download_url`, `size`); truncates `body` to 800 characters.
- Error cases: non-200, timeout, malformed JSON, no APK asset → `Failed`, logged, never thrown.

#### `update/ApkDownloader.kt` (new)

- `suspend fun download(context: Context, url: String, expectedSizeBytes: Long, onProgress: (Int) -> Unit): Result<File>` on `Dispatchers.IO`.
- Destination: `context.getExternalFilesDir(null)/update/app-release.apk` (no storage permission needed); the file is deleted before each download.
- Validation before returning success: file exists, length > 1 MB, and `packageManager.getPackageArchiveInfo(path, 0)` reports `packageName == context.packageName` and `versionCode > installedVersionCode`.
- Error cases: any failure returns `Failure` with a loggable reason; partial files are deleted.

#### `update/ApkInstaller.kt` (new)

- `fun canInstall(context: Context): Boolean` — `packageManager.canRequestPackageInstalls()`.
- `fun openInstallPermissionSettings(context: Context)` — `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with the app's package URI, `FLAG_ACTIVITY_NEW_TASK`, wrapped in `runCatching` with an `ACTION_APPLICATION_DETAILS_SETTINGS` fallback.
- `fun install(context: Context, apk: File)` — `FileProvider.getUriForFile(context, "${applicationId}.files", apk)` + `Intent(ACTION_VIEW)` with MIME `application/vnd.android.package-archive`, `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`, wrapped in `runCatching`.

#### Update UI (`ui/Ui.kt`, new `UpdateDialog` composable + `MainViewModel` state)

- `data class UpdateUiState(val phase: Phase, val tag: String = "", val versionName: String = "", val sizeBytes: Long = 0, val notes: String = "", val progress: Int = 0, val error: String? = null)` with `enum class Phase { HIDDEN, AVAILABLE, PERMISSION_REQUIRED, DOWNLOADING, ERROR }`.
- `MainViewModel` exposes `updateState: StateFlow<UpdateUiState>`, `fun checkForUpdate(force: Boolean = false)`, `fun startUpdate()`, `fun openInstallPermissionSettings()`, `fun postponeUpdate()`.
- Error cases: permission missing → `PERMISSION_REQUIRED` (dialog explains and offers the settings button); download failure → `ERROR` with a retry button; `Failed` from the check → state stays `HIDDEN` and is logged.

---

## 4. Features & Requirements

### Phase 1 — Consent, storage, SDK bootstrap

**Goal:** the app can capture events only after an explicit, revocable opt-in, and captures nothing otherwise.  
**Dependency:** None

#### F1.1 — Analytics prefs + build-time token

Related user stories:
- US-01, US-05

Description:
- Add `AnalyticsPrefs` (§3.3) and a `POSTHOG_PROJECT_TOKEN` `buildConfigField` read from `local.properties` (gitignored, default `""`). Enable `buildFeatures { buildConfig = true }`.
- A blank token disables analytics entirely (fail-safe) and logs a one-line warning in debug builds.

Acceptance Criteria:
- [ ] AC1: With an empty token, no PostHog SDK is initialised and no HTTP request is issued.
- [ ] AC2: With a token present and consent granted, `Analytics.init` sets up the SDK exactly once even if called repeatedly.

Edge Cases:
- If `local.properties` is missing (CI), then the build still succeeds and analytics is disabled.
- If `installId` is absent when consent is granted, then a UUID v4 is generated and persisted.

Constraints:
- The token must never be committed to the repository.

#### F1.2 — First-run consent screen

Related user stories:
- US-05

Description:
- New Compose screen shown after the language picker and before the main screen while `analyticsConsent` is absent: a short explanation, a details expander listing the exact events and properties, and two actions (Share anonymous usage / No thanks) plus one independent switch for the update check (default on, no data collected by it). The screen applies `systemBarsPadding()`: Android 15+ draws edge to edge, and without it the title renders inside the status bar.
- Strings added to `values`, `values-fr` and `values-ru`.

Acceptance Criteria:
- [ ] AC1: A fresh install shows the screen exactly once; the choice is persisted and the screen never reappears.
- [ ] AC2: Choosing "No thanks" leaves the app fully functional and results in zero analytics requests.
- [ ] AC3: The details section lists the event names and the fact that no notification content, contact name or message text is ever sent.

Edge Cases:
- If the user backs out or kills the app on this screen, then the screen shows again on next launch (no consent recorded).

Constraints:
- Compose + existing Material 3 patterns only; no new UI dependency.

#### F1.3 — Settings card

Related user stories:
- US-05, US-09

Description:
- New card in `SettingsScreen` after `ReminderCard`: analytics toggle (current state), update-check toggle, a static "what is sent" summary line, and a **"Review the privacy screen"** action that re-opens `ConsentScreen` (with a Close action that keeps the current choice).

Acceptance Criteria:
- [ ] AC1: Toggling analytics on/off takes effect without restarting the app and persists across restarts.
- [ ] AC2: Turning analytics off after a session results in zero further events in PostHog's live stream.

Edge Cases:
- If consent was never asked (older installs upgrading), then the card shows the analytics row as off and turning it on records consent as `granted`.

Constraints:
- Reuse the existing `Card` layout conventions.

#### Phase 1 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality
- [ ] Measured release APK size delta reported (target ≤ +3 MB)

---

### Phase 2 — Event instrumentation

**Goal:** every decision the owner needs (base, funnel, adoption, retention, version spread) is answerable from the dashboard.  
**Dependency:** Phase 1 complete

#### F2.1 — Sanitizer and event catalogue

Related user stories:
- US-03

Description:
- `AnalyticsEvents` + a pure `AnalyticsSanitizer.sanitize(properties: Map<String, Any?>): Map<String, Any>` that drops any key not in the allow-list, any `String` value longer than 64 characters, any `null` and any non-primitive value, keeping the call sites honest.

Acceptance Criteria:
- [ ] AC1: An unknown key is dropped; a known key with a 200-character value is dropped; a known key with a short value passes through.
- [ ] AC2: Unit tests cover the allow-list, the length cap and empty input.

Edge Cases:
- If the same key appears twice, then the last value wins (map semantics).

Constraints:
- Pure Kotlin, no Android imports, unit-tested with JUnit4 as in `RuleEngineTest`.

#### F2.2 — Lifecycle, identity and sessions

Related user stories:
- US-01, US-04, US-07

Description:
- `MainActivity.onCreate`: `Analytics.init(this)`, `screen("main")` / `screen("settings")`, and one `app_opened` per foreground session carrying `aura_version`, `aura_version_code`, `android_sdk`, `device_model`, `locale`, `theme`, `is_first_run` (no recorded run yet), `is_update` (`lastUpdateTime != firstInstallTime`, i.e. the APK replaced an earlier one), `listener_connected` and `privilege` (`shizuku`/`root`/`none`).

Acceptance Criteria:
- [x] AC1: One `app_opened` per foreground session, with all listed properties, including after an update (`is_update = true`).
- [ ] AC2: `screen("settings")` fires when the Settings screen opens and `screen("main")` when it is left.

Edge Cases:
- If `lastSeenVersionCode` is 0, then `is_first_run = true` and `is_update = false`, and `lastSeenVersionCode` is then set to the current `versionCode`.

Constraints:
- No analytics call before `super.onCreate` completes; no analytics work in the notification listener's emission path.

#### F2.3 — Setup funnel events

Related user stories:
- US-02

Description:
- One event per step, emitted from the existing callbacks: `setup_language_selected` `{language}` — reported when consent is granted, because the language picker runs before the consent screen; `setup_notification_access_opened` (emitted by the existing "open notification settings" action); `setup_notification_access_granted` (on the `NotificationListenerState.connected` false→true transition); `setup_privilege_requested` `{kind: "shizuku"|"root"}`; `setup_privilege_granted` `{kind}`; `setup_led_test_run` `{success: Boolean}`; `setup_autostart_opened`; `setup_battery_exemption_requested`; `setup_battery_exemption_granted`.
- The battery/autostart "granted" events are emitted lazily at each `refreshHealth()`/`refreshSystemLed()` call when the state is observed to have flipped, so no extra polling is needed.

Acceptance Criteria:
- [ ] AC1: Each event fires exactly once per user action (no repeats when re-entering a screen).
- [ ] AC2: A PostHog funnel built from `setup_language_selected → setup_notification_access_granted → setup_privilege_granted → setup_led_test_run` renders conversion without further code changes.

Edge Cases:
- If the notification listener is granted outside the app (system settings), then the transition-based event still fires.
- If the state is already granted when the app starts, then the event fires once on first observation, not on every start (persisted flag).

Constraints:
- Analytics calls must not run on the LED emission thread.

#### F2.4 — Feature-usage events

Related user stories:
- US-03

Description:
- Emitted from `MainViewModel` setters and the existing listener, with no content:
  - `app_rule_toggled` `{target_package, enabled}`, `app_rule_color_changed` `{target_package, is_custom}`, `app_rule_previewed` `{is_custom}`,
  - `sender_parsing_toggled` `{target_package, enabled}`, `sender_rule_created` `{kind, target_package, animation_id}`, `sender_rule_deleted` `{kind, target_package}`, `animation_selected` `{animation_id, target_package}` (sender-rule creation is the only place an animation is chosen),
  - `led_duration_changed` `{seconds}`, `system_led_toggled` `{disabled}`, `service_toggled` `{running}`, `listener_reconnect_tapped`, `theme_changed` `{mode}`,
  - `reminder_toggled` `{enabled}`, `reminder_interval_changed` `{interval_s}`, `reminder_quiet_hours_changed`,
  - `led_emitted` `{reason: "app"|"contact"|"group", target_package, is_reminder: Boolean}` from `AuraNotificationListener`.

Acceptance Criteria:
- [ ] AC1: Each of these events appears in PostHog with only the listed properties.
- [ ] AC2: `led_emitted` never contains a sender name, group name, `matchKey`, title or text — verified by unit test on the sanitizer allow-list and by manual inspection of the live event stream.

Edge Cases:
- If the user drags an animation slider, then at most one event per committed value is sent (emit on selection, not on every recomposition).
- If a notification arrives while analytics is off, then the LED path is unaffected (no-op).

Constraints:
- Never send the `matchKey` value; never send label text of installed apps.

#### Phase 2 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality
- [ ] Live event stream inspected once manually for privacy leaks

---

### Phase 3 — Update check, prompt and in-app install

**Goal:** users get the newest build in one tap, so the analytics base keeps growing.  
**Dependency:** Phase 1 (needs `AnalyticsPrefs`); independent of Phase 2.

#### F3.1 — Release check + policy

Related user stories:
- US-06, US-07

Description:
- `VersionComparison`, `UpdatePolicy`, `UpdateChecker` (§3.4) called from `MainViewModel` on screen start, throttled to once per 24 h via `lastUpdateCheckMs`; the check runs only if `updateCheckEnabled`.

Acceptance Criteria:
- [ ] AC1: With the published release newer than the installed version, the state becomes `AVAILABLE`; with equal or older, it stays `HIDDEN`.
- [ ] AC2: No more than one GitHub API request per 24 h period, and none at all when the check is disabled.
- [ ] AC3: `update_check_result` `{result, latest_tag}` is emitted only when analytics consent is granted.

Edge Cases:
- If the tag is unparsable, then no prompt is shown (fail-closed).
- If the network is unavailable, then the dialog is not shown, no error is surfaced to the user, and the next attempt is throttled to 24 h later so a broken network cannot turn every launch into a request.
- If the user chose "Later", then the prompt reappears after 72 h; if they dismissed that specific version, it never reappears for that tag.

Constraints:
- No new HTTP library; use `HttpURLConnection` on `Dispatchers.IO`.
- The check must not run while the app is in the background.

#### F3.2 — Update dialog

Related user stories:
- US-06

Description:
- Compose `AlertDialog` driven by `UpdateUiState`: title with the new version, size in MB, scrollable release notes (max 800 characters), actions Update / Later; a determinate progress bar while downloading; an explanatory state with an "Open settings" button when the install permission is missing; an error state with Retry.

Acceptance Criteria:
- [ ] AC1: Opening the dialog emits `update_prompt_shown`; Update emits `update_prompt_accepted`; Later emits `update_prompt_postponed`.
- [ ] AC2: The dialog is dismissible at every state and never blocks the LED features (closing it leaves the app usable).

Edge Cases:
- If the download fails mid-way, then the partial file is deleted and Retry restarts from zero.

Constraints:
- Compose + Material 3 only.

#### F3.3 — In-app APK install

Related user stories:
- US-06

Description:
- `ApkDownloader` + `ApkInstaller` + `FileProvider` (`${applicationId}.files`, new `res/xml/file_paths.xml`) and `REQUEST_INSTALL_PACKAGES` in the manifest.

Acceptance Criteria:
- [ ] AC1: With the permission already granted, tapping Update ends with the system installer showing this package at the newest version.
- [ ] AC2: Without the permission, the app routes the user to the "install unknown apps" screen for Aura, and after returning, Update proceeds.
- [ ] AC3: An APK whose `packageName` is not `com.aura.led` or whose `versionCode` is not greater than the installed one is never handed to the installer.

Edge Cases:
- If the user cancels the system installer, then the app returns to the normal state with no crash and can prompt again after 72 h.
- If the device has no FileProvider-usable storage, then the download reports a failure rather than crashing.

Constraints:
- Download target is app-specific external storage; no `WRITE_EXTERNAL_STORAGE`.
- Signature compatibility is enforced by Android itself (same signing key); the app does not implement its own signature check.

#### Phase 3 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality

---

### Phase 4 — Dashboard and documentation

**Goal:** the owner can answer the business question from a dashboard, and the public privacy claims match reality.  
**Dependency:** Phases 1–3 complete

#### F4.1 — PostHog dashboard

Related user stories:
- US-01, US-02, US-03, US-04, US-07

Description:
- Written setup guide (`docs/analytics-dashboard.md`) listing the exact PostHog insights to create: active installs DAU/WAU/MAU (`app_opened`, unique), version distribution breakdown (`aura_version`), setup funnel, feature-adoption trends, D1/D7/D30 retention, `led_emitted` totals by reason, update-prompt conversion (`update_prompt_shown` → `update_accepted` → `app_updated`).
- Project configured with EU data residency, IP anonymisation, and 12-month data retention.

Acceptance Criteria:
- [x] AC1: The guide's steps produce a dashboard where every question of §1.2 has a named insight (`docs/analytics-dashboard.md`).
- [ ] AC2: A test event sent from a real device appears in the dashboard within 5 minutes.

#### F4.2 — Public claims updated

Related user stories:
- US-05

Description:
- README: replace "100% local: no network permission, no backend, no telemetry" with an accurate statement (local-first: rules stay on-device; opt-in anonymous statistics, off by default; optional update check; no accounts, no ads, no personal data) and add a "Privacy" section describing exactly what is sent and how to disable it. Update the Download section to the new version. Note the two new permissions and why they exist.
- `docs/index.html`: bump the version reference.

Acceptance Criteria:
- [ ] AC1: No remaining claim in README or `docs/index.html` contradicts the shipped behaviour (no "no network permission", no "no telemetry" without the opt-in qualifier).
- [ ] AC2: The README lists the exact collected data fields.

Constraints:
- Documentation only; no code change.

#### Phase 4 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Documentation matches behaviour
- [ ] Release notes drafted (including the privacy change and how to opt out)

---

### Phase 5 — Monetization intent (P2)

**Goal:** a direct signal on whether a premium tier is viable.  
**Dependency:** Phase 1 complete

#### F5.1 — Donation card, then the support question

Related user stories:
- US-08

Description:
- A **donation card** is always present in Settings (no consent required, since opening a web page reports nothing): it states that Aura has no ads and no paid tier today, that donations pay for the development time, and offers one action to the Ko-fi page. The donation is the support path that exists today, so it is presented as such rather than as an answer option (D-10).
- A **question card** is shown whenever consent is granted, with the stored answer selected (nothing selected the first time), listing what a paid version could add — headlined by a **community animation library** (browse, preview, import animations shared by other users), then rule backup/restore, time-based profiles and Tasker/shortcut automation — and only then asking **how the user would prefer to support Aura**, with four fixed answers (`donation`, `one_time`, `subscription`, `nothing`) → `support_intent` `{answer}` with no free text. The offer must be on screen: a bare "would you pay?" produces an uninformative answer (D-09).
- **Every answer is worded so it reads on its own** — “Faire un don ponctuel”, “Acheter la version payante une fois”, “Payer un abonnement mensuel”, “Aucun paiement, ni maintenant ni plus tard” — rather than leaning on the question above it (the first copy said “Mensuel” and “Ni l’un ni l’autre”). The owner read the card and asked “neither of what?”, while it offered four options, not two: an answer defined by contrast with the others is a copy defect, not a survey detail (owner feedback, 2026-10-14). The fifth answer (`undecided`) was dropped in the same pass — someone who is unsure leaves the choice empty, and the card stays in Settings so they can come back to it.
- Answering `donation` opens the donation page, so the answer is an action and not a recorded intention.
- The answer stays **editable**: the stored choice is shown back as the selected chip, any other chip can be tapped to change it, and the card says so ("Saved — you can change this at any time"). An answer that cannot be revised is a vote the user regrets and cannot take back (owner feedback, 2026-10-14).

Acceptance Criteria:
- [ ] AC1: The donation card is always visible and its action works with analytics off; the question card is shown only with analytics consent, and the selected answer survives an app restart.
- [ ] AC2: Exactly one `support_intent` event is sent per **change** of answer, with `answer` one of the fixed values; re-tapping the selected answer sends nothing.
- [ ] AC3: Every answer label states its own meaning in all three locales; none is written as the negation of the others (guarded by `StringResourceParityTest`).

Edge Cases:
- If the user never opens Settings, then the question is never asked (accepted; Settings is part of the documented setup flow).

Constraints:
- No free-text input, no email capture.

---

## 5. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | Added latency on the notification → LED path | 0 ms (no analytics call in the emission path; `track` returns immediately) |
| Performance | Added release APK size | ≤ +3 MB unminified — **measured: 7 514 594 → 8 413 823 bytes, +899 KB (+12 %)** |
| Performance | Extra battery cost | ≤ 1 % over 8 h idle with analytics on (event batches ≤ 20 events / 30 s, no keep-alive, no polling) |
| Reliability | Analytics/network failure impact | Zero: every call is wrapped, failures logged only |
| Reliability | Update check failure | Silent for the user; logged; no crash |
| Security | Install path | Only an APK with the same `applicationId` and a strictly higher `versionCode` may be handed to the installer |
| Security | Secrets | The PostHog project token lives in `local.properties` (gitignored), never in the repository |
| Privacy | Default state | Collection off until explicit consent; no identifier beyond the SDK's anonymous per-install ID (regenerated on withdrawal) |
| Privacy | Content | No notification content, sender, contact, group, message text or `matchKey`; no installed-app list; no advertising ID |
| Privacy | Region | EU data residency (`eu.i.posthog.com`), IP anonymisation on |
| Privacy | Revocation | One toggle stops capture immediately and resets the anonymous identity |
| Privacy | Network minimalism | Automatic capture (lifecycle, deep links, screen views, feature flags, push subscriptions) is disabled; only explicit events are sent |
| Privacy | Payload | Fail-closed allow-list (`AnalyticsEventFilter`) on the merged event payload: a property the consent screen does not name never leaves the device, including anything a future SDK version adds — measured on a real `app_opened`, 46 → 24 properties, with no `$timezone`, `$network_carrier`, `$user_agent`, `$device_name`, `$screen_*` or `$sdk_debug_*` |
| Error handling | Exception safety | No analytics or update code path may throw out of a UI callback or the listener |
| Accessibility | Consent screen | Screen-reader labels for both actions and the switches (new strings only) |

---

## 6. Success Metrics

### Launch Criteria

Must all be true before shipping v0.6.0:

- [ ] Fresh install with "No thanks" → PostHog live stream shows zero events while using the app for 5 minutes, including enabling an app, changing a colour and running an LED test.
- [ ] Fresh install with consent granted → `app_opened`, `setup_*` and `led_emitted` events visible in PostHog (EU project) within 5 minutes of the actions.
- [ ] Funnel and retention insights render from real events.
- [ ] Update flow verified end-to-end against a real newer release tag: prompt → download → installer → app reports the new version.
- [x] `./gradlew :app:testDebugUnitTest` passes: **68 tests, 0 failures**, including 18 new tests for `AnalyticsSanitizer` (6), `VersionComparison` (5) and `UpdatePolicy` (7).
- [x] Release APK size delta measured: **+899 KB** (7 514 594 → 8 413 823 bytes), well under the +3 MB limit.
- [ ] On a device with analytics off, no request to `eu.i.posthog.com` or `api.github.com` (update check disabled) appears in a network capture.
- [ ] README and `docs/index.html` updated; no contradicting privacy claim remains.

### Ongoing KPIs

- Primary: active installs (DAU/WAU/MAU) and D7/D30 retention of `app_opened`.
- Secondary: setup-funnel completion rate; share of installs on the newest version within 30 days; `support_intent` distribution (read against `support_clicked`, the users who actually opened the donation page).
- Guardrail metrics: crash-free rate unchanged; ratio of `setup_led_test_run{success=false}` unchanged; no increase in uninstall-related complaints; opt-in rate tracked so the sample stays representative (a consent rate below 10 % invalidates conclusions drawn from the dashboard).

---

## 7. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Opt-in rate too low to be representative (< 10 %) | Medium | High | Friendly one-time screen with an explicit "what is sent" section; report the opt-in rate in the dashboard and read feature adoption as *relative* shares rather than absolute counts |
| Existing users (≈ 1357 downloads) never update, so the dashboard understates demand | High | Medium | In-app update flow + release notes + README; interpret early numbers as a floor, and grow the base with each release |
| The PostHog token is committed or abused | Low | Medium | Token in `local.properties` only; rotation documented; ingestion is the same exposure any mobile SDK has |
| APK growth penalises users on mobile data | Medium | Medium | Measure the real delta; if > 3 MB, either enable R8 for release (separate decision) or fall back to a lean `HttpURLConnection` client |
| Users feel betrayed by the change from "no telemetry" | Medium | High | Explicit opt-in, off by default, no third-party tracking of content, revocation in Settings, and a clear release note; README claims updated before the release |
| `REQUEST_INSTALL_PACKAGES` is perceived as invasive | Low | Medium | Used only when the user taps Update; the app explains the settings screen; the update check itself can be disabled |
| Android blocks the install intent on some HyperOS builds | Medium | Medium | Fall back to opening the GitHub release page in the browser when the installer intent fails |
| Analytics events double-count LED activity (`led_emitted` is high volume) | Medium | Low | Only counts and reasons are sent — no per-notification identifier; free tier (1 M events/month) is far above expected volume (≈ 1357 installs × a few hundred events/month) |
| Room schema or listener refactor sneaks in | Low | High | §3.2 DO NOT CHANGE, plus the existing test suite as the regression gate |

---

## 8. Decisions (Locked — 2026-10-14)

### D-01 — Explicit opt-in, off by default
- Decision: analytics requires an explicit choice on a first-run screen; "No thanks" means zero collection; the choice is revocable in Settings.
- Rationale: the app's differentiator is privacy; an opt-out default would break the promise made in the README across 1357 downloads. A consent rate of 20–40 % is expected and accepted; the opt-in rate itself is tracked.
- Impact: US-05, F1.1–F1.3.

### D-02 — PostHog Cloud, EU region
- Decision: SDK `com.posthog:posthog-android:3.72.2`, host `https://eu.i.posthog.com`, `personProfiles = NEVER`, IP anonymisation on, 12-month retention.
- Rationale: free tier of 1 M events/month is ~1000× the expected volume; dashboards, funnels, retention and surveys exist out of the box; EU residency for a French/EU-heavy user base.
- Impact: §3.1, F4.1.

### D-03 — Data depth: full product panel
- Decision: measure the base, the setup funnel, feature adoption, retention, version spread and monetization intent (§2.2).
- Rationale: the decision at stake is "invest more or build premium"; only funnel + retention + adoption answer it.
- Impact: Phase 2, Phase 5.

### D-04 — Update flow: in-app download and install
- Decision: GitHub Releases check → dialog → in-app APK download → one-tap install through the system installer, with `REQUEST_INSTALL_PACKAGES`.
- Rationale: manual re-download is the current bottleneck for both fixes and the data base; a browser redirect would keep the funnel loss.
- Impact: Phase 3. Fallback: open the release page in the browser if the installer intent fails.

### D-05 — Secrets and update check independence
- Decision: the PostHog project token is injected from `local.properties` into `BuildConfig.POSTHOG_PROJECT_TOKEN`; a blank token disables analytics silently (warning logged in debug). The update check is a separate toggle, enabled by default, sends no identifier, and works without analytics consent.
- Rationale: keeps the token out of a public repository; the update mechanism is a functional feature, not analytics, and must work for users who refuse analytics.
- Impact: F1.1, US-09.

### D-06 — No retroactive data
- Decision: accept that v0.1.0–v0.5.1 users produce no data; the analytics base starts with v0.6.0.
- Rationale: physically impossible to instrument an already-installed APK without the permission and code.
- Impact: §1.3 Non-Goals, §7 Risks, §6 metrics framing.

### D-07 — Analytics must never touch the LED path
- Decision: no analytics call inside the notification listener's emission path except `led_emitted`, which is fire-and-forget and no-ops without consent.
- Rationale: protects the core feature (PRD `docs/PRD-persistent-reminder.md` §3.2) from any regression.
- Impact: §5 Performance, F2.4.

### D-09 — The premium offer is shown before the premium question
- Decision: the one-time question presents the candidate premium features first; answers stay fixed and none of them is free text.

### D-10 — Donations are the support path today, not an answer option
- Decision: the always-available card asks for a donation (Ko-fi) and the one-time question covers the paid version; "a donation now" is the first answer and opens the donation page.
- Rationale (owner, 2026-10-14): a question framed only as "would you pay for a premium version?" collects refusals, because there is nothing to buy and nothing to act on. The project's real income is donations, and the paid version is a hypothesis; leading with the donation makes the ask honest and gives the answer a path. The community animation library is the headline premium candidate because it is the one item users cannot build themselves.
- 2026-10-14 (same day): the first version hid the card after one tap, so the answer could not be revoked — the owner reported it ("je ne peux pas le modifier"). The card is now persistent, shows the current choice and accepts a change; the event is sent on change only, and the dashboard reads the latest answer per user.
- 2026-10-14 (second report, same day): "je ne comprends pas tout, ce n'est pas intuitif… Ni l'un ni l'autre x quoi ?" — the labels, not the question, were the problem: "Mensuel" named no object, and "ni l'un ni l'autre" negated a pair the user could not find. The set drops to four answers (`donation`, `one_time`, `subscription`, `nothing`), each stating its own payment ("Faire un don ponctuel", "Acheter la version payante une fois", "Payer un abonnement mensuel", "Aucun paiement, ni maintenant ni plus tard"), and `undecided` is removed — someone unsure leaves the choice empty on a card that stays visible. A parity test now rejects any answer worded by contrast with the others.
- Rationale: owner feedback on the first build — asking "would you pay?" with nothing on screen cannot distinguish "no interest" from "nothing to buy".
- Impact: F5.1.

### D-11 — The region and the payload are checked, never trusted
- Decision: the PostHog project token and `Analytics.HOST` must belong to the same region, and the app enforces its own property allow-list (`AnalyticsEventFilter`) instead of relying on the SDK's defaults.
- Rationale (2026-10-14, first finding): the feature shipped with a **US** project token posting to **`eu.i.posthog.com`**. Nothing visible could have shown it — `POST /batch/` answers `200 {"status":"Ok"}` for an unknown token too, so `Flushed N events successfully` proves the request, never the storage. `POST /decide/?v=3` is the endpoint that discriminates (200 for a token the region knows, 401 `authentication_error` otherwise), and the app's token returned 401 on EU and 200 on US. Every event of that period was accepted and dropped by EU ingest.
- Rationale (same day, second finding): capturing the real payload showed the SDK attaching `$timezone` (`Europe/Paris`), `$network_carrier` (`SFR`), `$user_agent` (the full ROM build string), `$device_name`, screen metrics and a dozen `$sdk_debug_*` counters — none of which the consent screen or the README promises. `PostHogConfig.propertiesSanitizer` receives the merged map (checked in the SDK bytecode: `captureStateless` → `buildProperties` merges the context → `buildEvent` calls `sanitize`), so the promise is now enforced in code. The rejected alternative was to keep everything and disclose it; enforcing keeps the published copy true.
- Impact: §5 Privacy, §6 launch criteria, F1.1, `Analytics.init`, `docs/analytics-dashboard.md`.

### D-08 — No custom install identifier
- Decision: use the analytics SDK's own anonymous per-install ID instead of generating and storing an Aura UUID; `PostHog.reset()` on withdrawal discards it. First-run detection uses a stored `lastSeenVersionCode` instead of the identifier.
- Rationale: one fewer identifier and one fewer persistence path to maintain, with the same DAU/retention capability; less code, less to leak.
- Impact: §3.3 `AnalyticsPrefs`, §3.4 `Analytics`.

No remaining open questions for Phases 1–3.

---

## 9. Appendix

- Design references: GitHub Releases download counts (v0.5.1 = 171, v0.5.0 = 543, v0.4.2 = 484, older = 159; total 1357, read 2026-10-14 via the releases API).
- Related docs: `README.md` (privacy claims to update), `docs/analytics-dashboard.md` (dashboard insight checklist), the project's progress tracker (kept locally, not published), `docs/PRD-persistent-reminder.md` (protected behaviour and PRD format), `docs/index.html` (version reference).
- PostHog Android SDK docs: <https://posthog.com/docs/libraries/android> (`capture`, `screen`, `optIn`, `optOut`, `reset`, `personProfiles`, autocapture).
- Key files: `app/src/main/java/com/aura/led/AuraApp.kt`, `MainActivity.kt`, `ui/Ui.kt`, `notification/AuraNotificationListener.kt`, `data/Data.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`.
- Test patterns: JUnit4 + `kotlinx-coroutines-test`, see `app/src/test/java/com/aura/led/engine/RuleEngineTest.kt` and `service/NewYearSchedulerTest.kt`.
- Glossary: DAU/WAU/MAU = daily/weekly/monthly active users; NLS = NotificationListenerService; FGS = foreground service; FGS subtype `specialUse`; distinct ID = anonymous install identifier.

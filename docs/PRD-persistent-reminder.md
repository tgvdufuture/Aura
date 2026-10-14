# PRD — Persistent LED Reminder (loop until dismissed)

**Version:** 1.1  
**Date:** 2026-09-21  
**Status:** Frozen (open questions resolved, decisions locked)

---

## 0. Executive Summary

Aura currently lights the POCO X8 Pro rear LED rings for at most 30 seconds (`ledTimeoutMs`) when a notification arrives. A user away from the phone during that window misses the light and the notification entirely. This feature adds an **opt-in global "persistent reminder" mode**: while a notification that already drove the LED is still present in the status bar, Aura re-flashes the LED every N seconds, pausing while the screen is on and while Do Not Disturb (DND) or configured quiet hours are active. The loop stops as soon as the notification is dismissed. With several undismissed notifications, flashes rotate through them.

Primary success metric:
- A notification left undismissed still triggers an LED flash at least every (interval + flash duration) while the screen is off, verified over a 10-minute manual test, with the feature off by default on fresh installs.

---

## 1. Strategic Context

### 1.1 Problem Statement

- Current situation: LED lighting is one-shot, bounded by `LED_TIMEOUT_MS` (default 10s, max 30s).
- User pain point: away from the phone, the light window is missed; there is no "missed message" reminder (GitHub discussion #1, mr-gravl, 2026-09-16).
- Why this matters now: the notification-tracking infrastructure (`active` list, `onNotificationRemoved`) already exists; the reminder is an incremental extension, not a rewrite.

### 1.2 Goals

- G1: An undismissed notification re-triggers an LED flash every cycle (interval + flash duration) while the screen is off.
- G2: The loop stops within one cycle after the last driving notification is dismissed.
- G3: Zero reminder flashes while the screen is on, while DND is active, or inside configured quiet hours.
- G4: Flash/interval/hours are user-configurable; the feature ships disabled by default.

### 1.3 Non-Goals

This phase will NOT:

- Implement per-app reminder configuration or per-app opt-out (global toggle only).
- Use `AlarmManager` / exact alarms or any new manifest permission.
- Change the initial emission behavior on notification arrival (existing gating, dedup, and timeout stay as-is).
- Modify LED protocol code (`ShizukuLEDController`, `Animations`) or the `RuleEngine` resolution logic.
- Add notification-content parsing beyond what `AuraNotificationListener` already does.
- Gate the initial arrival flash on DND/quiet hours (D-01: night suppression applies to the reminder loop only).
- Provide a "test reminder" preview button (D-04: deferred post-v1).

---

## 2. Users & Context

### 2.1 Primary Persona

- Role: POCO X8 Pro owner using Aura as their notification LED replacement.
- Technical level: non-developer (discussion author states they are not a developer).
- Context: phone on a desk or charging in another room, screen off, HyperOS aggressive battery management.
- Tools/environment: HyperOS 3, Aura installed with Shizuku, notification-listener granted.
- Main job-to-be-done: notice from a distance that a message is waiting, even minutes after it arrived.

### 2.2 User Stories

| ID | User Story | Priority |
|----|------------|----------|
| US-01 | As a POCO X8 Pro owner away from my phone, I want the LED to re-flash every N seconds while a notification stays undismissed so that I notice it when I return. | P0 |
| US-02 | As a user, I want the reminder loop to stop as soon as the notification is dismissed so that handled messages stop flashing. | P0 |
| US-03 | As a user, I want no reminder flashes while my screen is on so that the loop never distracts me during use. | P0 |
| US-04 | As a user, I want the reminder suppressed during DND and during a configurable quiet-hours window so that blinking does not disturb my sleep. | P0 |
| US-05 | As a user, I want a settings section to enable the reminder and set the interval and quiet hours so that I control the aggressiveness and battery impact. | P0 |
| US-06 | As a user with several undismissed notifications, I want flashes to rotate through them so that I can tell multiple apps are waiting. | P0 |

### 2.3 User Story Acceptance Criteria

#### US-01 — Re-flash while undismissed
- [ ] AC1: With the feature enabled and screen off, a notification with an active rule produces at least one LED flash per (interval + flash duration) cycle for as long as it remains in the status bar.
- [ ] AC2: With the feature disabled (default), behavior is byte-for-byte identical to today (no loop, no extra flash).

#### US-02 — Stop on dismissal
- [ ] AC1: Dismissing the last driving notification stops the loop and the LED is off within one cycle (no flash after the removal tick).
- [ ] AC2: Dismissing one of several notifications keeps the loop running for the remaining ones.

#### US-03 — Screen-on pause
- [ ] AC1: No reminder flash occurs while `PowerManager.isInteractive` is true.
- [ ] AC2: Flashes resume within one cycle after the screen turns off again (resume latency ≤ interval).

#### US-04 — DND / quiet hours
- [ ] AC1: No reminder flash occurs while `NotificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL`.
- [ ] AC2: No reminder flash occurs while the current time is inside the configured quiet-hours window, including a window that wraps midnight (e.g. 23:00–07:00).

#### US-05 — Settings UI, default off
- [ ] AC1: Fresh install reports the reminder disabled; enabling persists across process death and reboot.
- [ ] AC2: Interval is configurable in 5s steps within 5–120s and is clamped to that range regardless of input.

#### US-06 — Round-robin
- [ ] AC1: With 2+ tracked notifications active, consecutive flashes cycle through all of them (order: most-recent-first list, rotating index) instead of repeating one.
- [ ] AC2: A notification dismissed between two ticks is skipped in the rotation without breaking the cycle.

---

## 3. Technical Specification

### 3.1 Architecture Overview

- Stack: Kotlin, Jetpack Compose (UI), Room (settings), `NotificationListenerService`, Shizuku LED controller, persistent foreground service (`AuraForegroundService`, `START_STICKY`).
- Frameworks: existing only; no new dependencies.
- Database: existing Room `settings` key/value table (no entity change, no migration, DB version stays 1).
- APIs: `StatusBarNotification` keys via `getActiveNotifications()`, `NotificationManager.currentInterruptionFilter`, `PowerManager.isInteractive`.
- External services: none.
- Deployment target: existing app module (`minSdk`/`targetSdk` unchanged).

Core design: a reminder loop coroutine runs inside `AuraNotificationListener`'s existing serialized scope (`Dispatchers.IO.limitedParallelism(1)`), so all LED emissions (initial + reminder) stay ordered. Tick sequence: **gates → select → emit → delay**. The loop is started lazily on the first qualifying emission and cancelled when the tracked set becomes empty or the feature is disabled. It survives listener rebinds (process stays alive via the foreground service).

New pure-logic helpers, unit-testable without Android (mirrors `RuleEngineTest` style):
- `QuietHours.isInRange(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean` — handles wrap-around; `start == end` means disabled.
- `ReminderCycle.nextIndex(current: Int, size: Int): Int` and stale-key skipping for the rotation.

### 3.2 DO NOT CHANGE

The coding agent must not change:

- `RuleEngine.kt` resolution logic and its API.
- `SenderParser.kt` and `FullNotificationReader.kt`.
- `ShizukuLEDController` internals and `ledTimeoutMs` semantics (1–30s clamp).
- `AppDatabase` schema, entities, and version (settings table reused as-is).
- `AuraForegroundService` lifecycle and New Year scheduling.
- Initial-arrival emission behavior: screen/lock gating, `DUPLICATE_EMISSION_WINDOW_MS` dedup, and fallback-to-previous on removal.
- The single-threaded serialized emission ordering of `AuraNotificationListener`.

### 3.3 Data Models

No entity changes. New rows in the existing `settings` table via new `SettingsKeys` constants:

| Entity | Field (key) | Type (stored) | Notes |
|--------|-------------|----------------|-------|
| Setting | `reminderEnabled` | String `"true"/"false"` | Default `"false"` (opt-in). |
| Setting | `reminderIntervalMs` | String, ms integer | Pause between flashes; clamp 5_000–120_000; default `"15000"`. |
| Setting | `reminderQuietStart` | String `"HH:mm"` | Default `"23:00"`. |
| Setting | `reminderQuietEnd` | String `"HH:mm"` | Default `"07:00"`; `start == end` disables quiet hours. |

Flash duration is NOT a new setting: each reminder flash lasts `LED_TIMEOUT_MS` (the existing 1–30s duration slider), then the LED is dark for `reminderIntervalMs`.

### 3.4 API / Interface Contracts

#### `SettingsKeys` (companion additions, `data/Data.kt`)
- Input: none.
- Output: `REMINDER_ENABLED`, `REMINDER_INTERVAL_MS`, `REMINDER_QUIET_START`, `REMINDER_QUIET_END` constants.
- Error cases: none.

#### `MainViewModel` reminder API (`ui/Ui.kt`)
- State: `data class ReminderState(enabled: Boolean = false, intervalMs: Long = 15_000L, quietStart: String = "23:00", quietEnd: String = "07:00")` exposed as `StateFlow`.
- Methods: `setReminderEnabled(Boolean)`, `setReminderIntervalMs(Long)` (clamps to 5–120s, rounds to 5s steps), `setQuietHours(start: String, end: String)` (validates `HH:mm`, invalid input ignored).
- Load: reads the four keys in `init` like `LED_TIMEOUT_MS` does; writes go through `repository.setSetting`.
- Error cases: malformed stored values fall back to the defaults above.

#### Internal loop contract (`AuraNotificationListener` + new `ReminderCycle`/`QuietHours` helpers)
- Start: after the first successful `emit(command)` for a notification whose key is tracked, if `reminderEnabled`.
- Tick (every `intervalMs` after the previous flash finishes):
  1. Gates — all must hold, otherwise skip the tick (loop stays alive): feature enabled; screen off (`isInteractive == false`); `currentInterruptionFilter == INTERRUPTION_FILTER_ALL`; current time outside quiet hours; tracked set non-empty.
  2. Resync — prune tracked entries whose key is absent from `getActiveNotifications()`; if the set is empty, cancel the loop (LED already stopped by the removal handler). Call wrapped in `runCatching`; on failure skip the tick.
  3. Select — next entry via round-robin index, skipping entries pruned in step 2.
  4. Emit — `emit(entry.command)` (same path as initial emission, so dedup/timeouts apply identically; min interval 5s keeps identical commands outside the 2s dedup window).
  5. Delay — `delay(intervalMs)`, then next tick. Cycle period = flash (`LED_TIMEOUT_MS`) + `intervalMs`.
- Stop conditions: tracked set empty; feature disabled at a gate check; listener destroyed (`scope.cancel()` already in `onDestroy`).
- Error cases: Shizuku emission failure → log and continue; Room config read failure → keep last in-memory config.
- Deep Doze note: `delay` may stretch while the device is idle. Accepted (fewer night flashes); no wake-locks, no exact alarms.

---

## 4. Features & Requirements

### Phase 1 — Settings storage & core loop

**Goal:** an opt-in loop re-flashes tracked notifications until they are cleared.  
**Dependency:** None

#### F1.1 — Reminder settings keys & persistence

Related user stories:
- US-05

Description:
- Add the four `SettingsKeys` constants, ViewModel state and setters (contract §3.4), with clamping and default-off.

Acceptance Criteria:
- [ ] AC1: Fresh install yields `ReminderState(enabled = false, intervalMs = 15_000, quietStart = "23:00", quietEnd = "07:00")`.
- [ ] AC2: `setReminderIntervalMs(3_000)` stores 5_000; `setReminderIntervalMs(500_000)` stores 120_000; values persist after process restart.

Edge Cases:
- If a stored value is malformed, then the default is used.
- If `start == end` quiet hours, then quiet hours are disabled.

Constraints:
- Room schema untouched; settings table only.

#### F1.2 — Reminder loop in `AuraNotificationListener`

Related user stories:
- US-01

Description:
- Coroutine loop per §3.4 tick sequence, reusing the serialized scope and `emit()`.

Acceptance Criteria:
- [ ] AC1: With the feature on, screen off, no DND, one tracked notification, a flash occurs every (interval + flash) cycle while the notification remains posted.
- [ ] AC2: With the feature off, no loop starts and behavior matches current code paths exactly.

Edge Cases:
- If `getActiveNotifications()` throws (not connected), then skip the tick.
- If the process dies, then the loop restarts on the next notification emission (no persistence needed).

Constraints:
- No new thread, no wake-lock, no manifest change.

#### F1.3 — Stop conditions

Related user stories:
- US-02

Description:
- Prune against real status-bar keys each tick; cancel when empty; rely on existing `handleNotificationRemoved` for LED-off and fallback.

Acceptance Criteria:
- [ ] AC1: Dismissing the last tracked notification produces no further flash and the LED is off (existing removal path).
- [ ] AC2: Dismissing one of several keeps flashing the remaining ones (existing fallback + loop continues).

Edge Cases:
- If a notification is updated (same key re-posted), then it stays a single rotation entry.

Constraints:
- Do not duplicate removal handling; reuse `onNotificationRemoved` flow.

#### Phase 1 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality

### Phase 2 — Night suppression (DND + quiet hours)

**Goal:** zero reminder flashes at night or during DND.  
**Dependency:** Phase 1 complete

#### F2.1 — DND gate

Related user stories:
- US-04

Description:
- Gate check `currentInterruptionFilter == INTERRUPTION_FILTER_ALL` before each tick's emission (already in §3.4 sequence; shipped as its own testable gate).

Acceptance Criteria:
- [ ] AC1: With DND active, zero reminder flashes while the loop remains alive.
- [ ] AC2: Leaving DND resumes flashes within one cycle.

Edge Cases:
- If the filter is `INTERRUPTION_FILTER_UNKNOWN`, then treat as suppressed (fail-safe for night use).

Constraints:
- Only the reminder loop is gated; the initial arrival flash keeps current behavior.

#### F2.2 — Quiet hours gate

Related user stories:
- US-04

Description:
- `QuietHours.isInRange` pure helper + gate check; settings `reminderQuietStart`/`reminderQuietEnd` in `HH:mm`.

Acceptance Criteria:
- [ ] AC1: Unit tests cover in-range, out-of-range, wrap-around (22:00→07:00) and `start == end` (disabled).
- [ ] AC2: With a wrapping window configured and current time inside it, zero reminder flashes.

Edge Cases:
- If parsing fails, then the default window (23:00–07:00) applies.

Constraints:
- Local time of the device; no timezone conversion logic.

#### Phase 2 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality

### Phase 3 — Round-robin rotation

**Goal:** consecutive flashes rotate through all tracked notifications.  
**Dependency:** Phase 2 complete

#### F3.1 — Rotation index

Related user stories:
- US-06

Description:
- `ReminderCycle` pure helper holds the rotation index over the most-recent-first tracked list; pruned keys are skipped; the initial arrival emission does not disturb the index.

Acceptance Criteria:
- [ ] AC1: With 2+ tracked notifications, consecutive flashes follow the list order cyclically (unit-testable via the helper).
- [ ] AC2: A key pruned between ticks is skipped; the cycle never emits for a stale key.

Edge Cases:
- If only one notification remains, then every flash is that one.

Constraints:
- Rotation state lives in the listener instance; reset when the loop restarts.

#### Phase 3 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality

### Phase 4 — Settings UI

**Goal:** the user can enable/configure the reminder from the Settings screen.  
**Dependency:** Phase 1 complete (can ship in parallel with 2–3)

#### F4.1 — Reminder card in `SettingsScreen`

Related user stories:
- US-05, US-04

Description:
- New card after `HealthCard`: enable switch, interval slider (5–120s, 5s steps), two `HH:mm` time inputs for quiet hours. Strings added to every existing `values*` folder (English + French at minimum).

Acceptance Criteria:
- [ ] AC1: Toggling the switch immediately persists and starts/stops the loop on the next tick (no restart needed).
- [ ] AC2: Slider and time inputs reflect the stored state after app restart; invalid time input is rejected and does not overwrite the stored value.

Edge Cases:
- If the user sets start == end, then quiet hours are disabled (card shows a hint).

Constraints:
- Compose + existing card patterns only; no new libraries.

#### Phase 4 Completion Checklist

- [ ] All P0 user stories implemented
- [ ] Main flow works end-to-end
- [ ] No regression in protected functionality

---

## 5. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | No added latency to first-emission path | 0 ms added before the initial `emit()` |
| Reliability | Loop survives listener rebind | Loop state kept while the process lives; resync via status-bar keys each tick |
| Reliability | Recovery after process death | Loop restarts on next notification; no stale loop state restored |
| Battery | No wake-locks, no exact alarms, interval floor | ≥ 5s pause between flashes; manual 8h idle test with 1 undismissed notification at 15s interval shows ≤ 2% extra drain |
| Security | No new permissions, no new IPC | 0 new manifest entries |
| Privacy | No data leaves the device | No network calls added |
| Error handling | Emission/reader failures | Log via existing `Log.d/w` tags, skip tick, never crash the listener |

---

## 6. Success Metrics

### Launch Criteria

Must all be true before shipping:

- [ ] Manual 10-min test: 1 undismissed notification, screen off → ≥ 1 flash per (interval + flash) cycle.
- [ ] Dismissal stops flashes within one cycle.
- [ ] DND on → 0 reminder flashes; quiet hours active → 0 reminder flashes.
- [ ] Screen on → 0 reminder flashes.
- [ ] Fresh install: feature off; enabling via UI works after process restart.
- [ ] Existing test suite (`./gradlew test`) passes, including new `QuietHours`/`ReminderCycle` unit tests.

### Ongoing KPIs

- Primary: GitHub discussion #1 closable with the shipped build (reporter confirms).
- Secondary: battery-drain reports in the XDA/Reddit threads show no regression.
- Guardrail metrics: crash-free rate unchanged; notification-listener connection rate unchanged.

---

## 7. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Deep Doze stretches `delay`, flashes become sparse | High | Low | Accepted by design (fewer night flashes); documented in settings help text |
| Battery impact on always-on notifications | Medium | Medium | Opt-in default-off; 5s interval floor; quiet hours; flash reuses ≤30s timeout slider |
| HyperOS kills the listener mid-loop | Medium | Medium | Existing foreground service + rebind + autostart guidance already handle this; loop is stateless across restarts |
| Reminder emissions interact with 2s dedup window | Low | Low | Interval floor 5s keeps identical consecutive commands outside the window |
| Round-robin confuses "which app is calling" | Low | Low | Document in help text; single-notification case is unchanged |
| DND read fails or returns UNKNOWN | Low | Low | Fail-safe: treat as suppressed |
| User forgets quiet hours are on by default (D-02), loop "mysteriously" pauses at night | Medium | Low | Settings-card hint naming the active window; help text in the reminder card |

---

## 8. Decisions (Frozen — 2026-09-21)

All Phase-1-blocking questions are resolved. A decision can only be changed by the project owner before implementation starts.

### D-01 — Night suppression applies to the reminder loop ONLY
- Decision: DND and quiet hours never suppress the initial arrival flash.
- Rationale: the initial flash is Aura's core feature and is protected by DO NOT CHANGE (§3.2); the discussion request explicitly targets the loop ("the loop should be disabled automatically"). Users who want total silence at night can disable the app's rule instead.
- Impact: §1.3 Non-Goals; F2.1/F2.2 gates are evaluated on reminder ticks only.

### D-02 — Quiet hours ship ENABLED with defaults 23:00–07:00
- Decision: keep `reminderQuietStart = "23:00"`, `reminderQuietEnd = "07:00"` as shipped defaults.
- Rationale: the reminder is opt-in, so anyone enabling it gets night protection out of the box; the failure mode "forgot to configure, woken at 3 AM" is worse than "had to disable quiet hours". The settings card shows an explicit hint that start == end disables quiet hours.
- Impact: §3.3 defaults unchanged; F4.1 must render the hint.

### D-03 — Interval range confirmed: 5–120s, step 5s, default 15s
- Decision: keep the 5–120s clamp.
- Rationale: the 15s default sits inside the discussion's proposed 10–15s band; the 5s floor keeps consecutive identical commands outside the 2s dedup window (§7) and bounds the worst-case duty cycle; 120s serves discreet use.

### D-04 — No "test reminder" button in v1
- Decision: deferred to post-v1 backlog.
- Rationale: the loop can be validated end-to-end with a real notification (launch criteria §6); a test button requires a new ViewModel→listener command path, which is scope creep for v1.
- Deferred idea: a card button "preview one reminder cycle" via a singleton event channel (pattern: `NotificationListenerState`).

No remaining open questions.

---

## 9. Appendix

- Design references: GitHub discussion #1 — "Feature Request: Persistent looping/blinking until notifications are dismissed (with DND support)" by mr-gravl, 2026-09-16 (pinned, Ideas category).
- Related docs: `README.md` (app overview), existing tests in `app/src/test/` (`RuleEngineTest`, `NewYearSchedulerTest` as unit-test patterns; JUnit4 + `kotlinx-coroutines-test`).
- Key files: `app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt`, `app/src/main/java/com/aura/led/data/Data.kt`, `app/src/main/java/com/aura/led/ui/Ui.kt`, `app/src/main/res/values*/strings.xml`.
- Glossary: NLS = NotificationListenerService; DND = Do Not Disturb; FGS = Foreground Service; Doze = Android deep idle state stretching timers.

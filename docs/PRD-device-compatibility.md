# PRD — Device compatibility gate

**Version:** 1.0
**Date:** 2026-10-15
**Status:** Draft (owner decisions locked 2026-10-15, see Decisions)

## Summary

Aura's only LED backend is `miui.lights.ILightsManager` (HyperOS), so the app can light the rear
ring on the Xiaomi family and nowhere else — but nothing tells a visitor that before the download
and nothing tells a user that inside the app. Of the 6 external installs of v0.6.0 in its first
12 h, 5 were non-Xiaomi phones where the LED test failed 12 attempts out of 12 and no LED command
ever succeeded; this adds a one-time full-screen notice inside the app (and keeps the standing band
on the app list) plus one compatibility block at the download call-to-action.

## User Story

**US-01** — As a phone owner searching for a notification light, I want to know before downloading
whether Aura can drive **my** phone, so that I do not install an app that can never work for me.

**US-02** — As the maintainer, I want an unsupported device to be told it is unsupported, so that
the setup funnel and the monetization read stop being dominated by installs that cannot succeed.

## Acceptance Criteria

- [x] AC1 (US-01): The download call-to-action on `docs/index.html` states, in the same block as
      the download button and **before it in DOM order**, that Aura drives the rear light ring of the
      Xiaomi/HyperOS family only, and names the supported devices (POCO X8 Pro, X8 Pro Max / Redmi
      Turbo 5 Max). Measured on the rendered page: at a 1407 px viewport the statement sits in the
      left column beside the button (statement top 4829 px, button top 4718 px); on the
      single-column layout (489 px) it sits directly above it (statement bottom 6611 px, button top
      6643 px). "Above the button" is literally true on the narrow layout only — the desktop column
      puts the condition left of the action, which is the page's existing composition.
- [x] AC2 (US-01): The same statement appears in `README.md` immediately after the download link,
      before the existing "Prerequisites" section.
- [x] AC3 (US-01): Neither text claims wider compatibility than the README's current supported-device
      list, and neither contradicts the FAQ entry "Other POCO models are not guaranteed". Both
      statements lead with the restrictive clause ("POCO X8 Pro, X8 Pro Max (Redmi Turbo 5 Max)
      only.") and the Xiaomi/HyperOS mention is given as the reason, never as a promise.
- [ ] AC4 (US-02): On a device whose manufacturer **and** brand are outside the Xiaomi family
      (`xiaomi`, `redmi`, `poco`, case-insensitive), and only the first time, the app shows a
      **full-screen notice** after the privacy screen: it says Aura cannot drive this phone's light,
      names the phones that work, and offers a single action, **Continue anyway**, which records the
      acknowledgement and lets the user in. Nothing is blocked and no feature is withheld.
- [x] AC5 (US-02): The notice is absent on a Xiaomi-family device, and absent when the manufacturer
      or brand is null/blank (unknown is never reported as unsupported). Rule-level, seven unit tests
      covering both the standing band (`shouldWarn`) and the once-only screen (`shouldShowScreen`).
- [x] AC6 (US-02): The rule is pure Kotlin, unit-tested, and decides from the two strings alone
      (no ServiceManager probe, no hidden API, no new permission).
- [x] AC7 (US-02): Copy exists in `values`, `values-fr` and `values-ru`, and the new keys are added
      to `StringResourceParityTest.featureKeys` so a locale that misses one fails the suite. Suite:
      **134 tests, 0 failures** (`./gradlew :app:testDebugUnitTest --rerun-tasks`), 6 of them new.

## Non-Goals

- **No LED backend for other phones.** The camera-torch path (Camera2 `setTorchMode`, permission-free,
  works on any flash) is explicitly deferred until the funnel shows a demand that justifies a second
  product; it is not built here. A generic `/sys/class/leds` path is rejected: root-only on modern
  SELinux, and phones that still have a notification LED already blink it natively.
- **No new analytics event or property.** `device_model` and `$device_manufacturer` are already sent on
  every event and already disclosed, so non-addressable installs are countable today with
  `uniq(distinct_id)` filtered on `$device_manufacturer`; adding a field would have meant a new
  disclosure for no new information. The dashboard note in `docs/analytics-dashboard.md` gets the query.
- **No wider Xiaomi claim.** Testing and announcing the other ring models (POCO F7 / Redmi Turbo 4 Pro,
  POCO F9 Ultra) is a separate task requiring a device pass.
- No blocking, no gating of features, no uninstall prompt.

## DO NOT CHANGE

- `led/ShizukuLEDController` and its `miui.lights` transactions; `led/SystemLedManager` behaviour.
- `notification/AuraNotificationListener`'s emission path (PRD D-07: nothing added there).
- `AppDatabase` schema, `AnalyticsEvents.PROPERTY_KEYS`, `AnalyticsEventFilter.ALLOWED_KEYS`.
- `docs/PRD-usage-analytics-and-updates.md` §3.2 list and every locked decision in it.

## Decisions (locked by the owner, 2026-10-15)

- **D-12 — Filter first, torch later.** Non-Xiaomi traffic is stopped by copy, not by a new backend;
  the torch fallback is revisited only if the numbers ask for it.
- **D-13 — Detect before widening.** In-app compatibility detection lands before any claim about
  other Xiaomi ring models.
- **D-14 — A full screen once, a band forever after (owner choice, 2026-10-15).** The first launch
  on an unsupported phone gets a full screen with one action, "Continue anyway"; later launches keep
  only the quiet band on the app list. "Continue anyway" is deliberate: the family rule is a
  heuristic on two strings, so a phone that would in fact work (a rebrand, a HyperOS port) must never
  be locked out — and its LED test still reaches PostHog, which is how the rule would be proven too
  strict.

## Verification status

- AC1–AC3 are verified by **measuring the rendered page** (headless Chrome geometry probe at two
  viewports) and by reading the README; the impeccable detector was run over `docs/index.html` and
  reports 12 findings, **none on the new element** (they are the page's pre-existing ones: hero
  eyebrow, all-caps labels, tracking, the display font, numbered sections).
- AC4 is **verified on hardware except for its trigger**, and stays unchecked for that one reason.
  The two surfaces, the single action, the acknowledgement and the `BackHandler` were seen on the
  owner's POCO X8 Pro (2026-10-15, `BEQWMFQWGMMV8HKZ`) through a throwaway build whose identity was
  made switchable at runtime (`CompatDemo` + `AURA_DEBUG_COMPAT` markers, all removed afterwards;
  `grep -rn AURA_DEBUG_COMPAT app/src/main` is empty). Read from `uiautomator dump` with the dumped
  package checked as `com.aura.led` every time: launch → the full screen with its French copy and a
  full-width button at `[386,1683][883,1748]`; **Continue anyway** → list visible, band present, and
  the misleading "no app lights up yet" empty state absent; the identity flipped the other way → the
  band gone and the list normal, then back; relaunch → the screen returns; `KEYCODE_BACK` → identical
  to the button. The clean release was rebuilt and reinstalled after that pass, and the same phone
  renders the list with **zero occurrences** of the compatibility copy — the "it works" case. What
  cannot be produced on that phone is the input itself: `Build.MANUFACTURER` / `Build.BRAND` on a
  genuinely non-Xiaomi device, which is what both call sites read (unreachable from the JVM suite;
  the repo still has no `androidTest` source set). The rule is nonetheless asserted against the
  **real** manufacturer/brand pairs read back from PostHog for the five non-Xiaomi installs.

## Risks & Open Questions

- **Assumption:** manufacturer/brand is enough to decide. A HyperOS port on a non-Xiaomi device could
  be wrongly flagged; the LED test remains the truth shown in Settings, and the notice is
  non-blocking, so the cost of a false positive is one paragraph, not a broken app.
- **Open:** a Xiaomi-family device *without* the ring (README: "other POCO models are not guaranteed")
  gets no notice, because a failed LED test cannot be attributed to hardware while the privilege
  grant is a separate step. Revisit if such installs appear in the data — the insight that would show
  it is `led_emitted` / `setup_led_test_run` broken down by `device_model` in
  `docs/analytics-dashboard.md`.
- **Not measured:** whether the notice changes what a non-Xiaomi user does. Nothing is reported when
  it is shown or acknowledged (no new event, no new property), so its effect is read as the volume of
  `setup_led_test_run` from non-Xiaomi devices before and after it shipped, never per user.
- **Verification of AC1–AC3 is by reading the rendered page and the README**, not by an automated
  test: the repo has no HTML/README assertion suite and inventing one is out of scope.

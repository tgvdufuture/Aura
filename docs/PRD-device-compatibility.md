# PRD — Device compatibility gate

**Version:** 1.1
**Date:** 2026-10-15, revised 2026-10-16
**Status:** Draft (owner decisions locked 2026-10-15, see Decisions)

> **v1.1 (2026-10-16).** The installs the *Risks* section was waiting for appeared, and the open
> question it named — "a Xiaomi-family device without the ring gets no notice" — is answered: the
> rule now takes the phone's **own LED test** as a second input (AC8, D-15). One Redmi Note 9 had
> reached **80 failed attempts** with no LED ever lit and no warning shown.

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
- [ ] AC8 (US-02, added 2026-10-16): When the LED test has failed **three times in a row on attempts
      that ran** (Shizuku or root held at that moment), the app says so where the button is — the
      Shizuku card — and the same fact turns on the standing band on the app list. One success clears
      the count and removes both. The full-screen notice is **not** triggered by a test (D-15).
      Guarded by `DeviceSupportTest` (rule, threshold and the two field sequences) and
      `LedTestWarningTest` (the card, the band, the privilege check, and that the takeover trigger
      stays the family rule's alone).

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
- **D-15 — The phone's own test is a second input to the band, never to the full screen
  (2026-10-16, owner request: "prévenir l'utilisateur quand le test LED échoue sur un téléphone
  Xiaomi, au lieu de le laisser réessayer 80 fois").** Three attempts that **ran** and failed are
  conclusive: the service Aura writes to is not there. They turn on the standing band and they put a
  sentence in the Shizuku card, next to the button that produced them. They do not take the screen
  over — the user is already inside the app, looking at the card that just answered — and nothing is
  blocked, exactly as in D-14. An attempt that ran **without** the privilege is not counted at all:
  it never reached the service, and counting it would tell a working phone it is broken. The
  threshold is the data's: the one Xiaomi phone in the field that works produced a single isolated
  failure in eight attempts, while the phones that cannot work produced 80, 10 and 6 in a row.
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

## Verification status (v1.1)

- **AC8's surfaces were seen on the owner's POCO X8 Pro** (`BEQWMFQWGMMV8HKZ`) through a probe build
  with an **unregistered token** (0 occurrences of the real token in its `classes*.dex`, checked
  before installing, so the run could not reach the project), by seeding `ledTestFailuresInARow`:
  seeded at **3** → the band `Aura ne peut pas éclairer ce téléphone` on the app list, the misleading
  "no app lights up yet" empty state gone, and in Settings the sentence `Trois échecs d'affilée : le
  service d'éclairage qu'Aura pilote n'est pas disponible sur ce téléphone…` under the button
  (`captures/30-led-warning-applist.png`, `captures/31-led-warning-settings.png`); seeded at **2** →
  neither (the negative control, so the threshold is not just nominal).
- **The loop was closed on a phone that works**: Shizuku granted to the probe, `Tester la LED (rouge)`
  → the ring lit, the card switched to `Test réussi — l'anneau s'est allumé.`, `ledTestFailuresInARow`
  went **3 → 0** (read back from the prefs), the band disappeared and the empty state returned —
  correct, because the phone had just proven that it lights up (`captures/33-led-test-passed-settings.png`,
  `captures/34-led-warning-cleared.png`). The probe was uninstalled and `app/build.gradle.kts`
  restored afterwards; the owner's own install was not touched.
- **What that phone cannot show, and is therefore not claimed**: the failing sequence itself. Its ring
  works, so three failures *with* privilege cannot be produced on it — the arithmetic is unit-tested,
  the threshold is the field data's, and the two seeded runs above prove the surfaces and the reset.
  The "attempt without privilege" branch is unreachable by tapping as well (the button only exists
  with the privilege), so it is covered by the rule test alone.

## Risks & Open Questions

- **Assumption:** manufacturer/brand is enough to decide. A HyperOS port on a non-Xiaomi device could
  be wrongly flagged; the LED test remains the truth shown in Settings, and the notice is
  non-blocking, so the cost of a false positive is one paragraph, not a broken app.
- **Answered 2026-10-16 (was: a Xiaomi-family device *without* the ring gets no notice).** The
  installs appeared. Read per `device_model` in PostHog, exactly the insight this risk named:
  `merlin` (Redmi Note 9) ran the LED test **80 times, 80 failures, and never emitted a single LED**
  across 13 sessions, with Shizuku granted and autostart opened eight times; `23106RN0DA` 6 of 6
  failed; `2602BPC18G` failed once in eight while working, which is the fluke the rule has to spare.
  The blocker this risk described ("a failed LED test cannot be attributed to hardware while the
  privilege grant is a separate step") is closed by reading the privilege **at the moment of the
  attempt**: `MainViewModel.testLed` records the outcome only when Shizuku or root was held, so a
  failure that reached the service is the phone's own answer (D-15, AC8). Nothing was added to the
  analytics payload to do it — the Non-Goal below still holds, and the same data that found `merlin`
  reads the rule's effect afterwards.
- **Not measured:** whether the notice changes what a non-Xiaomi user does. Nothing is reported when
  it is shown or acknowledged (no new event, no new property), so its effect is read as the volume of
  `setup_led_test_run` from non-Xiaomi devices before and after it shipped, never per user.
- **Verification of AC1–AC3 is by reading the rendered page and the README**, not by an automated
  test: the repo has no HTML/README assertion suite and inventing one is out of scope.

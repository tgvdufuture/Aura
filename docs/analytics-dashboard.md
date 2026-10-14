# Analytics dashboard — PostHog setup guide

Companion to `docs/PRD-usage-analytics-and-updates.md` (Phase 4). Everything below is dashboard
configuration, not app code: the app sends the events listed in
`app/src/main/java/com/aura/led/analytics/AnalyticsEvents.kt`, plus the `$screen` events from
`Analytics.screen()` (`$screen_name` = `main`, `settings`).

The SDK also attaches its own context to every event, and the app filters it: `AnalyticsEventFilter`
is a fail-closed allow-list applied to the merged payload through
`PostHogConfig.propertiesSanitizer`. What survives is the app/OS version, the device model, the
locale, the session id and the two "no person profile" flags; `$timezone`, `$network_carrier`,
`$user_agent`, `$device_name`, the screen metrics and the internal `$sdk_debug_*` counters are
dropped. A dashboard insight that needs one of those keys needs the key added to that list first.

Project token (already injected into the release build from `local.properties`):
`phc_…` — EU region, ingest host `https://eu.i.posthog.com`.

> **The token and the host must be in the same region**, and nothing in the app will tell you when
they are not: `/batch/` answers `200 {"status":"Ok"}` for an unknown token too, so a green
`Flushed N events successfully` in logcat proves the request, never the storage. The check that
discriminates is `/decide/`, which returns **200 for a token this region knows and 401
`authentication_error` otherwise**:
>
> ```sh
> curl -s -o /dev/null -w '%{http_code}\n' -X POST https://eu.i.posthog.com/decide/?v=3 \
>   -H 'Content-Type: application/json' \
>   -d '{"token":"phc_…","distinct_id":"region-check"}'   # 200 = same region, 401 = wrong region
> ```
>
> A US token sent to `eu.i.posthog.com` (what shipped until 2026-10-14) gives 401 there and 200 on
> `us.i.posthog.com`: every event is silently dropped and the dashboard stays empty.

---

## 1. Project settings (once)

| Setting | Value | Why |
|---|---|---|
| Region | **EU** | Most users are in the EU; the app posts to `eu.i.posthog.com`. |
| IP anonymisation | **On** | Only country-level geo is kept. |
| Data retention | **12 months** | Enough to see year-over-year, bounded storage. |
| Person profiles | Not used | The SDK runs `PersonProfiles.NEVER` and the app never calls `identify()`. Do not create profiles. |
| Autocapture / session replay | **Off** | The app disables all automatic capture; nothing else should be enabled. |

## 2. Insight checklist

Each row answers one question from the PRD (§1.2 Goals). Create them as PostHog insights and put
them on a single dashboard named **Aura**.

### G1 — Is there a real user base?

| Insight | Type | Definition |
|---|---|---|
| Active installs | Trend, unique users | Event `app_opened`, daily / weekly / monthly, **unique** count |
| Stickiness | Formula | `unique(app_opened, 1 day) / unique(app_opened, 30 days)` — above ~20 % is sticky for a utility app |
| New vs updated | Trend, breakdown | `app_opened` broken down by `is_first_run` and `is_update` |

### G2 — Where does setup fail?

| Insight | Type | Definition |
|---|---|---|
| Setup funnel | Funnel | Steps in order: `setup_language_selected` → `setup_notification_access_granted` → `setup_privilege_granted` → `setup_led_test_run` (filters: `success = true` on the last step), window 7 days |
| Privilege path | Trend, breakdown | `setup_privilege_granted` broken down by `kind` (`shizuku` vs `root`) |
| LED test outcome | Trend | `setup_led_test_run` broken down by `success` — a rising `false` share is a hardware/SELinux regression signal |
| Robustness steps | Trend | `setup_autostart_opened`, `setup_battery_exemption_requested`, `setup_battery_exemption_granted` |
| Reload of the notification screen | Trend | `setup_notification_access_opened` — high volume means the listener keeps dropping (HyperOS) |

### G3 — What do users actually use?

| Insight | Type | Definition |
|---|---|---|
| Top apps enabled | Trend, breakdown | `app_rule_toggled` (`enabled = true`) broken down by `target_package`, top 10 |
| Sender rules vs app rules | Trend, breakdown | `sender_rule_created` vs `app_rule_toggled`, broken down by `kind` (`CONTACT`/`GROUP`) |
| Animations | Trend, breakdown | `animation_selected` broken down by `animation_id` |
| Custom colours | Trend, breakdown | `app_rule_color_changed` broken down by `is_custom` — how often the palette is not enough |
| Reminder adoption | Trend, breakdown | `reminder_toggled` (`enabled = true`), `reminder_interval_changed` |
| Lights actually driven | Trend, breakdown | `led_emitted` broken down by `reason` (`app`/`contact`/`group`) — the closest proxy for "the app works for me" |
| Theme and language | Trend, breakdown | `app_opened` broken down by `theme` and `locale` |
| Screens | Trend, breakdown | `$screen` — how many users ever open Settings after setup |

### G4 — Do they come back?

| Insight | Type | Definition |
|---|---|---|
| Retention | Retention | `app_opened`, weekly and daily, D1/D7/D30 |
| Version spread | Trend, breakdown | `app_opened` broken down by `aura_version` — the share still on each release |

### G5 — Would they pay?

| Insight | Type | Definition |
|---|---|---|
| Support intent | Trend, breakdown | `support_intent` broken down by `answer` — **a user may change their answer**, so read the latest value per `distinct_id`, not the event count |
| Users who acted on it | Trend | `support_clicked` where `channel = kofi` — answers are opinions, this is the users who actually opened the donation page |

### Guardrails

| Insight | Type | Definition |
|---|---|---|
| Opt-in rate | Formula | `unique(distinct_id)` where `consent_granted` was sent ÷ `unique(distinct_id)` where `app_opened` fired. **Below ~10 % and the sample is not representative — read shares, not absolute numbers.** |
| Consent revoked | Trend | `consent_declined` — a spike after a release means users are rejecting the privacy change |
| Update prompt conversion | Funnel | `update_prompt_shown` → `update_prompt_accepted` → `update_download_result` (`result = success`) → `update_install_launched` |
| Update adoptions | Trend | `app_opened` where `is_update = true`, over time |
| Device/OS spread | Trend, breakdown | `app_opened` broken down by `device_model` and `android_sdk` — confirms the X8 Pro Max / other models report in |

## 3. How to read it

1. **Wait for volume.** With ~1357 downloads and no prior telemetry, expect the dashboard to be
   thin (`consent_granted` only accumulates from v0.6.0 onward). Judge directions, not decimals.
2. **Read the setup funnel first.** If a large share stops at `setup_privilege_granted`, the
   Shizuku step is the product's real cost of entry — that is the strongest premium candidate
   (a guided/automated setup) and the strongest onboarding fix.
3. **Then retention.** D30 above ~25–30 % for a device-specific utility means it is part of
   people's lives; below that, the app behaves like a one-week experiment and a paid tier is
   unlikely to convert.
4. **Then `led_emitted` volume per active install.** If the base is small but each active install
   emits steadily, the app works and the problem is distribution, not the product.
5. **Version spread tells you whether the in-app updater works**: a rising share on the newest
   release within ~30 days means the in-app update path is doing its job; a flat line means the
   prompt or the install permission is failing and the funnel insight will show where.
6. **Only trust `support_intent` once the opt-in rate is above ~10 %** and read it as a share of
   consenting users, never as a share of all installs.

## 4. Sanity checks

- **Live events**: after installing v0.6.0 and granting consent, open **Activity → Live events**
  and use the app (open Settings, enable an app, change a colour). Events must appear within
  ~30 s (the SDK flushes every 30 s or every 20 events).
- **Consent off**: switch analytics off in Settings, use the app for a few minutes, and confirm
  the live stream stays silent.
- **No content ever**: search the live stream for `sender`, `title`, `text` or `matchKey` — there
  must be zero matches. `AnalyticsSanitizerTest` enforces this in code.
- Test event from the token validation: `verification_ping` with `distinct_id = setup-verification`
  (sent once from the CLI on 2026-10-14). Ignore or delete that person.

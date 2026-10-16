# Analytics dashboard — PostHog setup guide

Companion to `docs/PRD-usage-analytics-and-updates.md` (Phase 4). Everything below is dashboard
configuration, not app code: the app sends the events listed in
`app/src/main/java/com/aura/led/analytics/AnalyticsEvents.kt`, plus the `$screen` events from
`Analytics.screen()` (`$screen_name` = `main`, `settings`).

The SDK also attaches its own context to every event, and the app filters it: `AnalyticsEventFilter`
is a fail-closed allow-list applied to the merged payload through
`PostHogConfig.propertiesSanitizer`. What survives is the app/OS version, the device model and
manufacturer, the locale, the session id and the two "no person profile" flags; `$timezone`,
`$network_carrier`, `$user_agent`, `$device_name`, the screen metrics and the internal
`$sdk_debug_*` counters are dropped. A dashboard insight that needs one of those keys needs the key
added to that list first. The app's own per-event properties are the `P_*` constants in the same
catalogue, and they are the second allow-list (`AnalyticsSanitizer`) applied before that one.

> **`app_opened` is one per foreground session, and since 2026-10-16 it includes the session that
granted consent.** Until that fix the resume that opened the consent screen ran while analytics was
still off, spent the session's single event on nothing, and the whole first session of every install
reported no `app_opened` at all — measured on the first two days: 7 of 27 consenting users had
sessions with dozens of events and not one `app_opened`, so G1 (active installs) and G4 (retention)
undercounted by about a quarter. Data collected before that build is a floor, not a count.

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
| Privilege requested vs granted | Trend, breakdown | `setup_privilege_requested` broken down by `kind`, read beside `setup_privilege_granted` — the gap is users who opened the Shizuku prompt and never came back with a grant |
| LED test outcome | Trend | `setup_led_test_run` broken down by `success` — a rising `false` share is a hardware/SELinux regression signal. **Break it down by `device_model` before reading it as one**: on 2026-10-16 the failures were 80 % one Redmi Note 9 (`merlin`, 80 failures from a single user, and that phone never emitted), 6 another Redmi, 1 a third — all Xiaomi identities, none of them warned. **Since 2026-10-16 the app warns them itself**: three failed attempts that ran (privilege held) turn on the band and put a sentence in the Shizuku card, where the button is — PRD `device-compatibility`, D-15 — so this row now also shows how many installs reach that state, and a `success=false` series that stops growing within a day or two of the rule is the rule working. Nothing new is sent to read it: the property set is unchanged |
| Autostart actually working | Trend | `setup_autostart_granted` — deduced from a boot broadcast that reached the app on its own, once per install, because HyperOS's autostart switch cannot be read: this is the only evidence the switch is on |
| Robustness steps | Trend | `setup_autostart_opened`, `setup_battery_exemption_requested`, `setup_battery_exemption_granted` — read each "opened" against its "granted": a gap there is exactly where setup loses people |
| Reload of the notification screen | Trend | `setup_notification_access_opened` — high volume means the listener keeps dropping (HyperOS) |
| Listener bound over time | Trend, breakdown | `app_opened` broken down by `listener_connected` — while it is `false` Aura can light nothing at all, so a rising `false` share (not a one-off after a reboot) says HyperOS killed the listener again |

### G3 — What do users actually use?

| Insight | Type | Definition |
|---|---|---|
| Top apps enabled | Trend, breakdown | `app_rule_toggled` (`enabled = true`) broken down by `target_package`, top 10 |
| Sender rules vs app rules | Trend, breakdown | `sender_rule_created` vs `app_rule_toggled`, broken down by `kind` (`CONTACT`/`GROUP`) |
| Animations | Trend, breakdown | `animation_selected` broken down by `animation_id` |
| Custom colours | Trend, breakdown | `app_rule_color_changed` broken down by `is_custom` — how often the palette is not enough |
| Reminder adoption | Trend, breakdown | `reminder_toggled` (`enabled = true`), and `reminder_interval_changed` broken down by `interval_s` to read which interval people actually settle on |
| Lights actually driven | Trend, breakdown | `led_emitted` broken down by `reason` (`app`/`contact`/`group`) — the closest proxy for "the app works for me" |
| Theme and language | Trend, breakdown | `app_opened` broken down by `theme` and `locale` |
| Sender parsing | Trend, breakdown | `sender_parsing_toggled` (`enabled = true`) broken down by `target_package` — the per-app cost of contact/group colours, and the apps whose sender names the parser cannot read |
| Sender rules removed | Trend | `sender_rule_deleted` — a rule created and deleted is either a rule the parser got wrong or a test the user undid; the second is harmless, the first is a bug report nobody filed |
| Colour previews | Trend, breakdown | `app_rule_previewed` broken down by `is_custom` — how often the palette is tried before someone reaches for a custom colour |
| LED duration | Trend, breakdown | `led_duration_changed` broken down by `seconds` — a pile-up at the 30 s clamp means the limit is the wanted value, not the slider |
| System LED switch | Trend, breakdown | `system_led_toggled` broken down by `disabled` (`false` = Aura drives the LED again) — users who would rather it left the light alone |
| Background service | Trend, breakdown | `service_toggled` (`running = true`) — how many keep the always-on service, i.e. who accepts the battery trade-off once it is spelled out |
| Quiet hours edited | Trend | `reminder_quiet_hours_changed` — the reminder adopted and then tuned; edits right after `reminder_toggled` mean the default window is wrong |
| Theme changes | Trend, breakdown | `theme_changed` broken down by `mode` — the app follows the system by default, so every edit says the default is wrong |
| Screens | Trend, breakdown | `$screen` — how many users ever open Settings after setup |

### G4 — Do they come back?

| Insight | Type | Definition |
|---|---|---|
| Retention | Retention | `app_opened`, weekly and daily, D1/D7/D30 |
| Version spread | Trend, breakdown | `app_opened` broken down by `aura_version` — the share still on each release |

### G5 — Would they pay?

| Insight | Type | Definition |
|---|---|---|
| Support intent | Trend, breakdown | `support_intent` broken down by `answer` — **a user may change their answer**, so read the latest value per `distinct_id`, not the event count. The question card itself only exists in Settings, but the app-list banner leads straight to it, so an answer can come from either entry point — the series counts answers, not a screen's visitors |
| Users who acted on it | Trend, breakdown | `support_clicked` broken down by `source` — `invite` = the compact banner at the top of the app list (**the only placement that reaches a user who never opens Settings**), `card` = the support card's donate button, `answer` = the "make a donation" chip. Answers are opinions; this is the users who actually opened the donation page, and `source` is the only thing that says which placement earns that. A click on `answer` always comes with a `support_intent` in the same second — the chip records the answer and opens the page — while `invite` and `card` produce clicks with no answer behind them, so a rising share of those two is the app's real conversion path, not a change of opinion |
| Donations | **Not in PostHog** | Nothing in the app can see a completed donation: Ko-fi is a page the user leaves for. The only attribution that exists is the one the URL carries — `utm_source=aura_app`, `utm_campaign=donation` and `utm_content` = the same `source` as the event above — read back in Ko-fi's own dashboard **once the page's Google Analytics is connected**. Without that step the tags change nothing, and a donation can be tied neither to Aura nor to the placement that produced it. Read it against the row above: clicks are the app's side, this is the only side that shows money, and the gap between them is people who opened the page and did not give |
| Stars | Trend, breakdown | `star_clicked` broken down by `channel` (`github`) — the update dialog's "Star Aura on GitHub" button. GitHub counts a download whether or not anyone ever opens the repository, so a rising `update_download_result` with a flat `star_clicked` means the flow converts updates but never converts visitors |

### G6 — Do the updates actually land?

| Insight | Type | Definition |
|---|---|---|
| Update check health | Trend, breakdown | `update_check_result` broken down by `result` (`up_to_date` / `update_available`). A failed check is logged and deliberately not reported (PRD D-07), so a flat series with no `update_available` means the checks are failing, not that everyone is current |
| Background announcement | Trend | `update_notification_shown` — the notification the daily worker posts for users who never open the app |
| Notification tap rate | Trend | `update_notification_tapped` read against `update_notification_shown` — the only read on whether a quiet notification is noticed at all |
| Update prompt conversion | Funnel | `update_prompt_shown` **or** `update_notification_tapped` → `update_prompt_accepted` → `update_download_result` (`result = success`) → `update_install_launched`. Two possible first steps: tapping the update notification opens the dialog without an `update_prompt_shown`, and those users were previously dropped at step 1. Every step names the release in `latest_tag` (`vX.Y.Z`, the tag and not the version name), so the funnel also reads per release. **The last step had never fired as of 2026-10-16**, and the single accepted update failed at the download: until that day the event carried `result` and `latest_tag` alone, so the cause existed only in that phone's logcat (`AuraVM update download failed: …`) and could not be acted on. It now carries `reason` — read the row below to see what each value means |
| Postponed releases | Trend, breakdown | `update_prompt_postponed` broken down by `latest_tag` — "Later" and the close button both land here; a release postponed version after version is one people are avoiding |
| Download failures by release | Trend, breakdown | `update_download_result` (`result = error`) broken down by `latest_tag` **and by `reason`** — separates a broken asset from broken networks, which `result` alone cannot. `reason` is a fixed vocabulary decided in `DownloadFailure`, sent since 2026-10-16: `http` (the server answered with a status other than 200, so the asset or the URL is wrong), `too_small` (the transfer stopped half-way, or what arrived is not an APK), `unreadable` (not readable as an APK at all), `wrong_package` (an APK, but not Aura's), `not_newer` (Aura's own, at a version not newer than the installed one) and `network` (everything the downloader could not classify: timeouts, dropped connections, unreadable storage — fail-closed, never a guess). The exception's message stays on the device: it is free text, and a package name or a URL is exactly what the sanitizer cannot tell from a stack trace |
| Update adoptions | Trend | `app_opened` where `is_update = true`, over time |

### Guardrails

| Insight | Type | Definition |
|---|---|---|
| Opt-in rate | Formula | `unique(distinct_id)` where `consent_granted` was sent ÷ `unique(distinct_id)` where `app_opened` fired. **Below ~10 % and the sample is not representative — read shares, not absolute numbers.** |
| Consent revoked | Trend | `consent_declined` — a spike after a release means users are rejecting the privacy change |
| Device/OS spread | Trend, breakdown | `app_opened` broken down by `device_model` and `android_sdk` — confirms the X8 Pro Max / other models report in |
| Non-addressable installs | Trend, breakdown | `app_opened` broken down by `$device_manufacturer` — outside the Xiaomi family (`Xiaomi`/`Redmi`/`POCO`) the rear ring does not exist, so those installs can never light anything up |
| Which models the ring actually works on | Trend, breakdown | `led_emitted` broken down by `device_model`, plus `setup_led_test_run` where `success = true` — **the only evidence that justifies naming another model** in the README, the landing page or the in-app notice. A POCO F7 or a Redmi Turbo 4 Pro that emits is a working phone whatever the copy says; one that never emits is not, and no amount of community reports replaces this. Read it before widening the supported-device list (PRD `device-compatibility`, D-13) |

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
7. **Read the funnel on addressable devices only.** Aura drives `miui.lights`, a HyperOS service:
   on an Infinix, a Samsung, a OnePlus or an emulator, no LED command can ever succeed. Of the first
   six external installs of v0.6.0, five were exactly that — 12 LED tests, 12 failures, and the one
   "I would pay" answer came from a phone where the app cannot work. Split `app_opened` by
   `$device_manufacturer` before drawing any conclusion, and never read a
   `setup_led_test_run{success=false}` share as a hardware regression until the devices are Xiaomi.
8. **The Ring worked on which models is the question the copy is waiting on.** From v0.6.1 the
   compatibility notice fires only on that same non-Xiaomi slice, so the volume of
   `setup_led_test_run` from non-Xiaomi devices, before and after 2026-10-15, says whether the notice
   stopped those installs or the installs stopped coming at all. The notice itself sends no event (no
   new disclosure was added for it), and it never blocks anything. **The identity rule is a family
   rule, not a model rule**: read on 2026-10-16, each of the three non-Xiaomi users who ran
   an LED test failed every run of it (15 of 15) and none of the nine ever emitted anything, so the
   rule has no false negative in the data — but three Xiaomi-identity models failed too, one of them
   80 times from a single user who had Shizuku granted and never lit anything. A Xiaomi phone that
   fails its own LED test is a phone Aura cannot drive, and it used to get no notice at all — fixed
   on 2026-10-16: after three failed attempts **that ran**, it gets the band and a sentence in the
   Shizuku card (D-15 in `docs/PRD-device-compatibility.md`).
9. **Read G6 before believing any other number.** Everything else here describes whoever is on a
   recent version. If `update_check_result` is empty or `update_notification_shown` never fires, the
   installed base is frozen where it was and the rest of the dashboard is a picture of the past.

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

# PRD — Autostart Detection

**Version:** 1.2
**Date:** 2026-10-14
**Status:** Draft — **premise not validated on the device** (see *Measurements*): the switch is not a reliable gate, so v1.2 reports what Aura observed instead of what the switch is set to. The owner decides whether to keep, reshape or drop the card.

## Summary

Tell an Aura user whether HyperOS will actually restart the app after a reboot, instead of asking them to trust a system screen they cannot verify.

## User Story

As an Aura user on HyperOS, I want Aura to tell me whether its autostart switch is really working, so that I know why the LED stopped after a reboot instead of guessing.

## Acceptance Criteria

- [ ] AC1: the setting is never read directly — no hidden-API bypass, no vendor provider, no new dependency; the state is deduced from the effect the switch has (whether `BOOT_COMPLETED` was delivered to Aura).
- [ ] AC2: three states exist and are shown in Settings — `ENABLED` (the boot broadcast reached Aura **without the user opening it**, in the process that received it), `BLOCKED` (the phone rebooted and no such delivery arrived), `UNKNOWN` (no reboot observed yet since install). A delivery that arrived after the app was already open in that process, or that was carried by the launch it landed in, proves nothing and must not move the state to `ENABLED`.
- [ ] AC7 (v1.2): the copy reports the **observation**, never the switch's value. Six device boots showed the switch is not deterministic — the card would otherwise state something the measurements contradict.
- [ ] AC3: the deduction rule is pure Kotlin and unit-tested, including two boot instants that drift, a missing history, and a clock that moved backwards.
- [ ] AC4: `BLOCKED` counts as something worth a look on the Settings gear dot; `ENABLED` and `UNKNOWN` do not.
- [ ] AC5: a detected `ENABLED` reports the setup step once per install (`setup_autostart_granted`), like the battery exemption.
- [ ] AC6: the Settings card keeps its button to HyperOS's autostart manager, and stops claiming the LED will break once the state is known to be enabled.

## Non-Goals

- Reading MIUI/HyperOS's autostart value directly (blocked: signature-level provider, hidden API restricted since Android 9).
- Guaranteeing Aura survives every HyperOS killing behaviour: a force-stop also blocks the boot broadcast, so `BLOCKED` is a suspicion, not a court ruling.
- Watching any other power setting (battery exemption already has its own card).

## DO NOT CHANGE

- `BootReceiver`'s existing job: restarting `AuraForegroundService` after a boot.
- The setup funnel's other steps and their once-per-install flags.
- The autostart button's intent (`com.miui.permcenter.autostart.AutoStartManagementActivity`) and its fallback.

## Measurements (POCO X8 Pro, `BEQWMFQWGMMV8HKZ`, 2026-10-14)

Six boots, the switch read back from HyperOS's own screen each time (`N applications peut commencer en arrière-plan`), "self-start" meaning an Aura process and its foreground service up **without anyone opening the app**:

| boot | switch | state before | Aura came back on its own? |
|---|---|---|---|
| 19:34 | on | normal | **yes** (broadcast at the unlock) |
| 19:37 | off | normal | **no** — no process/service/broadcast 20 s in; the broadcast arrived only with the next manual launch |
| 19:57 | off | just installed | **no** (same, 38 s in) |
| 20:05 | off | normal | **yes** — broadcast 5 s after boot, service up, nobody touched the phone |
| 20:09 | on | normal | **yes** |
| 20:11 | on | force-stopped | **yes** — HyperOS delivers `BOOT_COMPLETED` to a stopped app too |

**Verdict: the premise is not validated.** `off` gave no restart twice and a full restart once, with no user action in between, so the switch does not reliably decide whether the boot broadcast arrives. Consequences, all acted on:

- the states and the copy now describe **what Aura observed** (came back / did not come back), not the switch's value — the previous "Autostart works: Aura did restart" was written as a switch verdict and is false in the 20:05 boot, where the switch was off and Aura came back;
- a `BLOCKED` reading cannot be blamed on the switch either (it can also mean HyperOS staggered the broadcast, or a force-stop);
- what remains useful is the observation itself: the LED really is dead until the app is opened, and the card links to the switch as the first thing to try.

## Defects found by that measurement (all fixed, all pinned by tests)

1. **v1.0 read a delivered broadcast as proof.** With the switch off, the held broadcast was delivered once the app was opened by hand, and the card claimed the restart had worked.
2. **v1.1's "before the user opened it" test failed too**: the delivery lands **in the process the user's own launch creates, 23 ms before that activity's `onCreate`**, so the open is not yet recorded when the receiver runs (`isCarriedByTheLaunch` now undoes it).
3. **Comparing boot instants to spot "the same boot" was wrong**: a delivery was rejected as "after the user opened the app" because the *previous* boot was 3.5 min earlier and inside the 5 min tolerance, while Aura had in fact just started itself. That guard is now process-local (`isLateDelivery`), with no boot identity and no tolerance.

## Risks & Open Questions

- A force-stopped app does not receive boot broadcasts on stock Android, so a `BLOCKED` reading can be wrong; HyperOS delivered one anyway on this device, so the assumption does not even hold here. The copy stays a statement of what happened.
- A user who unlocks and opens Aura within 5 s of HyperOS delivering the boot broadcast would read `UNKNOWN` on a working switch (`LAUNCH_WINDOW_MS`). Deliberate: a missed "works" costs a card that stays honest, a wrong "works" costs a dead LED the user was told to expect.
- Clock drift still bounds `SAME_BOOT_TOLERANCE_MS`: a boot instant computed before an NTP correction of more than 5 min would look like a reboot. Rare (both readings usually happen after the clock settles) and the consequence is a *suspicion*, not a failure claim.
- **Owner decision, not taken yet**: keep the card as an observation, narrow it to "Aura came back after the last reboot" without mentioning the switch at all, or drop it and leave the manual hyperlink.

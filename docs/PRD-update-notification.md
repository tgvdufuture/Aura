# PRD — Update Available Notification

**Version:** 1.0
**Date:** 2026-10-14
**Status:** Implemented and verified on the owner's device (2026-10-14); see `PROGRESS.md` for the evidence and the one gap (no automated test for the notification itself).

## Summary

Tell an Aura user that a newer release exists even when they have not opened the app for days, and let them start the update with one tap from the notification.

## User Story

As an Aura user, I want to be told on my phone that a new version is out even if I have not opened the app, so that I can install it with one tap instead of never noticing it exists.

## Acceptance Criteria

- [ ] AC1: a unique daily WorkManager job (`UpdateWorker`, network constraint) asks the GitHub Releases API at most once a day while the app stays closed.
- [ ] AC2: when that check finds a newer version, a notification is posted on a dedicated `aura_updates` channel, quiet — no heads-up banner, no sound.
- [ ] AC3: tapping the notification opens Aura directly on the existing update dialog (release notes, size, "Update" → in-app download with progress → system installer).
- [ ] AC4: a given version is notified once; a version the user already dismissed inside the app never notifies.
- [ ] AC5: with "Check for new versions" off, no job is scheduled and no notification appears; turning it back on reschedules the job.
- [ ] AC6: the notification is removed once the install is launched, and once a later check finds the app up to date.

## Non-Goals

- Downloading the APK in the background straight from the notification: the tap opens the update screen, the download stays in the app.
- Silent or automatic updates without the user's confirmation.
- Notifying about anything but versions (news, reminders, tips).
- Checking more than once a day, or against another repository.

## DO NOT CHANGE

- The existing update path: `UpdateChecker`, `ApkDownloader`, `ApkInstaller`, `UpdateDialog`, and the consent screen.
- `UpdatePolicy.shouldCheck` / `decide` (the one-request-a-day budget) and the `updateCheckEnabled` preference.
- The LED pipeline and the persistent foreground service.
- Consent: the background worker never enables statistics and reports nothing when the user declined.

## Risks & Open Questions

- `androidx.work:work-runtime-ktx` becomes the project's first WorkManager dependency (accepted by the owner on 2026-10-14).
- One network request a day without interaction: already the case when the app is opened; assumed acceptable.
- Android 13+ without the notifications permission: the check still runs and the dialog stays available in the app, only the notification is missing.
- HyperOS may delay WorkManager jobs in deep sleep; the daily check can slide by hours. Accepted.

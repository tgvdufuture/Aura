# Aura 0.6.4

Updating now hands the download to the browser, and Aura stops asking for the install permission.

## If you are on 0.6.3

- Aura no longer installs its own updates. The dialog has one button, Download, and it opens the release page: your browser downloads the APK and Android asks you to confirm. The progress bar and the "install unknown apps" detour are gone with it.
- Aura no longer asks for the install-apps permission, the one Play Protect reads as "this app can install other apps". Play Protect still decides on its own about an app it has never seen, so it may still block Aura: this removes a reason to be suspicious, not the check.

## What changed

- The update dialog, the notification and these notes work as before. Only the last step moved out of the app.
- If the release page cannot be opened, Aura says so instead of leaving a button that does nothing.

## Unchanged

- No new permission, no new screen, no new setting, no new data. Rules, colours, language and preferences are untouched.
- Same signing key as 0.6.3: installing over the top keeps your settings.
- Anonymous statistics stay off until you agree.

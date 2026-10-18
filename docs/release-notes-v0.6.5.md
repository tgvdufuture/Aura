# Aura 0.6.5

A release about what Aura reports, not about what it does: how notifications drive the LED is untouched.

## What changed

- "Notification access was never granted" and "the listener is not connected right now" are now two separate answers. One number used to mean both, and an app still starting up looked broken.
- A listener that connects or drops is reported as it happens, even with the app in the background — the failure that silently leaves a phone with no light at all.
- The light-duration and reminder sliders report the value you settle on, not every step of the drag.

## Unchanged

- No new permission, no new screen, no new setting, no change to your rules or colours.
- The privacy screen names one more field it already used: whether the notification listener stayed connected. Statistics stay off until you agree to them.
- Same signing key as 0.6.3 and 0.6.4: installing over the top keeps your settings.

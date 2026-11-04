# Aura 0.8.0

This release adds the two light controls the ring was missing, and makes Settings easier to scan.

## Dim the ring, and fade it in

- Brightness from 10% to 100%, applied to Aura's own colours. It leaves HyperOS's brightness for charging and call scenes untouched.
- Optional fade in and out over 0.5 s for steady notifications. Animations keep their own pattern.
- Both controls apply immediately to previews, app rules and animations.
- A cancelled fade clears its own light strip, so it cannot leave a colour behind.

## Settings, rewritten for scanning

- Language and theme now share one Appearance card.
- Lighting has its own card: duration, brightness and fade.
- Reliability keeps the service, notification listener, autostart and battery rows, each with one short status instead of a paragraph.
- The HyperOS scope, the privacy promise and the LED troubleshooting steps are still on screen.

## Support and privacy, in more detail

- The support question now lists what donations fund and what a paid version could add. Nothing is for sale yet.
- Anonymous statistics now show what is sent, what is never sent, and where it goes: PostHog Cloud in the EU, kept for 12 months.

## Unchanged

- No new permission. The signing key is the same as 0.7.0, so installing over the top keeps your settings.
- Charging and call light effects remain separate HyperOS scenes under the system.

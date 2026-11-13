# Aura 0.8.3

Three fixes from your reports.

## The reminder that flashed once

- With Always-On Display, a locked phone counts as awake, so every repeat was skipped: the ring flashed once and stayed dark while the notification was unread.
- The repeat now uses the first flash's rule — screen on and unlocked — so a locked phone flashes again.
- Nothing to reconfigure: your settings now do what they say.

## The app that would not open

- On a ROM that refuses Aura's background notification, the refusal could take the whole app down and the system kept restarting it, so Aura would not open until it was uninstalled; it now runs without that notification instead of crashing.

## Root and battery

- A root grant accepted a moment after launch is now seen by "Test the LED", instead of being remembered as "no root" until the next launch.
- When HyperOS drops the notification listener, Aura asked for it back every two seconds with no limit. The retry now backs off from two seconds to five minutes and resets once the listener is back, so one isolated drop still recovers at once.

## Unchanged

- No new permission, and the same signing key as 0.8.2: installing over the top keeps your settings and rules.
- Charging and call light effects remain separate HyperOS scenes.

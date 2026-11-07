# Aura 0.8.1

This release fixes the breathing light that cut out mid-breath, gives each app its own lighting mode, and makes quiet hours cover every light Aura drives.

## A breath that no longer cuts

- The old breath re-armed the camera light's auto-off deadline on every frame, and a reset frame could extinguish the ring. The breath now claims the strip once, then redraws it through the timer-free path.
- The trough keeps 5% of the colour, so the ring fades instead of blinking off; brightness still applies.
- A cancelled breath clears its last frame, so the next notification starts clean.

## A lighting mode per app

- Each app rule now has its own mode: static, breathing, flashing, rainbow or alert.
- Contact and group rules still take priority over the app's mode.
- Existing rules keep working: the database gains the column through a one-step migration.

## Quiet hours cover every light

- A new "Silence all lights" switch extends the quiet window to the arrival light and the fallback light, not only the reminder loop.
- It is on by default. Turn it off to keep reminders quiet while new notifications still light the ring.
- Both the arrival flash and the removal fallback check the window before emitting.

## Not in this release

- A running or marquee effect is not here. HyperOS exposes one ARGB colour for the whole ring, with no per-LED or per-segment address, so segments cannot be lit independently through the current API.

## Unchanged

- No new permission. The signing key is the same as 0.8.0, so installing over the top keeps settings and rules.
- Charging and call light effects remain separate HyperOS scenes.

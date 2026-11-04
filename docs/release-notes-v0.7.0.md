# Aura 0.7.0

A field report from a POCO X8 Pro on HyperOS 3.0.303 showed three things Aura said that were not true. This release makes the test ask instead of assume, gives the reminder an end, and stops a "blink" from turning into a steady light.

## The LED test asks what you see

- **"Test the LED" used to report success as soon as HyperOS accepted the command.** With the phone's back light effects off, the command is accepted and the ring stays dark — the card still said it had lit up. It now asks "Did the ring light up?" and your answer is the result: Yes resets the failure count, No adds to it.
- **Three dark answers still explain an unsupported phone**, with one exception: while HyperOS's notification light is off, the system may simply be refusing to light the hardware, so a dark ring is not counted as the phone's verdict. The card names that cause and where to turn the back light effects back on.

## The HyperOS card stops promising more than it does

- Renamed to **HyperOS notification light**, and its switch now says what it does: **turn off the HyperOS notification pulse**.
- The card states the limit: only the notification pulse is switched off. **Charging and call light effects are separate HyperOS scenes**, and Aura cannot disable them. The README and the site's FAQ say the same.

## Reminder: a duration, and a blink that goes dark

- New **reminder duration**: until dismissed (the default), 5, 15, 30 or 60 minutes. The loop stops once that lifetime, counted from its first flash, is over.
- **The ring now goes dark between flashes.** A 5 s reminder with a 10 s light duration used to keep the LED lit across ticks; the next flash cannot start before the light's own duration plus a short off gap. Settings says so when the chosen interval is shorter than that.
- The reported example — a flash every 5 s, 3 s on — now blinks as expected.

## Not in this release

- Fade in/out and brightness control are **not** here yet. Both change how the ring itself is driven and must be tested on the phone before the app offers them.

## Unchanged

- No new permission, no new screen. Rules, colours, language and every other preference are untouched.
- Same signing key as 0.6.5: installing over the top keeps your settings.
- Anonymous statistics stay off until you agree. If you agreed, the reminder lifetime you pick is reported as one of the reminder settings the privacy screen already describes.

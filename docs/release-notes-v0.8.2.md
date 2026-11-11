# Aura 0.8.2

This release fixes the ring lighting up with an old colour after you dismiss a notification.

## The colour that came back

- With two notifications waiting, dismissing the newest one re-lit the ring for the other app with the colour that app had when its own notification arrived. Change an app's colour in between and you got the old one back.
- The ring now re-reads that notification before lighting it, so it shows what your rules give that app right now.
- If nothing else is left in the shade, the ring goes dark instead of replaying a colour at all.
- A notification now resolves the same way when it arrives and when it comes back, so the two cannot disagree.

## Unchanged

- No new permission, and the signing key is the same as 0.8.1, so installing over the top keeps settings and rules.
- Charging and call light effects remain separate HyperOS scenes.

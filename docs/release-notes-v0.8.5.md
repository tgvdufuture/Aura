# Aura 0.8.5

One fix, for the phones that updated to HyperOS 4.

## The ring that blinked and went dark

- HyperOS 4 (Android 17) no longer lets Aura recolour a ring that is already lit: the transaction the fade and the breath were built on is now limited to the music-rhythm strip, and the one that still drives the ring drops every update sent while the ring is active. Both effects drew their first frames, then the ring sat dark for the whole duration.
- On such a version Aura now lights the colour itself and holds it: with Fade on, the ring stays lit for the full duration instead of blinking out. No animation, but a light.
- HyperOS 3 (Android 16) is untouched: same fade, same breath, same transactions as 0.8.4.
- Nothing to reconfigure: no new permission, and the same signing key as 0.8.4.

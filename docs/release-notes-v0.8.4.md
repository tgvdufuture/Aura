# Aura 0.8.4

One fix, from the log sent with issue #5.

## The app that crashed on every start

- On a ROM without the `miui.lights` service, a failed root command threw out of the LED controller instead of being reported as a failure. The preview's stop step runs at launch, so Aura died and the system restarted it, in a loop.
- The failure now comes back the way the Shizuku path already did. On such a ROM Aura opens and stays open; it simply has no light to drive.
- Nothing to reconfigure: no new permission, and the same signing key as 0.8.3.

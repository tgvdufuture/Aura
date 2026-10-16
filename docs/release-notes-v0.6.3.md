# Aura 0.6.3

A phone that cannot light up now says so, and the support ask reaches the app list.

## If you are on 0.6.2

- **The LED test answers.** It used to do nothing you could see. It now says the test passed when the ring lit, asks you to check Shizuku if it failed for another reason, and after **three failures in a row** it says the real thing: this phone's system has no light service for Aura to drive, so the ring cannot light up however many times you try. The same fact raises the standing notice on the app list, where the "no app lights up yet" line — a promise nothing there could keep — is then gone.
- **The support ask is now on the app list**, above your apps: why Aura stays free, a button that opens the question ("how would you rather support Aura?"), its Ko-fi link kept quiet beside it, and Later to dismiss it for the session. It appears only while anonymous statistics are on — an answer that cannot be recorded is not worth asking for — and it never comes back once answered.
- **A failed update now tells us why** — timeout, a 404, a truncated file, a package that is not Aura. Nothing changes on screen: the dialog already offered the releases page. It is so the next failure can be fixed instead of guessed at.

## What changed

- The support question is one tap from the app list, instead of living only in Settings — where the people it was written for never went.
- The donation link names the placement that opened it, so a donation can be attributed to Aura at all.
- The compatibility notice no longer waits for a model name: your own test result is enough to raise it.

## Unchanged

- No new permission, no new screen, no new setting, no new data. Rules, colours, language and preferences are untouched.
- Same signing key as 0.6.2: the update installs in place.
- Anonymous statistics stay off until you agree.

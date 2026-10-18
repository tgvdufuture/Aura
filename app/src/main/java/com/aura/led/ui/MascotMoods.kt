package com.aura.led.ui

import androidx.annotation.DrawableRes
import com.aura.led.R

/**
 * The owner's mascot, in the three faces they drew. The values are named after the files rather than
 * after the feelings, so the code and the artwork can be checked against each other.
 *
 * The drawables are exports of `docs/mascotte/{joy,oh,what}.png`: **all three cropped to the same
 * box** `[188,156]-[1132,1100]` (the union of their ink, squared) and scaled to 512². Cropping each
 * file to its own ink would render the same head at three different sizes, because their ink boxes
 * differ by up to 130 px.
 */
enum class MascotMood(@DrawableRes val art: Int) {
    /** `joy.png` — laughing: the good news pose (a version is ready, it is downloading). */
    JOY(R.drawable.mascot_joy),

    /** `oh.png` — disappointed: something failed and the mascot is not pretending otherwise. */
    OH(R.drawable.mascot_oh),

    /** `what.png` — puzzled, question mark: the app needs something from the user. */
    WHAT(R.drawable.mascot_what),
}

/**
 * Which face the update dialog wears for each of its states. Kept apart from the composable because
 * a wrong mapping here is invisible to a compiler: an error dialog wearing the laughing face still
 * compiles, still looks like a mascot, and lies about what happened.
 *
 * Only good news is left: the dialog has a single state since the update action became "open the
 * release page", so the puzzled and disappointed faces have no state to show (`what.png` and
 * `oh.png` stay in the drawables for the next screen that needs them).
 */
internal fun mascotFor(phase: MainViewModel.UpdatePhase): MascotMood = when (phase) {
    MainViewModel.UpdatePhase.AVAILABLE -> MascotMood.JOY
    MainViewModel.UpdatePhase.HIDDEN -> MascotMood.JOY
}

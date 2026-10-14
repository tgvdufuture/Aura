package com.aura.led.ui

/**
 * What Settings still has to offer, so the gear can say so without being opened.
 *
 * Two levels only, because a dot that says everything says nothing: [AttentionLevel.IMPORTANT] is
 * something the app cannot do its job without (drawn in red), [AttentionLevel.INFO] is something
 * worth a look while nothing is broken (drawn in grey). What belongs on which side is a judgement,
 * so the reasoning is written down per input below — moving one between the two lists here is the
 * whole change.
 */
enum class AttentionLevel {
    IMPORTANT,
    INFO,
}

/** The states the dot is computed from, read from the ViewModel. */
data class AttentionInputs(
    /** The notification listener is bound: with it off, nothing lights up at all. */
    val notificationAccess: Boolean,
    /** Shizuku granted or root available: without it the notification LED cannot be written. */
    val privilege: Boolean,
    /** HyperOS may kill Aura in the background without the exemption, but the app still works. */
    val batteryExempt: Boolean,
    /** Whether statistics are on: the support card, and so its question, only exists when they are. */
    val analyticsGranted: Boolean,
    /** The one-time support question, `null` while unanswered: a question, not a defect. */
    val supportAnswer: String?,
)

data class SettingsAttention(val important: Int = 0, val info: Int = 0) {
    val total: Int get() = important + info

    /** The colour the dot takes: the worst thing found, or nothing to draw at all. */
    val level: AttentionLevel? get() = when {
        important > 0 -> AttentionLevel.IMPORTANT
        info > 0 -> AttentionLevel.INFO
        else -> null
    }
}

fun settingsAttention(inputs: AttentionInputs) = SettingsAttention(
    important = missing(inputs.notificationAccess, inputs.privilege),
    info = missing(inputs.batteryExempt) +
        // Only a question the app will actually ask: the support card is hidden while statistics are
        // off, because an answer that cannot be recorded is not worth asking for. Counting it there
        // would leave a dot pointing at a card that does not exist, for good.
         if (inputs.analyticsGranted && inputs.supportAnswer == null) 1 else 0,
)

private fun missing(vararg ok: Boolean) = ok.count { !it }

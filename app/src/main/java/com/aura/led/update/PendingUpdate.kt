package com.aura.led.update

/**
 * A release the app already discovered but did not install, kept between processes.
 *
 * The background check runs while the app is closed, so the tap on its notification arrives with
 * nothing in memory: the update screen is rebuilt from this record instead of asking GitHub a
 * second time, which would fail exactly when the user is looking at an empty screen.
 *
 * Stored as separate preference entries rather than one packed string: the release notes are free
 * text that can contain any separator a packed format would have to escape.
 */
data class PendingUpdate(
    val tag: String,
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
) {

    /**
     * Whether this record is complete enough to be shown and downloaded.
     *
     * One process writes it, another reads it, and preferences can be missing entries: a short or
     * foreign record must not reach the installer. The download is https or it does not happen —
     * the release asset is the only file this screen is allowed to fetch.
     */
    fun isUsable(): Boolean =
        tag.isNotBlank() && versionName.isNotBlank() && apkUrl.startsWith("https://")

    companion object {
        /** The part of a check result worth remembering. */
        fun of(result: CheckResult.UpdateAvailable): PendingUpdate = PendingUpdate(
            tag = result.tag,
            versionName = result.versionName,
            apkUrl = result.apkUrl,
            sizeBytes = result.sizeBytes,
            notes = result.notes,
        )
    }
}

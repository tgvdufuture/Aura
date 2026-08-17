package com.aura.led.notification

/**
 * Pure sender extraction from notification title/text, independent of Android so it
 * can be unit-tested. Resolves, most to least specific:
 *  1. Generic group-style chats ("Sender: message" with a non-empty title) — covers
 *     WhatsApp, Telegram and any app using that format, so sender detection also
 *     works for apps Aura doesn't know about.
 *  2. App-specific formats (Snapchat / Instagram) where the sender is not the title,
 *     in English and French.
 *  3. Default: the title is the sender/contact name (SMS and most apps).
 */
object SenderParser {

    data class Result(val senderName: String?, val groupName: String?)

    private const val SNAPCHAT = "com.snapchat.android"
    private const val INSTAGRAM = "com.instagram.android"

    /** Snapchat group snaps: "Friend sent you a snap/chat/video/photo" (EN/FR). */
    private val SNAP_ACTION = Regex(
        "^(.+?) (?:sent (?:you )?(?:a snap|a chat|a video|a photo)|vous a envoyé (?:un snap|un chat|une vidéo|une photo))\\.?$",
        RegexOption.IGNORE_CASE,
    )

    /** Instagram group DMs: "Friend sent you a message/photo/video/reel" or mentions (EN/FR). */
    private val INSTA_ACTION = Regex(
        "^(.+?) (?:sent you (?:a message|a photo|a video|a reel)|mentioned you in .+" +
            "|vous a envoyé (?:un message|une photo|une vidéo|un reel)" +
            "|vous a mentionné(?:e)?(?: dans .+)?)\\.?$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(appPkg: String, title: String, text: String): Result {
        // 1) Group-style chat.
        if (title.isNotEmpty()) {
            val idx = text.indexOf(": ")
            if (idx in 1..40) {
                return Result(senderName = text.substring(0, idx).trim(), groupName = title)
            }
        }

        // 2) App-specific.
        when (appPkg) {
            SNAPCHAT -> parseSnapchat(title, text)?.let { return it }
            INSTAGRAM -> parseInstagram(title, text)?.let { return it }
        }

        // 3) Default.
        return Result(senderName = title.ifEmpty { null }, groupName = null)
    }

    private fun parseSnapchat(title: String, text: String): Result? {
        val match = SNAP_ACTION.find(text) ?: return null
        val sender = match.groupValues[1].trim()
        if (sender.isEmpty()) return null
        val group = title.takeIf { it.isNotEmpty() && !it.equals(sender, ignoreCase = true) }
        return Result(senderName = sender, groupName = group)
    }

    private fun parseInstagram(title: String, text: String): Result? {
        val match = INSTA_ACTION.find(text) ?: return null
        val sender = match.groupValues[1].trim()
        if (sender.isEmpty()) return null
        val group = title.takeIf { it.isNotEmpty() && !it.equals(sender, ignoreCase = true) }
        return Result(senderName = sender, groupName = group)
    }
}

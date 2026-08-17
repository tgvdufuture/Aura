package com.aura.led.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderParserTest {

    private fun assertSender(
        pkg: String,
        title: String,
        text: String,
        expectedSender: String?,
        expectedGroup: String?,
    ) {
        val result = SenderParser.parse(pkg, title, text)
        assertEquals("sender for '$text'", expectedSender, result.senderName)
        assertEquals("group for '$text'", expectedGroup, result.groupName)
    }

    // --- Default (title = sender) ---

    @Test
    fun `uses title as sender by default`() {
        assertSender("com.android.mms", "Maman", "Coucou", "Maman", null)
    }

    @Test
    fun `returns null sender when title empty`() {
        assertSender("com.android.mms", "", "Coucou", null, null)
    }

    // --- Generic group-style chat ---

    @Test
    fun `extracts sender and group from Sender colon message`() {
        assertSender("com.whatsapp", "Famille", "Maman : Coucou", "Maman", "Famille")
    }

    // --- Snapchat ---

    @Test
    fun `snapchat english group`() {
        assertSender("com.snapchat.android", "Famille", "Marylou sent you a snap", "Marylou", "Famille")
    }

    @Test
    fun `snapchat french group chat`() {
        assertSender("com.snapchat.android", "Famille", "Marylou vous a envoyé un Chat.", "Marylou", "Famille")
    }

    @Test
    fun `snapchat french group snap`() {
        assertSender("com.snapchat.android", "Famille", "Marylou vous a envoyé un snap", "Marylou", "Famille")
    }

    @Test
    fun `snapchat one-to-one falls back to title`() {
        // Text has no sender, so the title is the sender (default path).
        assertSender("com.snapchat.android", "Marylou", "vous a envoyé un Chat.", "Marylou", null)
    }

    // --- Instagram ---

    @Test
    fun `instagram english message`() {
        assertSender("com.instagram.android", "Groupe", "Toto sent you a message", "Toto", "Groupe")
    }

    @Test
    fun `instagram french message`() {
        assertSender("com.instagram.android", "Groupe", "Toto vous a envoyé un message", "Toto", "Groupe")
    }

    @Test
    fun `instagram french reel`() {
        assertSender("com.instagram.android", "Groupe", "Toto vous a envoyé un reel", "Toto", "Groupe")
    }

    @Test
    fun `instagram french mention`() {
        assertSender("com.instagram.android", "Groupe", "Toto vous a mentionné dans sa story", "Toto", "Groupe")
    }

    @Test
    fun `instagram reaction falls back to title`() {
        assertSender("com.instagram.android", "Toto", "A réagi avec 😂 à votre message", "Toto", null)
    }

    // --- Unknown app ---

    @Test
    fun `unknown app keeps group colon parsing`() {
        assertSender("com.example.app", "Equipe", "Bob : hello", "Bob", "Equipe")
    }

    @Test
    fun `sender equal to title is not treated as a group`() {
        // A snap where the sender name equals the title should not yield a group name.
        val result = SenderParser.parse("com.snapchat.android", "Marylou", "Marylou vous a envoyé un snap")
        assertEquals("Marylou", result.senderName)
        assertNull(result.groupName)
    }
}

package com.aura.led.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullNotificationReaderTest {

    private fun dump(vararg records: String): String = records.joinToString("\n")

    private fun record(pkg: String, title: String? = null, text: String? = null): String = buildString {
        append("NotificationRecord(0x1: pkg=$pkg user=UserHandle{0} id=1 tag=null importance=3 ")
        append("key=0|$pkg|1|null|1000 bbbc=0: Notification())\n")
        if (title != null) append("    android.title=$title\n")
        if (text != null) append("    android.text=$text\n")
    }

    @Test
    fun `parses String wrapped title and text`() {
        val result = FullNotificationReader.parse(
            record("com.whatsapp", "String (Maman)", "String (Coucou !)"),
            "com.whatsapp",
        )
        assertEquals("Maman", result?.title)
        assertEquals("Coucou !", result?.text)
    }

    @Test
    fun `parses SpannableString wrapped content`() {
        val result = FullNotificationReader.parse(
            record("com.spotify.music", "SpannableString (TIME TRIAL)", "SpannableString (Ptite Soeur)"),
            "com.spotify.music",
        )
        assertEquals("TIME TRIAL", result?.title)
        assertEquals("Ptite Soeur", result?.text)
    }

    @Test
    fun `parses CharSequence with text metadata`() {
        val result = FullNotificationReader.parse(
            record("com.example.app", "CharSequence (className=android.text.SpannableString, text=Mom)"),
            "com.example.app",
        )
        assertEquals("Mom", result?.title)
    }

    @Test
    fun `keeps parentheses inside content`() {
        val result = FullNotificationReader.parse(
            record("com.example.app", "String (Appelle-moi (vite))"),
            "com.example.app",
        )
        assertEquals("Appelle-moi (vite)", result?.title)
    }

    @Test
    fun `parses plain unquoted value`() {
        val result = FullNotificationReader.parse(
            record("com.example.app", "Maman"),
            "com.example.app",
        )
        assertEquals("Maman", result?.title)
    }

    @Test
    fun `parses quoted value`() {
        val result = FullNotificationReader.parse(
            record("com.example.app", "\"Maman\""),
            "com.example.app",
        )
        assertEquals("Maman", result?.title)
    }

    @Test
    fun `returns null title and text for null values`() {
        val result = FullNotificationReader.parse(
            record("com.example.app", "null", "null"),
            "com.example.app",
        )
        assertEquals(null, result?.title)
        assertEquals(null, result?.text)
    }

    @Test
    fun `returns null when package not found`() {
        val result = FullNotificationReader.parse(
            record("com.whatsapp", "String (Maman)"),
            "com.instagram.android",
        )
        assertNull(result)
    }

    @Test
    fun `returns the last matching record`() {
        val result = FullNotificationReader.parse(
            dump(
                record("com.whatsapp", "String (Maman)", "String (premier)"),
                record("com.other.app", "String (Autre)"),
                record("com.whatsapp", "String (Papa)", "String (second)"),
            ),
            "com.whatsapp",
        )
        assertEquals("Papa", result?.title)
        assertEquals("second", result?.text)
    }

    @Test
    fun `trims leading whitespace on keys`() {
        val result = FullNotificationReader.parse(
            "NotificationRecord(0x1: pkg=com.whatsapp user=UserHandle{0} id=1)\n" +
                "                android.title=String (Maman)\n",
            "com.whatsapp",
        )
        assertEquals("Maman", result?.title)
    }
}

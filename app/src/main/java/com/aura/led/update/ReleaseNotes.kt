package com.aura.led.update

/**
 * Turns a GitHub release body into something a dialog can show.
 *
 * The body arrives as markdown written for a web page: `##` headings, `*` bullets, `**bold**`,
 * `[links](url)`, horizontal rules and a trailing "Full Changelog" link. Rendered raw, every one of
 * those markers is visible text in the middle of a sentence, which is exactly what the update popup
 * looked like before. This keeps the words, drops the syntax, and caps the length so a long release
 * body cannot push the dialog's buttons off the screen.
 *
 * Pure and dependency-free, so the rules below are pinned by unit tests instead of by eye.
 */
object ReleaseNotes {

    enum class Kind { HEADING, BULLET, PARAGRAPH }

    data class Note(val kind: Kind, val text: String)

    /** A release body longer than this is truncated: the dialog is a notification, not a changelog page. */
    const val MAX_NOTES = 24

    fun parse(markdown: String, limit: Int = MAX_NOTES): List<Note> =
        markdown.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { isRule(it) }
            .filterNot { isFullChangelog(it) }
            .map { toNote(it) }
            .filter { it.text.isNotEmpty() }
            .take(limit)
            .toList()

    private fun isRule(line: String): Boolean =
        line.length >= 3 && line.all { it == '-' || it == '*' || it == '_' }

    /** "**Full Changelog**: <url>" — a link to the page the update dialog already comes from. */
    private fun isFullChangelog(line: String): Boolean =
        line.lowercase().replace("*", "").startsWith("full changelog")

    private fun toNote(line: String): Note = when {
        line.startsWith("#") ->
            Note(Kind.HEADING, inline(line.trimStart('#')))

        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") ->
            Note(Kind.BULLET, inline(line.drop(2)))

        else -> Note(Kind.PARAGRAPH, inline(line))
    }

    /** Removes the inline syntax, keeping the words: emphasis, code spans, links, HTML comments. */
    private fun inline(text: String): String =
        text.replace(Regex("<!--.*?-->"), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "\$1")
            .replace(Regex("(\\*\\*|__|`|~~)"), "")
            .trim()
}

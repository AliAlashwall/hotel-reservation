package com.alialashwal.hotelreservation.core.network.internal

/**
 * Turns the HTML blob the catalogue returns for a description into plain text.
 *
 * The API sends real markup: `<p><strong>...</strong><br>...</p>`. Rendering that raw
 * shows tags to the user, and pulling in an HTML parser for four tags is not worth a
 * dependency. Block boundaries become blank lines so paragraphs survive; everything
 * else is dropped.
 */
internal fun String.htmlToPlainText(): String = this
    .replace(BLOCK_BREAK, "\n\n")
    .replace(LINE_BREAK, "\n")
    .replace(ANY_TAG, "")
    .let(::decodeEntities)
    .lines()
    .joinToString(separator = "\n") { it.trim() }
    .replace(EXCESS_BLANK_LINES, "\n\n")
    .trim()

private val BLOCK_BREAK = Regex("</(p|div|li|h[1-6])\\s*>", RegexOption.IGNORE_CASE)
private val LINE_BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]*>")
private val EXCESS_BLANK_LINES = Regex("\n{3,}")

private fun decodeEntities(text: String): String = ENTITIES.entries
    .fold(text) { acc, (entity, char) -> acc.replace(entity, char) }
    .replace(NUMERIC_ENTITY) { match ->
        match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }

private val NUMERIC_ENTITY = Regex("&#(\\d+);")

private val ENTITIES = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
    "&nbsp;" to " ",
    "&hellip;" to "…",
    "&mdash;" to "—",
    "&ndash;" to "–",
    "&rsquo;" to "’",
    "&lsquo;" to "‘",
)

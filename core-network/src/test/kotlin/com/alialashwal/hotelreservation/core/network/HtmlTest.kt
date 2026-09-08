package com.alialashwal.hotelreservation.core.network

import com.alialashwal.hotelreservation.core.network.internal.htmlToPlainText
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTest {

    @Test
    fun `paragraph boundaries become blank lines`() {
        assertEquals(
            "First para.\n\nSecond para.",
            "<p>First para.</p><p>Second para.</p>".htmlToPlainText(),
        )
    }

    @Test
    fun `a line break becomes a single newline`() {
        assertEquals("Title\nBody", "<strong>Title</strong><br>Body".htmlToPlainText())
    }

    @Test
    fun `entities are decoded, including the ampersand the API sends escaped`() {
        // Real text from the catalogue: "Cairo Marriott Hotel &amp; Omar Khayyam Casino".
        assertEquals(
            "Marriott & Omar Khayyam \"Casino\" – it’s open",
            "Marriott &amp; Omar Khayyam &quot;Casino&quot; &ndash; it&rsquo;s open".htmlToPlainText(),
        )
    }

    @Test
    fun `numeric entities are decoded`() {
        assertEquals("A-B", "A&#45;B".htmlToPlainText())
    }

    @Test
    fun `runs of empty blocks collapse instead of leaving a gap`() {
        assertEquals("A\n\nB", "<p>A</p><p></p><p></p><p>B</p>".htmlToPlainText())
    }

    @Test
    fun `text with no markup is returned unchanged apart from trimming`() {
        assertEquals("Plain description.", "  Plain description.  ".htmlToPlainText())
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", "".htmlToPlainText())
        assertEquals("", "<p></p>".htmlToPlainText())
    }
}

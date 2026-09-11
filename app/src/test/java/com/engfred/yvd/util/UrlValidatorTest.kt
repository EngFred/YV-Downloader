package com.engfred.yvd.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `converts a mixed watch URL into a canonical playlist URL`() {
        assertEquals(
            "https://www.youtube.com/playlist?list=RDZ5jm1VbXw4k",
            UrlValidator.toCanonicalPlaylistUrl(
                "https://www.youtube.com/watch?v=Z5jm1VbXw4k&list=RDZ5jm1VbXw4k"
            )
        )
    }

    @Test
    fun `returns null when a URL has no playlist id`() {
        assertNull(UrlValidator.toCanonicalPlaylistUrl("https://youtu.be/Z5jm1VbXw4k"))
    }
}

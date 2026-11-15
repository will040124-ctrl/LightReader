package com.will.lightreader.source.net

import com.will.lightreader.source.SourceError
import org.junit.Assert.*
import org.junit.Test

class PageResponseErrorTest {
    private val url = "https://www.wenku8.net/modules/article/tags.php"
    private val blocked = """<html><head><title>Attention Required! | Cloudflare</title></head>
        <body><div id="cf-error-details">Sorry, you have been blocked.
        You are unable to access example.test</div></body></html>"""

    @Test fun `browser block page is not passed to the tag parser even without HTTP status`() {
        val failure = PageResponseError.detect(url, blocked) as SourceError.ChallengeRequired
        assertTrue(failure.accessBlocked)
        assertEquals(url, failure.url)
    }

    @Test fun `interactive challenge remains distinct from a blocked connection`() {
        val failure = PageResponseError.detect(url, "<title>Just a moment...</title><script src='/cf-chl/challenge.js'></script>")
            as SourceError.ChallengeRequired
        assertFalse(failure.accessBlocked)
    }

    @Test fun `ordinary content mentioning a security service remains readable`() {
        assertNull(PageResponseError.detect(url, "<title>Cloudflare 的故事</title><div id='content'>她说 Just a moment，这是一段小说。</div>"))
        assertNull(PageResponseError.detect(url, "<title>标签</title><ul class='ultops'><li>校园</li></ul>"))
    }

    @Test fun `main document HTTP failures preserve the appropriate recovery action`() {
        assertTrue(PageResponseError.detect(url, "", 401) is SourceError.AuthRequired)
        assertTrue(PageResponseError.detect(url, "", 403) is SourceError.ChallengeRequired)
        assertTrue(PageResponseError.detect(url, "", 429) is SourceError.RateLimited)
        assertTrue(PageResponseError.detect(url, "", 503) is SourceError.Network)
    }
}

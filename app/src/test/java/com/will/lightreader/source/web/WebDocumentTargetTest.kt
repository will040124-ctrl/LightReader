package com.will.lightreader.source.web

import org.junit.Assert.*
import org.junit.Test

class WebDocumentTargetTest {
    @Test fun `ranking and tag parameters identify distinct pages`() {
        val target = WebDocumentTarget("https://www.wenku8.net/modules/article/tags.php?t=%C1%B5%B0%AE&v=1")
        assertFalse(target.matches("https://www.wenku8.net/modules/article/tags.php"))
        assertFalse(target.matches("https://www.wenku8.net/modules/article/tags.php?t=%C1%B5%B0%AE&v=2"))
        assertTrue(target.matches("https://wenku8.net/modules/article/tags.php?t=%c1%b5%b0%ae&v=1#top"))
    }
    @Test fun `search words cannot reuse another result but may redirect to a single book`() {
        val target=WebDocumentTarget("https://www.wenku8.net/modules/article/search.php?searchkey=%C4%A7")
        assertFalse(target.matches("https://www.wenku8.net/modules/article/search.php?searchkey=%C1%B5"))
        assertTrue(target.matches("https://www.wenku8.net/book/3057.htm"))
        assertFalse(target.matches("https://other.example/book/3057.htm"))
        assertFalse(WebDocumentTarget("https://www.wenku8.net/modules/article/toplist.php?sort=dayvisit").matches("https://www.wenku8.net/book/3057.htm"))
    }
}

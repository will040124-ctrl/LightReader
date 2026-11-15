package com.will.lightreader.source

import com.will.lightreader.domain.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ComicSourceTest {
    private val pages=listOf(ComicPage("a","https://example.test/a?token=old"),ComicPage("b","https://example.test/b"))
    @Test fun `stable identity restores position after page insertion and URL renewal`() {
        val changed=listOf(ComicPage("new","https://example.test/new"),pages[0].copy(url="https://example.test/a?token=new"),pages[1])
        assertEquals(1,restoreComicPage(changed,"a",0))
        assertEquals(2,restoreComicPage(changed,"removed",99))
        assertEquals(0,restoreComicPage(changed,null,-2))
    }
    @Test fun `old chapter cache still decodes and comic order survives roundtrip`() {
        val json=Json { ignoreUnknownKeys=true }
        assertTrue(json.decodeFromString(ChapterContent.serializer(),"""{"chapterUrl":"old","title":"old","blocks":[]}""").comicPages.isEmpty())
        val chapter=ChapterContent("episode","",emptyList(),comicPages=pages)
        assertEquals(pages,json.decodeFromString(ChapterContent.serializer(),json.encodeToString(ChapterContent.serializer(),chapter)).comicPages)
    }
    @Test(expected=IllegalArgumentException::class) fun `empty episode is refused`() { validateComicPages(emptyList()) }
    @Test(expected=IllegalArgumentException::class) fun `duplicate page identities are refused`() { validateComicPages(listOf(pages[0],pages[0])) }
    @Test(expected=IllegalArgumentException::class) fun `local file references cannot come from online comic pages`() { ComicPage("a","file:///private/file") }
    @Test fun `bridge keeps multiple pages in one episode and does not advertise offline downloads`() = runTest {
        val bridge=ComicSourceBridge(object:ComicSource {
            override val id="comic-test"
            override val displayName="Test"
            override suspend fun search(query:String)=emptyList<BookBrief>()
            override suspend fun detail(bookUrl:String)=BookDetail(id,"1",bookUrl,"Test")
            override suspend fun episodes(bookUrl:String)=listOf(ChapterRef("https://example.test/episode","第一话"))
            override suspend fun pages(episodeUrl:String)=pages
        })
        assertEquals(1,bridge.chapterList("https://example.test/book").size)
        assertEquals(2,bridge.chapterContent("https://example.test/episode").comicPages.size)
        assertFalse(bridge.supportsBulkDownload)
        assertNull(bridge.discovery)
    }
}

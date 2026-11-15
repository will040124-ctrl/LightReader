package com.will.lightreader.source.wenku8

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.BrowsePage
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.domain.TagGroup
import com.will.lightreader.source.NovelSource
import com.will.lightreader.source.PopularKinds
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.PageFetcher

/** 轻小说文库（wenku8.net，杰奇 CMS，GB18030 编码，需登录 Cookie）。 */
class Wenku8Source(
    private val http: PageFetcher,
    private val baseOverride: String? = null,
    private val packedOverride: String? = null,   // 测试注入下载包地址
) : NovelSource {
    override val id = Wenku8Routes.SOURCE_ID
    override val displayName = "轻小说文库"

    private fun url(build: Wenku8Routes.() -> String): String {
        val u = Wenku8Routes.build()
        return if (baseOverride == null) u else u.replaceFirst(Wenku8Routes.BASE, baseOverride)
    }

    override suspend fun search(query: String): List<BookBrief> =
        Wenku8Parser.parseSearchResults(text(url { search(query) }))

    override suspend fun searchByAuthor(query: String): List<BookBrief> =
        Wenku8Parser.parseSearchResults(text(url { searchAuthor(query) }))

    override suspend fun tags(): List<TagGroup> =
        Wenku8Parser.parseTagGroups(text(url { tagCloud() }))

    override suspend fun tagBooks(tag: String, page: Int, completedOnly: Boolean, popular: Boolean): BrowsePage =
        Wenku8Parser.parseBrowsePage(text(url { tag(tag, page, completedOnly, popular) }), page)

    override suspend fun completedBooks(page: Int): BrowsePage =
        Wenku8Parser.parseBrowsePage(text(url { completedList(page) }), page)

    override suspend fun authorWorks(authorName: String, authorUrl: String?): List<BookBrief> {
        // wenku8 无独立作者页：用作者名搜索拉出该作者作品（searchtype=author）
        val key = authorName.ifBlank { authorUrl.orEmpty() }
        return Wenku8Parser.parseSearchResults(text(url { searchAuthor(key) }))
    }

    override suspend fun popular(kind: String): List<BookBrief> {
        val year = PopularKinds.sugoiYear(kind)
        return if (year != null) Wenku8Parser.parseSugoiResults(text(url { sugoi(year) }))
        else Wenku8Parser.parseSearchResults(text(url { toplist(kind) }))
    }

    override suspend fun bookDetail(bookUrl: String): BookDetail =
        fetchBook(bookUrl).first

    override suspend fun chapterList(bookUrl: String): List<ChapterRef> {
        // 目录 URL（…/index.htm）可直传；书 URL 先取详情里的目录链接
        val catalogueUrl = if (bookUrl.endsWith("/index.htm")) bookUrl else fetchBook(bookUrl).second
        val list = Wenku8Parser.parseCatalogue(text(catalogueUrl), catalogueUrl)
        if (list.isEmpty()) throw SourceError.ParseChanged(bookUrl, "没有目录")
        return list
    }

    override suspend fun chapterContent(chapterUrl: String): ChapterContent {
        val (bid, cid) = Wenku8Routes.idsOfChapter(chapterUrl)
        val html = text(chapterUrl)
        val title = org.jsoup.Jsoup.parse(html, chapterUrl).selectFirst("#title, h1")?.text()?.trim() ?: ""
        val blocks = if (Regex("<span[^>]*>\\s*null\\s*</span>", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            // 正文只剩 null：走下载包兜底（pack.php 优先，失败换 packtxt.php）
            val primary = packedOverride ?: Wenku8Routes.packed(bid, cid)
            val fallback = packedOverride ?: Wenku8Routes.packedTxt(bid, cid)
            val packed = runCatching { text(primary) }.getOrElse { text(fallback) }
            Wenku8Parser.parsePacked(packed, chapterUrl)
        } else {
            Wenku8Parser.parseChapter(html, chapterUrl, title)
        }
        return ChapterContent(chapterUrl = chapterUrl, title = title, blocks = blocks)
    }

    private suspend fun fetchBook(bookUrl: String): Pair<BookDetail, String> {
        val bid = Wenku8Routes.bookIdOf(bookUrl)
        val parsed = Wenku8Parser.parseBookDetails(text(url { book(bid) }), bid)
        return parsed.detail to parsed.catalogueUrl
    }

    /** 统一出口：测试模式下把站内绝对地址改写到 MockWebServer。wenku8 无乱序问题，取文本即可。 */
    private suspend fun text(u: String, charset: java.nio.charset.Charset = Wenku8Routes.GB18030): String =
        http.getText(if (baseOverride == null) u else u.replaceFirst(Wenku8Routes.BASE, baseOverride), charset).text
}

package com.will.lightreader.source.linovelib

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.source.NovelSource
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.PageFetcher

/**
 * 哔哩轻小说（bilinovel.com 全站 301 到 linovelib.com，只认 linovelib）。
 * 章节正文直接 HTTP 抓取后用 ParagraphOrderRestorer 还原乱序，无需浏览器渲染。
 */
class LinovelibSource(
    private val http: PageFetcher,
    private val baseOverride: String? = null,   // 测试注入 MockWebServer 地址
) : NovelSource {
    override val id = LinovelibRoutes.SOURCE_ID
    override val displayName = "哔哩轻小说"

    private fun url(build: LinovelibRoutes.() -> String): String {
        val u = LinovelibRoutes.build()
        return if (baseOverride == null) u else u.replaceFirst(LinovelibRoutes.BASE, baseOverride)
    }

    override suspend fun search(query: String): List<BookBrief> =
        LinovelibParser.parseBookList(http.getText(url { search(query) }).text, LinovelibRoutes.BASE)

    override suspend fun authorWorks(authorName: String, authorUrl: String?): List<BookBrief> {
        // 优先走作者主页（书籍卡片列表，复用 parseBookList）；无作者页则退化为作者名搜索
        if (!authorUrl.isNullOrBlank()) {
            val html = http.getText(
                if (baseOverride == null) authorUrl else authorUrl.replaceFirst(LinovelibRoutes.BASE, baseOverride)
            ).text
            return LinovelibParser.parseBookList(html, LinovelibRoutes.BASE)
        }
        return LinovelibParser.parseBookList(http.getText(url { searchAuthor(authorName) }).text, LinovelibRoutes.BASE)
    }

    override suspend fun popular(kind: String): List<BookBrief> =
        LinovelibParser.parseBookList(http.getText(url { top(kind) }).text, LinovelibRoutes.BASE)

    override suspend fun bookDetail(bookUrl: String): BookDetail =
        fetchNovel(bookUrl).detail

    override suspend fun chapterList(bookUrl: String): List<ChapterRef> {
        val nid = LinovelibRoutes.bookIdOf(bookUrl)
        // 优先 /catalog 一次拿全书目录；仅当「页面确实没有目录」才退回逐卷页，网络/防护类错误照常上抛
        val fromCatalog = try {
            LinovelibParser.parseCatalog(http.getText(url { catalog(nid) }).text, nid)
        } catch (e: SourceError.ParseChanged) {
            emptyList()
        }
        if (fromCatalog.isNotEmpty()) return fromCatalog
        val novel = fetchNovel(bookUrl)
        val out = mutableListOf<ChapterRef>()
        for (vol in novel.volumes) {
            out += LinovelibParser.parseVolumeChapters(http.getText(url { volume(nid, vol.vid) }).text, nid, vol.vid, vol.title)
        }
        return out
    }

    override suspend fun chapterContent(chapterUrl: String): ChapterContent {
        val (nid, cid) = LinovelibRoutes.chapterIdsOf(chapterUrl)
        val blocks = mutableListOf<Block>()
        var title = ""
        var next: String? = url { chapter(nid, cid) }
        val seen = mutableSetOf<String>()
        while (next != null && next !in seen && seen.size < 30) {
            seen += next
            val page = http.getText(next)
            // 关键：原始 HTML 乱序需还原；WebView（浏览器已处理）的 DOM 不能再还原
            val parsed = LinovelibParser.parseChapterPage(page.text, nid, cid, restoreOrder = !page.browserProcessed)
            if (title.isEmpty()) title = parsed.title
            blocks += parsed.blocks
            next = parsed.nextPageUrl?.let { u ->
                if (baseOverride == null) u else u.replaceFirst(LinovelibRoutes.BASE, baseOverride!!)
            }
        }
        if (blocks.isEmpty()) throw SourceError.ParseChanged(chapterUrl, "分页合并后没有正文")
        return ChapterContent(chapterUrl = chapterUrl, title = title, blocks = blocks)
    }

    private suspend fun fetchNovel(bookUrl: String): LinovelibNovel {
        val nid = LinovelibRoutes.bookIdOf(bookUrl)
        return LinovelibParser.parseNovelDetail(http.getText(url { novel(nid) }).text, nid)
    }
}

package com.will.lightreader.source.linovelib

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.source.SourceError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class LinovelibVolume(val vid: String, val title: String, val coverUrl: String?)
data class LinovelibNovel(val detail: BookDetail, val volumes: List<LinovelibVolume>)
data class ChapterPageParse(val title: String, val blocks: List<Block>, val nextPageUrl: String?)

object LinovelibParser {
    private val TAKEN_DOWN = listOf("抱歉，作品已下架！", "小说下架了", "抱歉，该小说不存在！")

    fun parseNovelDetail(html: String, nid: String): LinovelibNovel {
        val doc = Jsoup.parse(html, LinovelibRoutes.BASE)
        ensureNotTakenDown(doc, LinovelibRoutes.novel(nid))
        val url = LinovelibRoutes.novel(nid)

        fun og(key: String): String =
            doc.selectFirst("meta[property=og:novel\\:$key]")?.attr("content")?.trim().orEmpty()

        // 书名：og 元数据 → 新版 h1.book-title → 老版 .book-name
        val name = og("book_name")
            .ifBlank { doc.selectFirst("h1.book-title")?.text()?.trim().orEmpty() }
            .ifBlank { doc.selectFirst(".book-info > .book-name")?.text()?.trim().orEmpty() }
        if (name.isEmpty()) throw SourceError.ParseChanged(url, "找不到书名（og:novel:book_name / h1.book-title / .book-name）")

        val author = og("author")
            .ifBlank { doc.selectFirst("span.authorname a")?.text()?.trim().orEmpty() }
            .ifBlank { doc.select(".book-author .au-name a").firstOrNull { it.text().isNotBlank() }?.text()?.trim().orEmpty() }
            .ifBlank { doc.selectFirst(".au-name a")?.text()?.trim().orEmpty() }

        // 作者页链接：作者名本就是 <a href>，取其 href（如 /authorarticle/123.html）转绝对地址
        val authorUrl = listOf(
            "span.authorname a",
            ".book-author .au-name a",
            ".au-name a",
        ).asSequence()
            .mapNotNull { doc.selectFirst(it)?.attr("abs:href")?.trim().orEmpty().ifBlank { null } }
            .firstOrNull()

        // 封面：老版 .book-img > img → 新版 img.book-cover
        val cover = doc.selectFirst(".book-img > img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?: doc.selectFirst("img.book-cover")?.attr("src")

        // 标签：老版 .book-label → 新版 em.tag-small
        val labels = doc.select(".book-info > .book-label a").map { it.text().trim() }.filter { it.isNotEmpty() }
            .ifEmpty { doc.select("em.tag-small a").map { it.text().trim() }.filter { it.isNotEmpty() } }

        // 简介：老版 .book-dec → meta[description]（去掉「书名内容简介：」前缀）
        val intro = doc.selectFirst(".book-info > .book-dec > p:not(.backupname)")?.text()?.trim()
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                ?.replaceFirst(Regex("^.*?内容简介[：:]\\s*"), "")
            ?: ""

        val updatedAt = og("update_time")
        val latest = og("latest_chapter_name")
        val volumes = doc.select(".book-vol-chapter > a").mapNotNull { a ->
            val vid = Regex("vol_(\\d+)").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val vtitle = a.selectFirst("h4")?.text()?.trim() ?: "第 $vid 卷"
            val vcover = Regex("url\\(['\"]?(.*?)['\"]?\\)").find(a.selectFirst(".tit.fl")?.attr("style").orEmpty())
                ?.groupValues?.get(1)
            LinovelibVolume(vid, vtitle, vcover?.let { abs(it, LinovelibRoutes.BASE) })
        }.sortedBy { it.vid.toIntOrNull() ?: 0 }

        return LinovelibNovel(
            detail = BookDetail(
                sourceId = LinovelibRoutes.SOURCE_ID, bookId = nid, bookUrl = url, title = name,
                author = author, coverUrl = cover?.takeIf { it.isNotBlank() }?.let { abs(it, LinovelibRoutes.BASE) },
                authorUrl = authorUrl,
                intro = intro, tags = labels,
                status = og("status"), updatedAt = updatedAt, latestChapter = latest,
            ),
            volumes = volumes,
        )
    }

    fun parseVolumeChapters(html: String, nid: String, vid: String, volumeTitle: String): List<ChapterRef> {
        val doc = Jsoup.parse(html, LinovelibRoutes.BASE)
        return doc.select(".book-new-chapter > .tit > a").mapNotNull { a ->
            val cid = Regex("/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            ChapterRef(chapterUrl = LinovelibRoutes.chapter(nid, cid), title = a.text().trim(),
                volume = volumeTitle, chapterId = cid)
        }.filter { it.title.isNotEmpty() }
    }

    /** 目录页 /novel/{nid}/catalog：li.chapter-bar 是分卷头，li.jsChapter 是章节（一次请求拿全书目录）。 */
    fun parseCatalog(html: String, nid: String): List<ChapterRef> {
        val doc = Jsoup.parse(html, LinovelibRoutes.BASE)
        val out = mutableListOf<ChapterRef>()
        var volume = ""
        for (li in doc.select("div.catalog-volume li.chapter-li, li.chapter-li")) {
            if (li.classNames().contains("chapter-bar")) {
                volume = li.selectFirst("a")?.text()?.trim().orEmpty()
                continue
            }
            if (!li.classNames().contains("jsChapter")) continue
            val a = li.selectFirst("a.chapter-li-a") ?: continue
            val cid = Regex("/novel/\\d+/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            val title = a.text().trim()
            if (title.isEmpty()) continue
            out.add(ChapterRef(chapterUrl = LinovelibRoutes.chapter(nid, cid), title = title, volume = volume, chapterId = cid))
        }
        return out
    }

    /**
     * 单个章节页（长章的一页）。
     * @param restoreOrder 原始 HTML（OkHttp 直抓）的段落是洗牌乱序的，需算法还原；
     *   浏览器引擎（WebView）取回的 DOM 站点 JS 已修好顺序，**不能再还原**（还原反而打乱）。
     */
    fun parseChapterPage(html: String, nid: String, cid: String, restoreOrder: Boolean = true): ChapterPageParse {
        val url = LinovelibRoutes.chapter(nid, cid)
        val doc = Jsoup.parse(html, url)
        ensureNotTakenDown(doc, url)
        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        // 新版（2025+）容器 #acontent；老版 #mlfy_main_text > #TextContent
        val container = doc.selectFirst("#mlfy_main_text > #TextContent")
            ?: doc.selectFirst("#acontent")
            ?: throw SourceError.ParseChanged(url, "找不到正文容器（#TextContent / #acontent）")

        // 1) 收集 <p>（innerHTML 去空白非空）；原始 HTML 需按站点同款置换还原顺序
        val paragraphs = container.children().filter { it.tagName() == "p" && it.html().replace("\\s".toRegex(), "").isNotEmpty() }
        val ordered = if (restoreOrder) ParagraphOrderRestorer.restore(paragraphs, cid.toIntOrNull() ?: 0) else paragraphs
        val slot = HashMap<Element, Element>()   // 页序节点 -> 还原后节点（占同一位置）
        for (i in paragraphs.indices) slot[paragraphs[i]] = ordered[i]

        // 2) 按容器子节点顺序出块：<p> 用还原后的节点，其余原样
        val blocks = mutableListOf<Block>()
        for (node in container.childNodes()) {
            when (node) {
                is TextNode -> cleanSiteChrome(node.text().trim()).takeIf { it.isNotEmpty() }?.let { blocks.add(Block.Paragraph(it)) }
                is Element -> {
                    if (isDecoy(node)) continue
                    val el = slot[node] ?: node
                    if (el.tagName() == "br") continue
                    if (el.tagName() == "hr") { blocks.add(Block.Divider); continue }
                    if (el.tagName() == "img") { imageBlock(el, url)?.let(blocks::add); continue }
                    for (img in el.select("img")) imageBlock(img, url)?.let(blocks::add)
                    val text = cleanSiteChrome(el.text().trim())
                    if (text.isNotEmpty()) blocks.add(Block.Paragraph(text))
                }
            }
        }
        if (blocks.isEmpty()) throw SourceError.ParseChanged(url, "解析后没有正文块")

        val next = doc.select(".mlfy_page a").firstOrNull { it.text().contains("下一页") || it.text().contains("下一頁") }
            ?.attr("abs:href")
        return ChapterPageParse(title = title, blocks = blocks, nextPageUrl = next)
    }

    /** 文库列表 / 榜单 / 搜索结果共用：新版 .bookbox 卡片，老版 .mind-book 卡片，最后退回裸锚点。 */
    fun parseBookList(html: String, baseUrl: String): List<BookBrief> {
        val doc = Jsoup.parse(html, baseUrl)
        val out = LinkedHashMap<String, BookBrief>()

        fun put(nid: String, title: String, author: String, cover: String?) {
            if (title.isEmpty() || out.containsKey(nid)) return
            out[nid] = BookBrief(
                sourceId = LinovelibRoutes.SOURCE_ID, bookId = nid,
                bookUrl = LinovelibRoutes.novel(nid), title = title, author = author,
                coverUrl = cover?.takeIf { it.isNotBlank() && !it.endsWith("book-cover-no.svg") }?.let { abs(it, baseUrl) },
            )
        }

        for (box in doc.select(".store_collist > .bookbox")) {
            val a = box.selectFirst(".bookimg a") ?: box.selectFirst("a[href*=/novel/]")
            val nid = Regex("/novel/(\\d+)").find(a?.attr("href").orEmpty())?.groupValues?.get(1) ?: continue
            val title = box.selectFirst(".bookname")?.text()?.trim().orEmpty()
            val cover = box.selectFirst(".bookimg img")?.attr("data-original")?.ifBlank { null }
                ?: box.selectFirst(".bookimg img")?.attr("src")
            val author = box.select(".bookilnk span").firstOrNull()?.text()?.trim() ?: ""
            put(nid, title, author, cover)
        }

        // 新版（2025+）卡片：li.book-li > a.book-layout，封面 data-src 懒加载，作者 span.book-author
        for (card in doc.select("li.book-li")) {
            val a = card.selectFirst("a.book-layout") ?: continue
            val nid = Regex("/novel/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            put(nid,
                card.selectFirst("h4.book-title")?.text()?.trim() ?: "",
                card.selectFirst("span.book-author")?.text()?.trim() ?: "",
                card.selectFirst("div.book-cover img")?.attr("data-src")?.ifBlank { null })
        }

        // 老版（2021 前后）文库卡片：div.mind-book，封面 img[data-original]，作者 div.author a
        for (card in doc.select("div.mind-book")) {
            val a = card.selectFirst(".bookname a") ?: card.selectFirst("div.imgbox a") ?: continue
            val nid = Regex("/novel/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            put(nid, a.text().trim(),
                card.selectFirst("div.author a")?.text()?.trim() ?: "",
                card.selectFirst("div.imgbox img")?.attr("data-original")?.ifBlank { null })
        }

        // 老版榜单卡片：div.rank_d_list，标题在 rank_d_b_name，封面在 rank_d_book_img
        for (card in doc.select("div.rank_d_list")) {
            val a = card.selectFirst("div.rank_d_b_name a") ?: continue
            val nid = Regex("/novel/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: continue
            put(nid, a.text().trim(),
                card.selectFirst("a[href*=/authorarticle/]")?.text()?.trim() ?: "",
                card.selectFirst("div.rank_d_book_img img")?.attr("data-original")?.ifBlank { null })
        }

        if (out.isEmpty()) {
            for (a in doc.select("a[href*=/novel/][href$=.html]")) {
                val nid = Regex("/novel/(\\d+)\\.html").find(a.attr("href"))?.groupValues?.get(1) ?: continue
                put(nid, a.text().trim(), "", null)
            }
        }
        return out.values.toList()
    }

    /** 无 JS 环境下站点注入的样板提示，不属于正文（繁简两式都可能出现）。 */
    private fun cleanSiteChrome(text: String): String =
        text.replace(Regex("[（(]?內容加載失敗[！!]?(請刷新或更換瀏覽器)[）)]?"), "")
            .replace(Regex("[（(]?内容加载失败[！!]?(请刷新或更换浏览器)[）)]?"), "")
            .trim()

    private fun isDecoy(el: Element): Boolean {
        val cls = el.classNames().joinToString(" ")
        if ("dag" in cls || "google" in cls || "ad-slot" in cls) return true
        if (el.id().contains("hidden-images") || el.id().contains("show-more-images")) return true
        val style = el.attr("style").replace(" ", "").lowercase()
        if (style.contains("display:none") || style.contains("visibility:hidden")) return true
        if (Regex("scale\\(0(?:\\.0+)?\\)").containsMatchIn(style)) return true
        val matrix = Regex("matrix\\(([^)]+)\\)").find(style)?.groupValues?.get(1)
        if (matrix != null) {
            val v = matrix.split(",", ";").map { it.trim().toDoubleOrNull() ?: 1.0 }
            if (v.size >= 6 && (v[0] == 0.0 || v[3] == 0.0)) return true
        }
        return false
    }

    private fun imageBlock(img: Element, baseUrl: String): Block.Image? {
        val ref = img.attr("data-src").ifBlank { img.attr("src") }.trim()
        if (ref.isEmpty()) return null
        return Block.Image(ref = abs(ref, baseUrl), caption = img.attr("alt").trim().ifEmpty { null })
    }

    private fun ensureNotTakenDown(doc: Document, url: String) {
        val text = doc.text()
        if (TAKEN_DOWN.any { text.contains(it) }) throw SourceError.TakenDown(url)
    }

    private fun abs(href: String, base: String): String =
        if (href.startsWith("//")) "https:$href" else java.net.URI(base).resolve(href).toString()
}

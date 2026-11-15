package com.will.lightreader.source.wenku8

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.BrowsePage
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.domain.TagGroup
import com.will.lightreader.source.SourceError
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object Wenku8Parser {
    private val LABELS = listOf("文库分类", "小说分类", "小说作者", "作者", "文章状态", "小说状态",
        "最后更新", "更新时间", "全文长度", "全文字数", "作品Tags", "作品热度", "最近章节", "内容简介")

    data class ParsedBook(val detail: BookDetail, val catalogueUrl: String)

    /** 登录墙判定：页面带登录表单。注意老版页面顶部导航也带登录框，故只在「解析不出内容」时才用它裁决。 */
    private fun hasLoginForm(html: String): Boolean =
        Regex("name=[\"']?(username|useremail|passwd|password)", RegexOption.IGNORE_CASE).containsMatchIn(html) &&
            html.contains("用户登录")

    private fun authOrParse(html: String, url: String, detail: String): SourceError =
        if (hasLoginForm(html)) SourceError.AuthRequired(url) else SourceError.ParseChanged(url, detail)

    fun parseBookDetails(html: String, bid: String): ParsedBook {
        val url = Wenku8Routes.book(bid)
        val doc = Jsoup.parse(html, url)
        val content = doc.selectFirst("#content") ?: throw authOrParse(html, url, "找不到 #content")
        val title = (content.selectFirst("span b")?.text()?.trim() ?: "")
            .ifBlank { content.selectFirst("h1")?.text()?.trim() ?: "" }
            .ifBlank { doc.title().split("-").firstOrNull()?.trim() ?: "" }
        if (title.isEmpty()) throw authOrParse(html, url, "找不到书名")
        val pageText = content.text()
        val catalogueHref = content.select("a[href$=/index.htm]").firstOrNull()?.attr("abs:href")
            ?: throw SourceError.ParseChanged(url, "找不到目录链接")
        val cover = content.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        val detail = BookDetail(
            sourceId = Wenku8Routes.SOURCE_ID, bookId = bid, bookUrl = url, title = title,
            author = label(pageText, listOf("小说作者", "作者")).ifBlank { "未知作者" },
            coverUrl = cover?.takeIf { it.isNotBlank() }?.let { abs(it, url) },
            intro = labelAfter(content, "内容简介"),
            tags = label(pageText, listOf("作品Tags")).split(Regex("\\s+")).filter { it.isNotBlank() },
            status = label(pageText, listOf("文章状态", "小说状态")),
            updatedAt = label(pageText, listOf("最后更新", "更新时间")),
            latestChapter = labelAfter(content, "最近章节").ifBlank { label(pageText, listOf("最近章节")) },
        )
        return ParsedBook(detail, catalogueHref)
    }

    /** 目录：table tr 里 td[colspan] 是分卷头，其余行的链接是章节。 */
    fun parseCatalogue(html: String, catalogueUrl: String): List<ChapterRef> {
        val doc = Jsoup.parse(html, catalogueUrl)
        val out = mutableListOf<ChapterRef>()
        var volume = ""
        for (row in doc.select("table tr")) {
            val head = row.select("td[colspan]").firstOrNull()
            if (head != null) {
                volume = head.text().trim().ifBlank { "第 ${out.size + 1} 卷" }
                continue
            }
            for (a in row.select("a[href]")) {
                val href = a.attr("abs:href")
                if (!Regex("\\.htm$").containsMatchIn(href)) continue
                val title = a.text().trim()
                if (title.isEmpty()) continue
                val cid = Regex("/(\\d+)\\.htm").find(href)?.groupValues?.get(1) ?: ""
                out.add(ChapterRef(chapterUrl = href, title = title, volume = volume, chapterId = cid))
            }
        }
        if (out.isEmpty()) throw authOrParse(html, catalogueUrl, "目录页没有章节")
        return out
    }

    /** 章节正文：【图片】占位符与 <img> 都出 Image 块；<br> 为段落边界；过滤水印与站点 chrome 图。 */
    fun parseChapter(html: String, chapterUrl: String, chapterTitle: String): List<Block> {
        if (Regex("因版权问题[^<]*不再提供").containsMatchIn(html)) throw SourceError.TakenDown(chapterUrl)
        val doc = Jsoup.parse(html, chapterUrl)
        val root = doc.selectFirst("#content") ?: doc.selectFirst("#contentmain")
            ?: throw SourceError.ParseChanged(chapterUrl, "找不到正文节点")
        // A partially parsed page can expose #contentmain with only the title/ad slot.
        if (root.id() == "contentmain") root.select("#title, [id^=adv], script, style").remove()
        val blocks = mutableListOf<Block>()
        val buf = StringBuilder()

        fun flush() {
            val text = buf.toString().replace('\u00a0', ' ').replace(Regex("[ \\t\\r]+"), " ").trim()
            buf.setLength(0)
            if (text.isEmpty()) return
            // <br> 换行是段落边界；行内再切图片占位符
            for (line in text.split(Regex("\\n+"))) {
                for (piece in line.split(Regex("(【图片】|\\[图片\\])"))) {
                    when {
                        piece.isEmpty() -> continue
                        Regex("^(【图片】|\\[图片\\])$").matches(piece) ->
                            blocks.add(Block.Image(ref = "", caption = "图片"))   // 占位图无 URL（真图只在下载包里）
                        else -> {
                            val t = piece.trim()
                            if (t.isNotEmpty()) blocks.add(Block.Paragraph(t))
                        }
                    }
                }
            }
        }

        fun visit(node: org.jsoup.nodes.Node) {
            when (node) {
                is org.jsoup.nodes.TextNode -> buf.append(node.text())
                is Element -> when (node.tagName().lowercase()) {
                    "script", "style" -> {}
                    "img" -> {
                        flush()
                        val ref = node.attr("data-src").ifBlank { node.attr("data-original").ifBlank { node.attr("src") } }
                        if (ref.isNotBlank() && !isChromeImage(ref)) {
                            blocks.add(Block.Image(ref = abs(ref, chapterUrl), caption = node.attr("alt").ifBlank { null }))
                        }
                    }
                    "br" -> buf.append('\n')
                    "hr" -> { flush(); blocks.add(Block.Divider) }
                    else -> for (c in node.childNodes()) visit(c)
                }
            }
        }
        for (c in root.childNodes()) visit(c)
        flush()

        val filtered = blocks.filterNot {
            it is Block.Paragraph && Regex("轻小说文库.*一网打尽|本文来自\\s*轻小说文库").containsMatchIn(it.text)
        }
        if (filtered.isEmpty()) throw authOrParse(html, chapterUrl, "解析后没有正文（$chapterTitle）")
        return filtered
    }

    /** 下载包兜底：纯文本/HTML 里的图片 URL 与段落。 */
    fun parsePacked(content: String, chapterUrl: String): List<Block> {
        val imgRe = Regex("https?://[^\\s\"'<>]+\\.(?:jpg|jpeg|png|gif|webp)(?:\\(\\d+K\\))?", RegexOption.IGNORE_CASE)
        val blocks = mutableListOf<Block>()
        val text = imgRe.replace(content) { m -> "\n%%IMG%%${m.value}%%\n" }
        for (piece in text.split(Regex("(?:\\r?\\n){2,}"))) {
            val t = piece.trim()
            if (t.isEmpty() || Regex("轻小说文库.*一网打尽").containsMatchIn(t)) continue
            if (t.startsWith("%%IMG%%")) {
                val url = t.removePrefix("%%IMG%%").substringBefore("%%")
                blocks.add(Block.Image(ref = url.replace(Regex("\\(\\d+K\\)$"), "")))
            } else {
                blocks.add(Block.Paragraph(t))
            }
        }
        if (blocks.isEmpty()) throw SourceError.ParseChanged(chapterUrl, "下载包没有可用正文")
        return blocks
    }

    /** 搜索结果 / 榜单 共用卡片解析。空结果 + 登录表单 = 未登录的内联登录页，抛 AuthRequired。 */
    fun parseSearchResults(html: String): List<BookBrief> {
        val doc = Jsoup.parse(html, Wenku8Routes.BASE)
        val out = LinkedHashMap<String, BookBrief>()

        // A unique search match can redirect directly to the detail page.
        val catalogue = doc.selectFirst("#content a[href$=/index.htm]")?.attr("abs:href")
        val directId = catalogue?.let { Regex("/novel/(?:\\d+/)?(\\d+)/index\\.htm").find(it)?.groupValues?.get(1) }
        if (directId != null && doc.selectFirst("#content span b, #content h1") != null) {
            val detail = parseBookDetails(html, directId).detail
            return listOf(BookBrief(detail.sourceId, detail.bookId, detail.bookUrl, detail.title,
                detail.author, detail.coverUrl, detail.tags, detail.status))
        }

        // 现行卡片：div[style 宽373px]，封面 img、标题 b>a、「作者:x/分类:y」p 行
        for (card in doc.select("div[style*=373px]")) {
            val a = card.selectFirst("a[href*=/book/]") ?: continue
            val bid = bidOf(a.attr("href")) ?: continue
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.isEmpty() || out.containsKey(bid)) continue
            val text = card.text()
            val cover = card.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            val author = Regex("作者\\s*[:：]\\s*([^/／|]+)").find(text)
                ?.groupValues?.get(1)?.replace('\u00a0', ' ')?.trim().orEmpty()
            // 列表页顺带带出 Tags 与连载状态（标签页 / 完结页 / 搜索页同构）
            val tags = card.select("p").firstOrNull { it.text().trim().startsWith("Tags") }
                ?.text()?.trim()?.substringAfter(":")?.substringAfter("：")
                ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            val status = when {
                "已完结" in text -> "已完结"
                "连载中" in text -> "连载中"
                else -> ""
            }
            out[bid] = BookBrief(Wenku8Routes.SOURCE_ID, bid, Wenku8Routes.book(bid), title,
                author = author,
                coverUrl = coverUrl(cover) ?: coverByBid(bid),
                tags = tags, status = status)
        }

        // 兜底：裸锚点（老页面/榜单变体/移动版列表）：从链接附近补作者与状态，封面按 bookId 推导
        if (out.isEmpty()) {
            for (a in doc.select("a[href]")) {
                val bid = bidOf(a.attr("href")) ?: continue
                val title = a.attr("tiptitle").ifBlank { a.attr("title") }.ifBlank { a.text() }.trim()
                if (title.isEmpty() || title in setOf("我要阅读", "加入书架", "推荐本书") || out.containsKey(bid)) continue
                val scope = a.parents().firstOrNull { it.text().length in 1..800 }
                val text = scope?.text().orEmpty()
                val author = Regex("作者\\s*[:：]\\s*([^/／|]+)").find(text)
                    ?.groupValues?.get(1)?.replace('\u00a0', ' ')?.trim().orEmpty()
                val status = when {
                    "已完结" in text -> "已完结"
                    "连载中" in text -> "连载中"
                    else -> ""
                }
                out[bid] = BookBrief(Wenku8Routes.SOURCE_ID, bid, Wenku8Routes.book(bid), title,
                    author = author, coverUrl = coverByBid(bid), status = status)
            }
        }
        if (out.isEmpty() && hasLoginForm(html)) throw SourceError.AuthRequired(Wenku8Routes.BASE)
        if (out.isEmpty() && !Regex("没有找到|未找到|没有符合|没有搜索到|没有相关|找不到.*(?:小说|记录)").containsMatchIn(doc.text()))
            throw SourceError.ParseChanged(Wenku8Routes.BASE, "列表尚未完整载入，请重试")
        return out.values.toList()
    }

    /** 列表封面：相对地址转绝对、升 https（Android 默认拦明文）；noimg 哨兵视为无封面。 */
    private fun coverUrl(raw: String?): String? {
        val src = raw?.trim().orEmpty()
        if (src.isEmpty() || Regex("noimg\\.[a-z]+$", RegexOption.IGNORE_CASE).containsMatchIn(src)) return null
        return abs(src, Wenku8Routes.BASE).replaceFirst(Regex("^http://"), "https://")
    }

    /** 页面没有 img 时按站点图床规律兜底：image/{bid/1000}/{bid}/{bid}s.jpg。 */
    private fun coverByBid(bid: String): String? {
        val n = bid.toIntOrNull() ?: return null
        return "https://img.wenku8.com/image/${n / 1000}/$bid/${bid}s.jpg"
    }

    /**
 * 标签云：Tags云集页的 `ul.ultops` 里，无链接的 li 是分类标题（如「日常系属性Tags：」），
 * 其余 li 内是一组 `tags.php?t=xxx` 标签链接。
 */
fun parseTagGroups(html: String): List<TagGroup> {
    val doc = Jsoup.parse(html, Wenku8Routes.BASE)
    val list = doc.selectFirst("ul.ultops") ?: throw authOrParse(html, Wenku8Routes.tagCloud(), "找不到标签云")
    val out = mutableListOf<TagGroup>()
    var groupName = "Tags"
    val buf = mutableListOf<String>()

    fun flush() {
        val cleaned = buf.distinct()
        if (cleaned.isNotEmpty()) out.add(TagGroup(groupName, cleaned))
        buf.clear()
    }

    for (li in list.select("> li")) {
        val links = li.select("a[href*=tags.php]").filter { it.attr("href").contains("t=") }
        if (links.isEmpty()) {
            val head = li.text().trim()
            if (head.isNotEmpty()) {
                flush()
                groupName = head.replace(Regex("(?:属性)?类?Tags[：:]?$"), "").trim().ifBlank { "Tags" }
            }
        } else {
            for (a in links) {
                val t = a.text().trim()
                if (t.isNotEmpty()) buf.add(t)
            }
        }
    }
    flush()
    if (out.isEmpty()) throw authOrParse(html, Wenku8Routes.tagCloud(), "标签云尚未完整载入")
    return out
}

/** 可翻页列表页（标签结果 / 完结全本）：卡片解析复用搜索，页码看底部分页链接。 */
fun parseBrowsePage(html: String, page: Int): BrowsePage {
    val items = parseSearchResults(html)
    val doc = Jsoup.parse(html, Wenku8Routes.BASE)
    val maxPage = doc.select("a[href*=page=]").mapNotNull {
        Regex("[?&]page=(\\d+)").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
    }.maxOrNull()
    val hasMore = if (maxPage != null) maxPage > page else items.size >= 20
    return BrowsePage(items, hasMore)
}

/** 轻小说大赏：按 table.grid 分榜单段，收录其中的书籍链接。 */
    fun parseSugoiResults(html: String): List<BookBrief> {
        val doc = Jsoup.parse(html, Wenku8Routes.BASE)
        val out = LinkedHashMap<String, BookBrief>()
        val tables = doc.select("table.grid").ifEmpty { listOf(doc.body()) }
        for (table in tables) {
            val section = table.selectFirst("caption")?.text()?.trim().orEmpty()
                .replace(Regex("^这本轻小说真厉害！?\\d+\\s*"), "")
            for (a in table.select("a[href]")) {
                val bid = bidOf(a.attr("href")) ?: continue
                val title = a.attr("title").ifBlank { a.text() }.trim()
                if (title.isEmpty() || out.containsKey(bid)) continue
                out[bid] = BookBrief(Wenku8Routes.SOURCE_ID, bid, Wenku8Routes.book(bid), title = title, author = section)
            }
        }
        if (out.isEmpty() && hasLoginForm(html)) throw SourceError.AuthRequired(Wenku8Routes.BASE)
        return out.values.toList()
    }

    /** 书目 ID：现行 /book/{bid}.htm 与老版 articleinfo.php?id={bid} 两种形态都认。 */
    private fun bidOf(href: String): String? {
        val m = Regex("/book/(\\d+)\\.htm").find(href) ?: Regex("articleinfo\\.php\\?id=(\\d+)").find(href)
        return m?.groupValues?.get(1)
    }

    private fun label(text: String, names: List<String>): String {
        for (name in names) {
            val others = LABELS.filter { it != name }.joinToString("|") { Regex.escape(it) }
            val m = Regex("${Regex.escape(name)}\\s*[：:]\\s*([\\s\\S]*?)(?=\\s*(?:$others)\\s*[：:]|$)").find(text)
            if (m != null) return m.groupValues[1].trim()
        }
        return ""
    }

    private fun labelAfter(content: Element, name: String): String {
        val labeled = content.select("span").firstOrNull { it.text().trim().startsWith(name) } ?: return ""
        val same = labeled.text().trim().removePrefix(name).trim(' ', ':', '：')
        if (same.isNotEmpty()) return same
        var el = labeled.nextElementSibling()
        while (el != null) {
            if (el.tagName() != "br") {
                val t = el.text().trim()
                if (t.isNotEmpty()) return t
            }
            el = el.nextElementSibling()
        }
        return ""
    }

    private fun isChromeImage(url: String): Boolean =
        Regex("(?:logo|banner|avatar|button|icon|counter)[^/]*\\.(?:gif|png|jpe?g)$", RegexOption.IGNORE_CASE)
            .containsMatchIn(url)

    private fun abs(href: String, base: String): String =
        if (href.startsWith("//")) "https:$href" else java.net.URI(base).resolve(href).toString()
}

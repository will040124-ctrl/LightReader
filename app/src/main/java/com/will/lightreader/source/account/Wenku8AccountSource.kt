package com.will.lightreader.source.account

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.PageFetcher
import com.will.lightreader.source.wenku8.Wenku8Routes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder

class Wenku8AccountSource(private val pages: PageFetcher, private val onUnrecognized: (String) -> Unit = {}) : AccountLibrarySource {
    override val sourceId = "wenku8"
    override val displayName = "轻小说文库"
    override val loginUrl = Wenku8Routes.BASE + "/login.php"
    override suspend fun readLibrary(onProgress: (Int, Int) -> Unit): AccountLibrarySnapshot {
        val queue = ArrayDeque<String>(); val visited = mutableSetOf<String>()
        val entries = linkedMapOf<String, AccountLibraryEntry>(); val fingerprints = mutableSetOf<String>()
        queue.add(Wenku8AccountParser.BOOKCASE)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            require(visited.size <= 80) { "网站书架分页超过本次上限，请按网站分组整理后重试" }
            // A failure on any page aborts the complete snapshot. No partial result is presented as success.
            val html = pages.getText(url, Wenku8Routes.GB18030).text
            val page = try { Wenku8AccountParser.parse(html, url) }
                catch(e:SourceError.ParseChanged) { onUnrecognized(Wenku8AccountParser.shape(html));throw e }
            val uri = URI(url)
            val group = Regex("[?&]classid=(-?\\d+)").find(uri.toString())?.groupValues?.get(1).orEmpty()
            val fingerprint = group + ":" + page.entries.map { it.book.bookId }.sorted().joinToString(",")
            if (page.entries.isNotEmpty() && !fingerprints.add(fingerprint) && Regex("[?&]page=([2-9]|[1-9]\\d)").containsMatchIn(url))
                throw SourceError.ParseChanged(url,"账号书架分页没有前进")
            for (entry in page.entries) {
                val old = entries[entry.book.bookId]
                if (old == null || old.readChapterUrl == null && entry.readChapterUrl != null) entries[entry.book.bookId] = entry
            }
            require(entries.size <= 10_000) { "网站书架条目过多，请分批整理后重试" }
            page.moreUrls.filterNot { it in visited || it in queue }.forEach(queue::addLast)
            onProgress(visited.size, entries.size)
        }
        return AccountLibrarySnapshot(sourceId, entries.values.toList(), visited.size)
    }
}

object Wenku8AccountParser {
    const val BOOKCASE = "https://www.wenku8.net/modules/article/bookcase.php"
    data class Page(val entries: List<AccountLibraryEntry>, val moreUrls: List<String>)
    /** Whitelisted structural flags only. Never includes titles, account names, URLs or cookies. */
    fun shape(html: String): String {
        val doc=Jsoup.parse(html,BOOKCASE)
        val headings=doc.select("th").text()
        val words=listOf("书名","文章名称","小说名称","小说","书籍","名称","最新章节","已读章节","上次阅读","书签","操作","删除").filter { headings.contains(it) }
        val emptyWords=listOf("为空","没有","暂无","空的","尚未收藏","空空").filter { doc.text().contains(it) }
        val paths=doc.select("table a[href]").map { a -> runCatching {
            val uri=URI(a.attr("abs:href"));val path=uri.path.orEmpty()
            if(path.startsWith("/modules/article/")||path.startsWith("/book/")||path.startsWith("/novel/"))
                path.replace(Regex("[0-9]+"),"N")+"?"+query(uri.rawQuery).keys.sorted().joinToString(",") else "other"
        }.getOrDefault("other") }.distinct().take(8)
        val body=doc.body().text()
        val zero=Regex("(?:共有|总计|总共|共收藏|已收藏)\\s*[:：]?\\s*0\\s*(?:本|部)").containsMatchIn(body)
        return "tables=${doc.select("table").size};rows=${doc.select("table tr").size};cells=${doc.select("table td").size};bookLinks=${doc.select("a[href]").count{bookId(it.attr("abs:href"))!=null}};passwords=${doc.select("input[type=password]").size};headings=$words;emptyFlags=$emptyWords;zero=$zero;paths=$paths;accountMarker=${body.contains("书架")};registeredOnly=${body.contains("注册用户")}".trim()
    }
    private val chapterPattern = Regex("/novel/(?:\\d+/)?(\\d+)/(\\d+)\\.htm")
    private fun ownUrl(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme in setOf("http", "https") && it.host in setOf("www.wenku8.net", "wenku8.net") && it.userInfo == null && (it.port == -1 || it.port in setOf(80, 443))
    }
    fun bookId(url: String): String? {
        val uri = ownUrl(url) ?: return null
        return Regex("^/book/(\\d{1,9})\\.htm$").matchEntire(uri.path)?.groupValues?.get(1)
            ?: Regex("^/novel/(?:\\d+/)?(\\d{1,9})/index\\.htm$").matchEntire(uri.path)?.groupValues?.get(1)
            ?: if (uri.path == "/modules/article/articleinfo.php") query(uri.rawQuery)["id"]?.takeIf { it.matches(Regex("[0-9]{1,9}")) } else null
    }
    fun validEntry(entry: AccountLibraryEntry): Boolean {
        val book = entry.book
        if (book.sourceId != "wenku8" || bookId(book.bookUrl) != book.bookId || book.title.isBlank()) return false
        val chapter = entry.readChapterUrl ?: return true
        return ownUrl(chapter)?.path?.let { chapterPattern.matchEntire(it)?.groupValues?.get(1) == book.bookId } == true
    }
    private fun query(value: String?): Map<String, String> = value.orEmpty().split('&').filter { '=' in it }.associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    private fun canonicalPage(url: String): String? {
        val uri = ownUrl(url) ?: return null
        if (uri.path != "/modules/article/bookcase.php") return null
        val params = runCatching { query(uri.rawQuery) }.getOrNull() ?: return null
        // In particular never follow act=delete/clean, external links or session/user parameters.
        if (params.keys.any { it !in setOf("page", "classid") } || params.values.any { !it.matches(Regex("-?[0-9]{1,4}")) }) return null
        if (params["page"]?.toIntOrNull()?.let { it !in 1..80 } == true) return null
        return BOOKCASE + params.toSortedMap().entries.joinToString("&", prefix = if (params.isEmpty()) "" else "?") { "${it.key}=${it.value}" }
    }
    fun parse(html: String, url: String = BOOKCASE): Page {
        val doc = Jsoup.parse(html, url)
        if(doc.title().contains("Just a moment",ignoreCase=true) || html.contains("cf-chl-",ignoreCase=true)) throw SourceError.ChallengeRequired(BOOKCASE)
        val tables = doc.select("table").filter { table ->
            val headers = table.select("th").text().ifBlank { table.selectFirst("tr")?.text().orEmpty() }
            (headers.contains("书名") || headers.contains("小说名称") || headers.contains("文章名称") || headers.contains("小说") || headers.contains("书籍") ||
                listOf("最新章节","书签","操作").all { headers.contains(it) }) &&
                listOf("已读", "书签", "操作", "删除").any { headers.contains(it) }
        }
        if (tables.isEmpty()) {
            val message = doc.select("#content, .blockcontent, body").text()
            if (listOf("请先登录", "尚未登录", "没有登录", "用户登录").any { message.contains(it) } ||
                doc.select("form input[name=password], form input[name=passwd]").isNotEmpty()) throw SourceError.AuthRequired(BOOKCASE)
            throw SourceError.ParseChanged(BOOKCASE, "未识别到账号书架表格；现有书架未改动")
        }
        val entries = mutableListOf<AccountLibraryEntry>()
        for (table in tables) {
            val header = table.selectFirst("tr") ?: continue
            val headings = header.select("th,td").map { it.text() }
            val readIndex = headings.indexOfFirst { label -> (label.trim() == "书签" || listOf("已读", "上次阅读", "阅读书签").any { label.contains(it) }) && !label.contains("最新") }
            for (row in table.select("tr").drop(1)) {
                val link = row.select("a[href]").firstOrNull { bookId(it.attr("abs:href")) != null } ?: continue
                val bid = bookId(link.attr("abs:href")) ?: continue
                val title = link.text().trim(); if (title.isBlank()) continue
                val readLink = row.select("td").getOrNull(readIndex)?.select("a[href]")?.firstOrNull { a ->
                    ownUrl(a.attr("abs:href"))?.path?.let { chapterPattern.matchEntire(it)?.groupValues?.get(1) == bid } == true
                }
                entries += AccountLibraryEntry(BookBrief("wenku8", bid, Wenku8Routes.book(bid), title),
                    readLink?.attr("abs:href"), readLink?.text().orEmpty())
            }
        }
        // A recognized empty table needs an explicit empty-state message, otherwise stop on redesign.
        if(entries.isEmpty()) {
            val body=doc.body().text()
            val statedEmpty=listOf("书架为空", "没有收藏", "没有任何", "没有书籍", "没有书本", "暂无", "没有找到").any { body.contains(it) }
            val zeroCount=Regex("(?:已收藏|本组有)\\s*0\\s*本").containsMatchIn(body)
            val authenticated=doc.select("a[href*=logout.php]").isNotEmpty()
            if(zeroCount && !authenticated)throw SourceError.AuthRequired(BOOKCASE)
            if(!statedEmpty && !zeroCount)throw SourceError.ParseChanged(BOOKCASE,"网站没有返回可识别条目，也没有空书架提示")
        }
        val more = doc.select("a[href]").mapNotNull { canonicalPage(it.attr("abs:href")) }.toMutableList()
        doc.select("select[name=classid] option[value]").forEach { option ->
            val value = option.attr("value")
            canonicalPage("$BOOKCASE?classid=$value")?.let(more::add)
        }
        return Page(entries.distinctBy { it.book.bookId }, more.distinct())
    }
}

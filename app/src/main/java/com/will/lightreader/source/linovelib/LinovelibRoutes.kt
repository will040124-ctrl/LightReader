package com.will.lightreader.source.linovelib

import java.net.URLEncoder

object LinovelibRoutes {
    const val BASE = "https://www.linovelib.com"
    const val SOURCE_ID = "linovelib"
    const val SEARCH_PATH = "/search.php?searchtype=articlename&searchkey="
    const val SEARCH_AUTHOR_PATH = "/search.php?searchtype=author&searchkey="

    fun novel(nid: String) = "$BASE/novel/$nid.html"
    fun catalog(nid: String) = "$BASE/novel/$nid/catalog"
    fun volume(nid: String, vid: String) = "$BASE/novel/$nid/vol_$vid.html"
    fun chapter(nid: String, cid: String, page: Int = 1) =
        "$BASE/novel/$nid/$cid" + (if (page > 1) "_$page" else "") + ".html"
    fun top(sort: String, page: Int = 1) = "$BASE/top/$sort/$page.html"
    fun wenkuList() = "$BASE/wenku/lastupdate_0_0_0_0_0_0_1.html"
    fun search(query: String) = BASE + SEARCH_PATH + URLEncoder.encode(query, "UTF-8")
    fun searchAuthor(query: String) = BASE + SEARCH_AUTHOR_PATH + URLEncoder.encode(query, "UTF-8")
    /** 作者主页（详情页作者链接指向的 /authorarticle/… 页面）。 */
    fun authorPage(url: String) = url

    /** 书 URL 必须形如 /novel/{nid}.html */
    fun bookIdOf(bookUrl: String): String =
        Regex("/novel/(\\d+)").find(bookUrl)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法识别书籍 URL：$bookUrl")

    /** 章节 URL 形如 /novel/{nid}/{cid}(_N)?.html */
    fun chapterIdsOf(chapterUrl: String): Pair<String, String> {
        val m = Regex("/novel/(\\d+)/(\\d+)(?:_\\d+)?\\.html").find(chapterUrl)
            ?: throw IllegalArgumentException("无法识别章节 URL：$chapterUrl")
        return m.groupValues[1] to m.groupValues[2]
    }
}

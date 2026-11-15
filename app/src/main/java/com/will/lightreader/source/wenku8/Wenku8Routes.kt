package com.will.lightreader.source.wenku8

import java.net.URLEncoder
import java.nio.charset.Charset

object Wenku8Routes {
    const val BASE = "https://www.wenku8.net"
    const val SOURCE_ID = "wenku8"
    val GB18030: Charset = Charset.forName("GB18030")
    val GBK: Charset = Charset.forName("GBK")

    fun book(bid: String) = "$BASE/book/$bid.htm"
    fun search(query: String) = "$BASE/modules/article/search.php?searchtype=articlename&searchkey=" +
        gbkPercentEncode(query)
    fun searchAuthor(query: String) = "$BASE/modules/article/search.php?searchtype=author&searchkey=" +
        gbkPercentEncode(query)
    fun toplist(sort: String) = "$BASE/modules/article/toplist.php?sort=$sort"
    fun tagCloud() = "$BASE/modules/article/tags.php"

    /**
     * 标签结果页。[name] 用 GBK 百分号编码（站点 GB18030）；
     * [completedOnly] 对应 v=2「只看已完结」，[popular] 对应 v=1「按热门」。
     */
    fun tag(name: String, page: Int = 1, completedOnly: Boolean = false, popular: Boolean = false): String = buildString {
        append(BASE).append("/modules/article/tags.php?t=").append(gbkPercentEncode(name))
        if (page > 1) append("&page=").append(page)
        when {
            popular -> append("&v=1")
            completedOnly -> append("&v=2")
        }
    }

    /** 完结全本列表（articlelist.php?fullflag=1），20 本一页。 */
    fun completedList(page: Int = 1): String = buildString {
        append(BASE).append("/modules/article/articlelist.php?fullflag=1")
        if (page > 1) append("&page=").append(page)
    }
    fun sugoi(year: Int) = "$BASE/zt/sugoi/$year.php"
    fun packed(bid: String, cid: String) = "https://dl.wenku8.com/pack.php?aid=$bid&vid=$cid"
    fun packedTxt(bid: String, cid: String) = "https://dl.wenku8.com/packtxt.php?aid=$bid&vid=$cid"

    /** wenku8 搜索参数是 GBK 字节的百分号编码。 */
    fun gbkPercentEncode(text: String): String =
        text.toByteArray(GBK).joinToString("") { b ->
            "%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }

    /** 章节 URL 形如 …/novel/{cat}/{bid}/{cid}.htm 或 …/novel/{bid}/{cid}.htm */
    fun idsOfChapter(chapterUrl: String): Pair<String, String> {
        val m = Regex("/novel/(?:\\d+/)?(\\d+)/(\\d+)\\.htm").find(chapterUrl)
            ?: throw IllegalArgumentException("无法识别 wenku8 章节 URL：$chapterUrl")
        return m.groupValues[1] to m.groupValues[2]
    }

    fun bookIdOf(bookUrl: String): String =
        Regex("/book/(\\d+)\\.htm").find(bookUrl)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法识别 wenku8 书籍 URL：$bookUrl")
}

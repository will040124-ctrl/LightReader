package com.will.lightreader.source

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.BrowsePage
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.domain.TagGroup

/**
 * 站点适配契约。所有方法失败时抛 [SourceError]，正常时返回（列表可为空，空 ≠ 错误）：
 * - 网络/限流 → [SourceError.Network] / [SourceError.RateLimited]
 * - Cloudflare / 需登录 → [SourceError.ChallengeRequired] / [SourceError.AuthRequired]
 * - 站点结构变化 → [SourceError.ParseChanged]；作品下架 → [SourceError.TakenDown]
 */
interface NovelSource {
    val id: String
    val displayName: String
    val discovery: SourceDiscovery? get() = SourceDiscovery()
    val supportsBulkDownload: Boolean get() = true

    suspend fun prepareDiscovery(): SourceDiscovery? = discovery

    suspend fun discoveryHome(): BrowsePage = BrowsePage(emptyList(), false)

    /** 关键词搜索书名/作者。 */
    suspend fun search(query: String): List<BookBrief>
    /** Optional paged search. Existing novel sources keep their single-page behavior. */
    suspend fun searchPage(query:String,page:Int):BrowsePage = if(page==1)BrowsePage(search(query),false) else BrowsePage(emptyList(),false)

    /**
     * 该作者的其他作品。[authorUrl] 为详情页解析出的作者主页链接（可能为空），
     * [authorName] 为作者名（用于无作者页的书源退化为作者名搜索）。
     * 返回该作者的作品列表，可为空（空 ≠ 错误）。
     */
    suspend fun authorWorks(authorName: String, authorUrl: String?): List<BookBrief>

    /**
     * 榜单/发现。kind 语法（见 [PopularKinds]）：
     * - linovelib：榜单名，如 `lastupdate` / `postdate` / `monthvisit` / `weekvisit`
     * - wenku8：榜单名（`dayvisit` / `monthvisit` / `allvisit` / `goodnum` / `postdate`）
     *   或 `"sugoi:<年份>"` 表示轻小说大赏（如 `sugoi:2026`）
     */
    suspend fun popular(kind: String): List<BookBrief>
    suspend fun popularPage(kind: String, page: Int): BrowsePage =
        if (page == 1) BrowsePage(popular(kind), false) else BrowsePage(emptyList(), false)

    /** 按作者名检索；书源不支持时回退到普通关键词搜索。 */
    suspend fun searchByAuthor(query: String): List<BookBrief> = search(query)

    /** 标签云（分组）。书源没有标签体系时返回空表。 */
    suspend fun tags(): List<TagGroup> = emptyList()
    fun detailTagRoute(tag: String): String = tag

    /** 按标签翻页浏览；[completedOnly] 只看已完结，[popular] 按热门排序。 */
    suspend fun tagBooks(tag: String, page: Int, completedOnly: Boolean, popular: Boolean = false): BrowsePage =
        BrowsePage(emptyList(), false)

    /** 完结作品翻页浏览。 */
    suspend fun completedBooks(page: Int): BrowsePage = BrowsePage(emptyList(), false)

    suspend fun bookDetail(bookUrl: String): BookDetail

    /** 全书目录（按阅读顺序），已按分卷标注 [ChapterRef.volume]。 */
    suspend fun chapterList(bookUrl: String): List<ChapterRef>

    /** 章节正文（含长章分页合并、乱序还原），返回渲染就绪的块列表。 */
    suspend fun chapterContent(chapterUrl: String): ChapterContent
}

/** [NovelSource.popular] 的 kind 取值表，UI 与 Source 实现共用，避免魔法字符串漂移。 */
object PopularKinds {
    val LINOVELIB = listOf("lastupdate", "postdate", "monthvisit", "weekvisit")
    val WENKU8 = listOf("dayvisit", "monthvisit", "allvisit", "goodnum", "postdate")
    const val SUGOI_PREFIX = "sugoi:"
    fun sugoi(year: Int): String = SUGOI_PREFIX + year
    fun sugoiYear(kind: String): Int? =
        if (kind.startsWith(SUGOI_PREFIX)) kind.removePrefix(SUGOI_PREFIX).toIntOrNull() else null
}

sealed class SourceError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ContentUnavailable(val reason:String):SourceError(reason)
    class ComicRoute(val reason: String, val deniedHost: String? = null) : SourceError(reason)
    class Network(cause: Throwable) : SourceError("网络错误：${cause.message ?: cause.javaClass.simpleName}", cause)
    class ChallengeRequired(val url: String, val accessBlocked: Boolean = false) :
        SourceError(if (accessBlocked) "站点拒绝访问：$url" else "站点防护拦截：$url")
    class AuthRequired(val url: String) : SourceError("需要登录/验证：$url")
    class RateLimited(val url: String) : SourceError("请求过于频繁：$url")
    class ParseChanged(val url: String, val detail: String) : SourceError("站点结构可能改版：$url（$detail）")
    class TakenDown(val url: String) : SourceError("作品已下架：$url")
    class Unsupported(val url: String, val why: String = "该书籍来自已停用的旧版书源") : SourceError("$why：$url")
}

package com.will.lightreader.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookBrief(
    val sourceId: String,
    val bookId: String,
    val bookUrl: String,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    /** 列表页顺带解析的作品标签（标签 / 完结 / 榜单通用）。 */
    val tags: List<String> = emptyList(),
    /** 连载状态：已完结 / 连载中；未知留空。 */
    val status: String = "",
)

/** 标签分组（wenku8 的 Tags 云集按 日常系 / 幻想系 / … 分组）。 */
data class TagGroup(val name: String, val tags: List<String>, val routes: Map<String, String> = emptyMap())

/** 一页可继续翻的浏览结果。 */
data class BrowsePage(val items: List<BookBrief>, val hasMore: Boolean, val sections: List<BrowseSection> = emptyList())

data class BrowseSection(val title: String, val items: List<BookBrief>, val route: String? = null)

@Serializable
data class BookDetail(
    val sourceId: String,
    val bookId: String,
    val bookUrl: String,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    /** 作者主页链接（有则详情页作者可点 → 该作者全部作品）。无独立作者页的书源留空。 */
    val authorUrl: String? = null,
    val intro: String = "",
    val tags: List<String> = emptyList(),
    val status: String = "",
    val updatedAt: String = "",
    val latestChapter: String = "",
)

@Serializable
data class ChapterRef(
    val chapterUrl: String,
    val title: String,
    val volume: String = "",
    val chapterId: String = "",
)

/** 注音注记：base 为汉字原文，ruby 为假名注音。M1 解析层暂不拆分，整段文本进 Paragraph.text。 */
@Serializable
data class RubySpan(val base: String, val ruby: String)

@Serializable
sealed interface Block {
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val text: String, val ruby: List<RubySpan> = emptyList()) : Block

    @Serializable
    @SerialName("image")
    data class Image(val ref: String, val caption: String? = null, val fullWidth: Boolean = true) : Block

    @Serializable
    @SerialName("divider")
    data object Divider : Block
}

@Serializable
data class ChapterContent(
    val chapterUrl: String,
    val title: String,
    val blocks: List<Block>,
    val fixedPage: FixedPage? = null,
    val comicPages: List<ComicPage> = emptyList(),
)

/** Original-layout page. Null PDF index means an extracted comic image. */
@Serializable
data class FixedPage(val ref: String, val pdfIndex: Int? = null)

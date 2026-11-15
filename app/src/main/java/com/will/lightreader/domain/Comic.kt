package com.will.lightreader.domain

import kotlinx.serialization.Serializable
import java.net.URI

/** Stable identity is separate from a potentially expiring image URL. Credentials are not cached. */
@Serializable
data class ComicPage(val id: String, val url: String,
    @kotlinx.serialization.Transient val localRef:String?=null,
    @kotlinx.serialization.Transient val requestHeaders:Map<String,String> = emptyMap(),
    @kotlinx.serialization.Transient val imageTransform: String? = null) {
    init {
        require(id.isNotBlank()) { "漫画页缺少标识" }
        val uri = URI(url)
        require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "漫画图片地址无效" }
    }
}

fun validateComicPages(pages: List<ComicPage>): List<ComicPage> {
    require(pages.isNotEmpty() && pages.size <= 2000) { "漫画页数无效" }
    require(pages.map { it.id }.distinct().size == pages.size) { "漫画页标识重复" }
    return pages
}

fun restoreComicPage(pages: List<ComicPage>, savedId: String?, fallbackIndex: Int): Int {
    if (pages.isEmpty()) return 0
    return pages.indexOfFirst { it.id == savedId }.takeIf { it >= 0 }
        ?: fallbackIndex.coerceIn(pages.indices)
}

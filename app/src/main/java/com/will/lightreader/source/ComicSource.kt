package com.will.lightreader.source

import com.will.lightreader.domain.*

/** Separate comic contract. The bridge only reuses existing shelf/detail navigation. */
interface ComicSource {
    val id: String
    val displayName: String
    val discovery: SourceDiscovery? get() = null
    suspend fun prepareDiscovery(): SourceDiscovery? = discovery
    suspend fun discoveryHome(): BrowsePage = BrowsePage(emptyList(), false)
    suspend fun popularPage(kind: String, page: Int): BrowsePage = BrowsePage(emptyList(), false)
    suspend fun tags(): List<TagGroup> = emptyList()
    fun detailTagRoute(tag: String): String = tag
    suspend fun tagBooks(route: String, page: Int, popular: Boolean): BrowsePage = BrowsePage(emptyList(), false)
    suspend fun search(query: String): List<BookBrief>
    suspend fun searchPage(query:String,page:Int):BrowsePage = if(page==1)BrowsePage(search(query),false) else BrowsePage(emptyList(),false)
    suspend fun detail(bookUrl: String): BookDetail
    suspend fun episodes(bookUrl: String): List<ChapterRef>
    suspend fun pages(episodeUrl: String): List<ComicPage>
    /** Resolve session headers at request time; never persist them with chapter JSON. */
    fun imageHeaders(episodeUrl: String, page: ComicPage): Map<String, String> = emptyMap()
}

class ComicSourceBridge(val comic: ComicSource) : NovelSource {
    override val id get() = comic.id
    override val displayName get() = comic.displayName
    override val discovery: SourceDiscovery? get() = comic.discovery
    override val supportsBulkDownload get() = comic is com.will.lightreader.source.script.ScriptComicSource
    override suspend fun search(query: String) = comic.search(query)
    override suspend fun searchPage(query:String,page:Int)=comic.searchPage(query,page)
    override suspend fun authorWorks(authorName: String, authorUrl: String?) = comic.search(authorName)
    override suspend fun prepareDiscovery() = comic.prepareDiscovery()
    override suspend fun discoveryHome() = comic.discoveryHome()
    override suspend fun popular(kind: String): List<BookBrief> = comic.popularPage(kind, 1).items
    override suspend fun popularPage(kind: String, page: Int) = comic.popularPage(kind, page)
    override suspend fun tags() = comic.tags()
    override fun detailTagRoute(tag: String) = comic.detailTagRoute(tag)
    override suspend fun tagBooks(tag: String, page: Int, completedOnly: Boolean, popular: Boolean) =
        if (completedOnly) BrowsePage(emptyList(), false) else comic.tagBooks(tag, page, popular)
    override suspend fun bookDetail(bookUrl: String) = comic.detail(bookUrl)
    override suspend fun chapterList(bookUrl: String) = comic.episodes(bookUrl)
    override suspend fun chapterContent(chapterUrl: String) = ChapterContent(
        chapterUrl, "", emptyList(), comicPages = validateComicPages(comic.pages(chapterUrl)))
}

package com.will.lightreader.source

import com.will.lightreader.domain.*

/** Offline records remain readable after a source is disabled or removed. */
class UnavailableSource(override val id:String):NovelSource {
    override val displayName="已停用的源"
    override val discovery:SourceDiscovery?=null
    override val supportsBulkDownload=false
    private fun missing():Nothing=throw SourceError.Unsupported(id,"源已停用或移除，请先启用源或阅读已下载章节")
    override suspend fun search(query:String):List<BookBrief> = missing()
    override suspend fun authorWorks(authorName:String,authorUrl:String?):List<BookBrief> = missing()
    override suspend fun popular(kind:String):List<BookBrief> = missing()
    override suspend fun bookDetail(bookUrl:String):BookDetail = missing()
    override suspend fun chapterList(bookUrl:String):List<ChapterRef> = missing()
    override suspend fun chapterContent(chapterUrl:String):ChapterContent = missing()
}

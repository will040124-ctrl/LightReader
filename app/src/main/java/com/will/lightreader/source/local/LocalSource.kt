package com.will.lightreader.source.local

import com.will.lightreader.data.LibraryRepository
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.source.NovelSource
import com.will.lightreader.source.SourceError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 本地书随书元数据（简介/语言/格式），书名作者进度仍走 books 表。 */
@Serializable
data class LocalMeta(
    val intro: String = "",
    val language: String = "",
    val format: String = "",
)

/** [meta.json] 与正文缓存分层：清缓存不丢简介，删书整目录一起删。 */
object LocalMetaStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun read(dir: File): LocalMeta = runCatching {
        json.decodeFromString(LocalMeta.serializer(), File(dir, "meta.json").readText())
    }.getOrDefault(LocalMeta())

    fun write(dir: File, meta: LocalMeta) {
        dir.mkdirs()
        runCatching { File(dir, "meta.json").writeText(json.encodeToString(LocalMeta.serializer(), meta)) }
    }
}

/**
 * 本地导入书源：正文与目录在导入时已经落进 Room + ChapterCache，
 * 所以这里只做「读取 + 转成站点书源的同一套结构」，阅读页完全不用改。
 */
class LocalSource(
    private val repo: LibraryRepository,
    private val root: File,
) : NovelSource {
    override val id: String = LocalIds.SOURCE
    override val displayName: String = "本地导入"
    override val discovery: com.will.lightreader.source.SourceDiscovery? = null

    override suspend fun search(query: String): List<BookBrief> = emptyList()

    override suspend fun authorWorks(authorName: String, authorUrl: String?): List<BookBrief> = emptyList()

    override suspend fun popular(kind: String): List<BookBrief> = emptyList()

    override suspend fun bookDetail(bookUrl: String): BookDetail {
        val bookId = LocalIds.bookIdOf(bookUrl)
        val b = repo.book(LibraryRepository.bookKey(id, bookId))
            ?: throw SourceError.Unsupported(bookUrl, "这本本地书已被移除")
        val meta = LocalMetaStore.read(File(root, bookId))
        return BookDetail(
            sourceId = id, bookId = bookId, bookUrl = b.bookUrl,
            title = b.title, author = b.author, coverUrl = b.coverUrl,
            intro = meta.intro,
            tags = listOfNotNull(meta.format.ifBlank { null }, "本地"),
            status = "本地",
        )
    }

    override suspend fun chapterList(bookUrl: String): List<ChapterRef> {
        val key = LibraryRepository.bookKey(id, LocalIds.bookIdOf(bookUrl))
        return repo.chapters(key).map { ChapterRef(it.key, it.title, it.volume) }
    }

    override suspend fun chapterContent(chapterUrl: String): ChapterContent =
        repo.chapterContent(chapterUrl)
            ?: throw SourceError.Unsupported(chapterUrl, "本地正文文件缺失，请重新导入")
}

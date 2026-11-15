package com.will.lightreader.data

import android.content.Context
import androidx.room.withTransaction
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.serialization.json.Json

data class CachedBookDetail(val detail: BookDetail, val detailAt: Long, val catalogueAt: Long) {
    fun detailIsFresh(now: Long = System.currentTimeMillis()) = isFresh(detailAt, now)
    fun catalogueIsFresh(now: Long = System.currentTimeMillis()) = isFresh(catalogueAt, now)

    private fun isFresh(savedAt: Long, now: Long) = savedAt > 0 && now - savedAt in 0..86_400_000L
}

/**
 * 书架/离线/记忆/书签的统一入口。
 * - 书架：books 表（收藏 + 最近阅读）
 * - 离线：chapters 表 + ChapterCache 文件（正文 JSON）
 * - 记忆：positions 表（锚点：块索引 + 偏移比例）
 */
class LibraryRepository(context: Context) {
    private val db = LightReaderDb.get(context)
    private val cache = ChapterCache(File(context.filesDir, ChapterCache.CHAPTER_DIR))
    val downloadTasks=BookDownloadTasks()
    val downloadScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
    private val comicOffline=ComicOfflineStore(File(context.filesDir,"comic-offline"),validateImage={ bytes ->
        val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=100_000_000L
    })
    private val importsDir = File(context.filesDir, com.will.lightreader.source.local.LocalIds.IMPORT_DIR)

    // ---------- 书架 ----------
    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()

    suspend fun book(key: String): BookEntity? = db.bookDao().get(key)

    suspend fun cachedBookDetail(key: String): CachedBookDetail? {
        val row = db.bookDao().cachedDetail(key) ?: return null
        val detail = runCatching { Json.decodeFromString<BookDetail>(row.detailJson) }.getOrNull() ?: return null
        return CachedBookDetail(detail, row.detailAt, row.catalogueAt)
    }

    suspend fun expireComicDetails() = db.bookDao().expireComicDetails()

    suspend fun cacheBookDetail(key: String, detail: BookDetail, catalogueReady: Boolean = false, onlyIfMissing: Boolean = false) {
        if (detail.sourceId == com.will.lightreader.source.local.LocalIds.SOURCE) return
        db.withTransaction {
            val dao = db.bookDao()
            if (dao.get(key) == null) return@withTransaction
            val old = cachedBookDetail(key)
            if (onlyIfMissing && old != null) return@withTransaction
            val now = System.currentTimeMillis()
            dao.putCachedDetail(BookDetailCacheEntity(key, Json.encodeToString(BookDetail.serializer(), detail),
                now, if (catalogueReady) now else old?.catalogueAt ?: 0L))
        }
    }

    suspend fun saveBook(brief: BookBrief, now: Long = System.currentTimeMillis()) {
        val key = bookKey(brief.sourceId, brief.bookId)
        val old = db.bookDao().get(key)
        db.bookDao().upsert(
            BookEntity(
                key = key, sourceId = brief.sourceId, bookId = brief.bookId, bookUrl = brief.bookUrl,
                title = brief.title, author = brief.author, coverUrl = brief.coverUrl,
                isFavorite = old?.isFavorite ?: true, addedAt = old?.addedAt ?: now, lastReadAt = old?.lastReadAt ?: 0L,
            )
        )
    }

    /** 彻底删除一本书：书 + 阅读进度 + 章节记录 + 书签/摘抄 + 下载缓存文件。 */
    suspend fun removeBook(key: String) {
        downloadTasks.cancelAndJoin(key)
        // P0 修复：多表删除包事务，避免中途异常留下孤儿章节/进度
        db.withTransaction {
            clearBookDownloads(key)           // 先删离线文件 + 指针
            db.chapterDao().deleteByBook(key) // 章节记录
            db.positionDao().delete(key)      // 阅读进度
            db.bookmarkDao().deleteByBook(key)
            db.excerptDao().deleteByBook(key)
            db.bookDao().deleteCachedDetail(key)
            db.bookDao().delete(key)          // 收藏书本体
        }
        // 本地导入书：连同源文件解析产物（封面/插图/元数据）一起删
        if (com.will.lightreader.source.local.LocalIds.isLocal(key)) {
            val id = key.substringAfter(':')
            runCatching { File(importsDir, id).deleteRecursively() }
        }
    }

    /** 清空书架（全部收藏书）。 */
    suspend fun clearShelf() {
        db.withTransaction {
            db.bookDao().clearCachedDetails()
            db.bookDao().deleteAll()
        }
    }

    /** 清空浏览记录（阅读位置锚点）。 */
    suspend fun clearHistory() {
        db.positionDao().deleteAll()
    }

    suspend fun touchRead(key: String, now: Long = System.currentTimeMillis()) =
        db.bookDao().touch(key, now)

    // ---------- 章节（含离线） ----------
    suspend fun saveChapters(bookKey: String, refs: List<ChapterRef>) {
        val entities = refs.mapIndexed { i, r ->
            ChapterEntity(key = r.chapterUrl, bookKey = bookKey, title = r.title, volume = r.volume, idx = i)
        }
        // P0 修复：批量插入 + 逐条更新包事务，避免中途异常留下不一致目录
        db.withTransaction {
            // 新章插入 + 旧章仅更新元数据：contentPath 下载指针绝不被目录刷新冲掉
            db.chapterDao().insertNew(entities)
            entities.forEach { db.chapterDao().updateMeta(it.key, it.title, it.volume, it.idx) }
        }
    }

    suspend fun chapters(bookKey: String): List<ChapterEntity> = db.chapterDao().byBook(bookKey)

    /** Commit imported metadata, page/chapter directory and cache pointers as one database unit. */
    suspend fun saveImportedBook(brief: BookBrief, refs: List<ChapterRef>, contents: List<ChapterContent>) {
        require(refs.size == contents.size)
        db.withTransaction {
            saveBook(brief)
            saveChapters(bookKey(brief.sourceId, brief.bookId), refs)
            contents.forEach { saveChapterContent(it.chapterUrl, it) }
        }
    }

    /** 书架刷新批量快照：一次取多本书的章节 + 位置 + 下载数，避免 N+1 查询。 */
    suspend fun shelfSnapshot(bookKeys: List<String>): ShelfSnapshot {
        if (bookKeys.isEmpty()) return ShelfSnapshot(emptyMap(), emptyMap(), emptyMap())
        val chapters = db.chapterDao().byBooks(bookKeys).groupBy { it.bookKey }
        val positions = db.positionDao().byBooks(bookKeys).associateBy { it.bookKey }
        val downloaded = chapters.mapValues { (_, list) -> list.count { it.contentPath != null } }
        return ShelfSnapshot(chapters, positions, downloaded)
    }

    suspend fun downloadedChapters(bookKey: String): List<ChapterEntity> {
        val chapters=db.chapterDao().downloadedOf(bookKey)
        return chapters.filter { chapter ->
            if(bookKey.startsWith("comic-js-") && comicOffline.read(chapter.key,false)==null) {
                db.chapterDao().clearDownloadedOne(chapter.key);false
            } else true
        }
    }
    suspend fun comicChapterContent(chapterUrl:String):ChapterContent? {
        val content=comicOffline.read(chapterUrl)
        if(content==null && chapterUrl.contains("/comic-js-"))db.chapterDao().clearDownloadedOne(chapterUrl)
        return content
    }
    fun hasOfflineComicChapter(chapterUrl: String): Boolean = comicOffline.read(chapterUrl, verifyHashes = false) != null
    suspend fun downloadComicChapter(chapterUrl:String,content:ChapterContent,source:com.will.lightreader.source.script.ScriptComicSource,onPage:(Int,Int)->Unit={_,_->}) {
        require(content.chapterUrl==chapterUrl)
        val headers=content.comicPages.associate { it.id to source.imageHeaders(chapterUrl,it) }
        comicOffline.download(content,onPage) { page -> source.downloadImage(page,headers[page.id].orEmpty()) }
        val path=cache.write(chapterUrl,content)
        db.chapterDao().setDownloaded(chapterUrl,path,System.currentTimeMillis())
    }

    /** 离线保存一章正文；返回缓存路径。 */
    suspend fun saveChapterContent(chapterUrl: String, content: ChapterContent, now: Long = System.currentTimeMillis()): String {
        val record = db.chapterDao().get(chapterUrl)
        if (com.will.lightreader.domain.TextIntegrity.titleOnly(content, record?.title.orEmpty()))
            throw java.io.IOException("正文尚未完整载入，已保留原缓存")
        val path = cache.write(chapterUrl, content)
        record?.takeIf { content.comicPages.isEmpty() }?.let {
            db.chapterDao().setDownloaded(chapterUrl, path, now)
        }
        return path
    }

    /** 读一章正文：先离线缓存（指针 + 按 URL 兜底双查），miss 返回 null（调用方走网络）。 */
    suspend fun chapterContent(chapterUrl: String): ChapterContent? {
        comicChapterContent(chapterUrl)?.let { return it }
        val record = db.chapterDao().get(chapterUrl)
        val content = record?.contentPath?.let { cache.read(it) }?.takeIf { it.chapterUrl==chapterUrl } ?: cache.readByUrl(chapterUrl)
        return content?.takeUnless { com.will.lightreader.domain.TextIntegrity.titleOnly(it, record?.title.orEmpty()) }
    }

    suspend fun chapterNeedsRepair(chapterUrl: String): Boolean = cache.needsRepair(chapterUrl) ||
        cache.readByUrl(chapterUrl)?.let { com.will.lightreader.domain.TextIntegrity.titleOnly(it, db.chapterDao().get(chapterUrl)?.title.orEmpty()) } == true

    // ---------- 阅读位置（锚点记忆） ----------
    suspend fun position(bookKey: String): PositionEntity? = db.positionDao().get(bookKey)

    suspend fun savePosition(
        bookKey: String,
        chapterUrl: String,
        chapterTitle: String,
        blockIndex: Int,
        offsetRatio: Float,
        now: Long = System.currentTimeMillis(),
    ) {
        db.positionDao().upsert(
            PositionEntity(bookKey, chapterUrl, chapterTitle, blockIndex, offsetRatio, now)
        )
    }

    // ---------- 书签 ----------
    fun observeBookmarks(bookKey: String): Flow<List<BookmarkEntity>> =
        db.bookmarkDao().observeByBook(bookKey)

    suspend fun addBookmark(bookKey: String, chapterUrl: String, blockIndex: Int, text: String) =
        db.bookmarkDao().add(
            BookmarkEntity(
                bookKey = bookKey, chapterUrl = chapterUrl, blockIndex = blockIndex,
                text = text, createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun removeBookmark(id: Long) = db.bookmarkDao().delete(id)

    // ---------- 摘抄 ----------
    fun observeExcerpts(bookKey: String): Flow<List<ExcerptEntity>> =
        db.excerptDao().observeByBook(bookKey)

    suspend fun addExcerpt(
        bookKey: String,
        chapterUrl: String,
        chapterTitle: String,
        blockIndex: Int,
        text: String,
        now: Long = System.currentTimeMillis(),
    ) = db.excerptDao().add(
        ExcerptEntity(
            bookKey = bookKey, chapterUrl = chapterUrl, chapterTitle = chapterTitle,
            blockIndex = blockIndex, text = text, createdAt = now,
        )
    )

    suspend fun removeExcerpt(id: Long) = db.excerptDao().delete(id)

    /** 清空离线下载缓存（正文文件 + 数据库指针），书架与进度不受影响。 */
    suspend fun clearDownloads() {
        // 本地导入书的正文就是书本身，清了就读不了了 —— 只清站点书源的下载缓存
        val localKeys = db.bookDao().keysOf(com.will.lightreader.source.local.LocalIds.SOURCE).toSet()
        db.withTransaction {
            for (ch in db.chapterDao().allDownloaded()) {
                if (ch.bookKey in localKeys) continue
                ch.contentPath?.let { cache.delete(it) }
                db.chapterDao().clearDownloadedOne(ch.key)
            }
        }
    }

    /** 删除单章离线缓存（文件 + 指针）。 */
    suspend fun removeChapterContent(chapterUrl: String) {
        db.chapterDao().get(chapterUrl)?.let { downloadTasks.cancelAndJoin(it.bookKey);downloadTasks.forget(it.bookKey) }
        val path = db.chapterDao().get(chapterUrl)?.contentPath
        db.chapterDao().clearDownloadedOne(chapterUrl)
        path?.let { cache.delete(it) }
        comicOffline.delete(chapterUrl)
    }

    /** 删除一本书的全部离线缓存。 */
    suspend fun clearBookDownloads(bookKey: String) {
        if (com.will.lightreader.source.local.LocalIds.isLocal(bookKey)) return  // 本地书正文不可清
        downloadTasks.cancelAndJoin(bookKey)
        downloadTasks.forget(bookKey)
        db.withTransaction {
            for (ch in chapters(bookKey)) {
                ch.contentPath?.let { cache.delete(it) }
                comicOffline.delete(ch.key)
            }
            db.chapterDao().clearDownloadedOfBook(bookKey)
        }
    }

    /** 每书下载概览：章数 + 占用字节（全局下载管理用）。 */
    data class BookDownloadStat(
        val bookKey: String,
        val title: String,
        val chapters: Int,
        val bytes: Long,
    )

    /**
     * 重链接断指针的下载记录：目录刷新曾用 REPLACE 冲掉 contentPath，
     * 但缓存文件还在 —— 扫一遍把文件在的章指针补回来（自愈）。
     */
    suspend fun relinkDownloads(): Int {
        var fixed = 0
        for (ch in db.chapterDao().unlinked()) {
            if (cache.offlineCompleteByUrl(ch.key) || comicOffline.read(ch.key,false)!=null) {
                db.chapterDao().setDownloaded(ch.key, cache.pathFor(ch.key), System.currentTimeMillis())
                fixed++
            }
        }
        return fixed
    }

    suspend fun downloadsOverview(): List<BookDownloadStat> {
        val books = db.bookDao().observeAll().first()
        return books.mapNotNull { b ->
            val chs = downloadedChapters(b.key)
            val bytes = chapters(b.key).sumOf { ch -> (ch.contentPath?.let { cache.sizeOf(it) } ?: 0L)+comicOffline.size(ch.key) }
            if(chs.isEmpty() && bytes==0L)return@mapNotNull null
            BookDownloadStat(b.key, b.title, chs.size, bytes)
        }
    }

    companion object {
        fun bookKey(sourceId: String, bookId: String) = "$sourceId:$bookId"
    }
}

/** 书架批量快照：bookKey → 章节列表 / 阅读位置 / 已下载章数。 */
data class ShelfSnapshot(
    val chaptersByBook: Map<String, List<ChapterEntity>>,
    val positionByBook: Map<String, PositionEntity>,
    val downloadedByBook: Map<String, Int>,
)

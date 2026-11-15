package com.will.lightreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM book_detail_cache")
    suspend fun detailSnapshot(): List<BookDetailCacheEntity>
    @Query("SELECT * FROM book_detail_cache WHERE bookKey = :key")
    suspend fun cachedDetail(key: String): BookDetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCachedDetail(detail: BookDetailCacheEntity)

    @Query("DELETE FROM book_detail_cache WHERE bookKey = :key")
    suspend fun deleteCachedDetail(key: String)

    @Query("DELETE FROM book_detail_cache")
    suspend fun clearCachedDetails()

    @Query("UPDATE book_detail_cache SET detailAt = 0, catalogueAt = 0 " +
        "WHERE bookKey IN (SELECT `key` FROM books WHERE sourceId LIKE 'comic-js-%')")
    suspend fun expireComicDetails()

    @Query("SELECT * FROM books")
    suspend fun snapshot(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE `key` = :key")
    suspend fun get(key: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET lastReadAt = :at WHERE `key` = :key")
    suspend fun touch(key: String, at: Long)

    @Query("DELETE FROM books WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    /** 某书源的全部书架 key（清缓存时用来跳过本地导入书）。 */
    @Query("SELECT `key` FROM books WHERE sourceId = :sourceId")
    suspend fun keysOf(sourceId: String): List<String>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters")
    suspend fun snapshot(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE bookKey = :bookKey ORDER BY idx")
    fun observeByBook(bookKey: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookKey = :bookKey ORDER BY idx")
    suspend fun byBook(bookKey: String): List<ChapterEntity>

    /** 批量：一次取多本书的章节（书架刷新用，避免 N+1）。 */
    @Query("SELECT * FROM chapters WHERE bookKey IN (:bookKeys) ORDER BY idx")
    suspend fun byBooks(bookKeys: List<String>): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE `key` = :key")
    suspend fun get(key: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    /** 新章插入；已存在的不动（保护 contentPath 下载指针不被目录刷新冲掉）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(chapters: List<ChapterEntity>)

    /** 仅更新目录元数据（标题/分卷/序号），绝不触碰 contentPath/downloadedAt。 */
    @Query("UPDATE chapters SET title = :title, volume = :volume, idx = :idx WHERE `key` = :key")
    suspend fun updateMeta(key: String, title: String, volume: String, idx: Int)

    @Query("UPDATE chapters SET contentPath = :path, downloadedAt = :at WHERE `key` = :key")
    suspend fun setDownloaded(key: String, path: String, at: Long)

    @Query("SELECT * FROM chapters WHERE contentPath IS NOT NULL AND bookKey = :bookKey ORDER BY idx")
    suspend fun downloadedOf(bookKey: String): List<ChapterEntity>

    @Query("UPDATE chapters SET contentPath = NULL, downloadedAt = NULL")
    suspend fun clearDownloaded()

    @Query("UPDATE chapters SET contentPath = NULL, downloadedAt = NULL WHERE `key` = :key")
    suspend fun clearDownloadedOne(key: String)

    @Query("UPDATE chapters SET contentPath = NULL, downloadedAt = NULL WHERE bookKey = :bookKey")
    suspend fun clearDownloadedOfBook(bookKey: String)

    @Query("SELECT * FROM chapters WHERE contentPath IS NULL")
    suspend fun unlinked(): List<ChapterEntity>

    /** 全部已离线章节（清缓存时按书区分，本地导入书属于「正文本体」不能清）。 */
    @Query("SELECT * FROM chapters WHERE contentPath IS NOT NULL")
    suspend fun allDownloaded(): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE bookKey = :bookKey")
    suspend fun deleteByBook(bookKey: String)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions")
    fun observeAll(): Flow<List<PositionEntity>>
    @Query("SELECT * FROM positions")
    suspend fun snapshot(): List<PositionEntity>

    @Query("SELECT * FROM positions WHERE bookKey = :bookKey")
    suspend fun get(bookKey: String): PositionEntity?

    /** 批量：一次取多本书的阅读位置（书架刷新用，避免 N+1）。 */
    @Query("SELECT * FROM positions WHERE bookKey IN (:bookKeys)")
    suspend fun byBooks(bookKeys: List<String>): List<PositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("DELETE FROM positions")
    suspend fun deleteAll()

    @Query("DELETE FROM positions WHERE bookKey = :bookKey")
    suspend fun delete(bookKey: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks")
    suspend fun snapshot(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE bookKey = :bookKey ORDER BY createdAt DESC")
    fun observeByBook(bookKey: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun add(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookKey = :bookKey")
    suspend fun deleteByBook(bookKey: String)
}

@Dao
interface ExcerptDao {
    @Query("SELECT * FROM excerpts")
    suspend fun snapshot(): List<ExcerptEntity>

    @Query("SELECT * FROM excerpts WHERE bookKey = :bookKey ORDER BY createdAt DESC")
    fun observeByBook(bookKey: String): Flow<List<ExcerptEntity>>

    @Insert
    suspend fun add(excerpt: ExcerptEntity): Long

    @Query("DELETE FROM excerpts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM excerpts WHERE bookKey = :bookKey")
    suspend fun deleteByBook(bookKey: String)
}

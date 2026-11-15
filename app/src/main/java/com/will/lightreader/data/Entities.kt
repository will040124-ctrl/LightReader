package com.will.lightreader.data

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 书（书架）。key = "sourceId:bookId"，跨源不去重。 */
@Serializable
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val key: String,
    val sourceId: String,
    val bookId: String,
    val bookUrl: String,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    val isFavorite: Boolean = true,
    val addedAt: Long = 0L,
    val lastReadAt: Long = 0L,
)

/** Separate from the observed shelf rows so warming a synopsis does not rebuild the shelf. */
@Serializable
@Entity(tableName = "book_detail_cache")
data class BookDetailCacheEntity(
    @PrimaryKey val bookKey: String,
    val detailJson: String,
    val detailAt: Long,
    val catalogueAt: Long,
)

/** 章节（元数据 + 离线缓存指针）。key = chapterUrl。 */
@Serializable
@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val key: String,          // chapterUrl
    val bookKey: String,
    val title: String,
    val volume: String = "",
    val idx: Int = 0,
    val contentPath: String? = null,      // 相对 filesDir 的正文 JSON 路径；null = 未离线
    val downloadedAt: Long? = null,
)

/** 阅读位置（锚点式记忆：块索引 + 偏移比例，抗图片加载漂移）。 */
@Serializable
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val bookKey: String,
    val chapterUrl: String,
    val chapterTitle: String = "",
    val blockIndex: Int = 0,
    val offsetRatio: Float = 0f,
    val updatedAt: Long = 0L,
)

/** 书签。 */
@Serializable
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookKey: String,
    val chapterUrl: String,
    val blockIndex: Int = 0,
    val text: String = "",
    val createdAt: Long = 0L,
)

/** 摘抄：整段正文 + 章节锚点，点击可跳回原文。 */
@Serializable
@Entity(tableName = "excerpts")
data class ExcerptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookKey: String,
    val chapterUrl: String,
    val chapterTitle: String = "",
    val blockIndex: Int = 0,
    val text: String,
    val createdAt: Long = 0L,
)

package com.will.lightreader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class BackupPreferences(val fontSize: Int = 18, val lineHeight: Float = 1.7f,
    val serif: Boolean = true, val paper: String = "follow", val twoPage: Boolean = false,
    val turnStyle: String = "slide", val shelfLayout: String = "AUTO", val appearance: String = "SYSTEM",
    val comicRtl: Boolean = false, val anchors: Map<String,String> = emptyMap(),
    // Null means an older backup never specified this choice. Preserve current privacy/preferences.
    val shelfArtwork: String? = null, val shelfWheel: Boolean? = null, val widgetDetails: Boolean? = null,
    val immersive: Boolean? = null, val hideStatusBar: Boolean? = null,
    val defaultComicMode: String? = null, val shelfStartsClosed: Boolean? = null,
    val comicModes: Map<String, String> = emptyMap(), val comicAnchors: Map<String, BackupComicAnchor> = emptyMap())

@Serializable
 data class BackupComicAnchor(val pageId: String? = null, val scrollPageId: String? = null, val fraction: Float = 0f)

@Serializable
data class ReadingRecords(val books: List<BookEntity>, val chapters: List<ChapterEntity>,
    val positions: List<PositionEntity>, val bookmarks: List<BookmarkEntity>, val excerpts: List<ExcerptEntity>,
    val preferences: BackupPreferences = BackupPreferences())

@Serializable
private data class BackupEnvelope(val format: String = "lightreader-records", val version: Int = 3,
    val createdAt: Long, val payload: String, val sha256: String)

/** A records-only, bounded format. No credentials, files, local paths or cache pointers. */
object RecordBackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private fun digest(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun encode(records: ReadingRecords, now: Long = System.currentTimeMillis()): ByteArray {
        val clean = records.copy(books = records.books.map { it.copy(coverUrl = it.coverUrl?.takeIf { u -> u.startsWith("https://") || u.startsWith("http://") }) },
            chapters = records.chapters.map { it.copy(contentPath = null, downloadedAt = null) })
        validate(clean)
        val payload = json.encodeToString(clean)
        return json.encodeToString(BackupEnvelope(createdAt = now, payload = payload, sha256 = digest(payload)))
            .toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "备份记录超过32MB，请减少记录后重试" } }
    }
    fun decode(bytes: ByteArray): ReadingRecords {
        require(bytes.size <= MAX_BYTES) { "备份文件超过32MB" }
        val envelope = json.decodeFromString<BackupEnvelope>(bytes.toString(Charsets.UTF_8))
        require(envelope.format == "lightreader-records" && envelope.version in 1..3) { "不是受支持的轻阅记录备份版本" }
        require(digest(envelope.payload) == envelope.sha256) { "备份校验失败，文件可能已损坏" }
        return json.decodeFromString<ReadingRecords>(envelope.payload).also(::validate)
    }
    private fun validate(r: ReadingRecords) {
        require(r.books.size <= 10_000 && r.chapters.size <= 200_000 && r.bookmarks.size <= 100_000 && r.excerpts.size <= 100_000) { "备份记录数量过大" }
        val keys = r.books.map { it.key }.toSet()
        require(keys.size == r.books.size && r.books.all { it.key == "${it.sourceId}:${it.bookId}" && it.sourceId.isNotBlank() && it.bookId.isNotBlank() && it.title.isNotBlank() }) { "书架记录无效或重复" }
        require(r.books.filter { it.sourceId == "local" }.all { Regex("[a-fA-F0-9]{16}").matches(it.bookId) && it.bookUrl == "local://${it.bookId}" }) { "本地书籍标识无效" }
        require(r.chapters.map { it.key }.distinct().size == r.chapters.size && r.chapters.all { it.bookKey in keys && it.key.isNotBlank() && it.idx >= 0 && it.contentPath == null && it.downloadedAt == null }) { "章节记录无效" }
        require(r.positions.map { it.bookKey }.distinct().size == r.positions.size && r.positions.all { it.bookKey in keys && it.chapterUrl.isNotBlank() && it.blockIndex >= 0 && it.offsetRatio.isFinite() && it.offsetRatio in 0f..1f }) { "阅读进度无效" }
        require(r.bookmarks.all { it.bookKey in keys && it.chapterUrl.isNotBlank() && it.blockIndex >= 0 } &&
            r.excerpts.all { it.bookKey in keys && it.chapterUrl.isNotBlank() && it.blockIndex >= 0 }) { "书签或摘抄无效" }
        val p=r.preferences
        require(p.fontSize in 13..32 && p.lineHeight.isFinite() && p.lineHeight in 1.4f..2.4f &&
            p.paper in setOf("follow","light","sepia","dark") && p.turnStyle in setOf("slide","cover") &&
            p.shelfLayout in setOf("AUTO","LIST","GRID") && p.appearance in setOf("SYSTEM","LIGHT","DARK") &&
            (p.shelfArtwork == null || p.shelfArtwork in setOf("MASCOT","LAST_COVER","HIDDEN")) &&
            (p.defaultComicMode == null || p.defaultComicMode in setOf("paged", "scroll"))) { "阅读偏好无效" }
        fun safeKey(key: String) = key.isNotBlank() && key.length <= 8192 && key.none(Char::isISOControl)
        require(p.comicModes.size <= 10_000 && p.comicModes.all { (key, value) -> safeKey(key) && value in setOf("paged", "scroll") }) { "漫画阅读方式无效" }
        require(p.comicAnchors.size <= 200_000 && p.comicAnchors.all { (key, anchor) ->
            safeKey(key) && (anchor.pageId == null || safeKey(anchor.pageId)) &&
                (anchor.scrollPageId == null || safeKey(anchor.scrollPageId)) && anchor.fraction.isFinite() && anchor.fraction in 0f..1f
        }) { "漫画阅读锚点无效" }
        require(p.anchors.size <= 200_000 && p.anchors.all { (key,value) -> key.startsWith("inline:") && Regex("[0-9]{1,10}:[0-9]{1,10}").matches(value) }) { "文字锚点无效" }
    }
}

internal fun bookmarkIdentity(b: BookmarkEntity) = listOf(b.bookKey,b.chapterUrl,b.blockIndex.toString(),b.text)
internal fun excerptIdentity(e: ExcerptEntity) = listOf(e.bookKey,e.chapterUrl,e.blockIndex.toString(),e.text)

package com.will.lightreader.data

import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.TextIntegrity
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 章节正文离线缓存：ChapterContent JSON 存 filesDir/chapters/ 下（原子写）。
 * 纯文件逻辑，不碰 Android API —— JVM 可直接单测。
 */
class ChapterCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun write(chapterUrl: String, content: ChapterContent): String {
        if (TextIntegrity.damaged(content) || TextIntegrity.titleOnly(content, content.title))
            throw java.io.IOException("正文编码异常或尚未完整载入，已保留原缓存，请重新获取章节")
        dir.mkdirs()
        val name = fileName(chapterUrl)
        val target = File(dir, name)
        if (target.exists()) {
            val old = runCatching { json.decodeFromString(ChapterContent.serializer(), target.readText()) }.getOrNull()
            if (old != null && TextIntegrity.damaged(old)) {
                val recovery = File(dir.parentFile, "chapter-recovery").apply { mkdirs() }
                val backup = File(recovery, name)
                if (!backup.exists()) target.copyTo(backup)
            }
        }
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(ChapterContent.serializer(), content))
        try {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return "$CHAPTER_DIR/$name"
    }

    /** path 为 write 返回的相对路径；文件缺失返回 null（表示未离线）。 */
    fun read(path: String): ChapterContent? {
        val f = File(dir, File(path).name)
        if (!f.exists()) return null
        return runCatching {
            json.decodeFromString(ChapterContent.serializer(), f.readText()).takeUnless { TextIntegrity.damaged(it) }
        }.getOrNull()
    }

    /**
     * 按章节 URL 直读（文件名确定性推导，不依赖 DB 的 contentPath 指针）。
     * 离线优先兜底：哪怕指针丢了/从未写过指针，只要文件在就能读。
     */
    fun readByUrl(chapterUrl: String): ChapterContent? {
        val primary=File(dir,fileName(chapterUrl))
        val f = if(primary.exists())primary else File(dir,legacyFileName(chapterUrl))
        if (!f.exists()) return null
        return runCatching {
            json.decodeFromString(ChapterContent.serializer(), f.readText()).takeIf { it.chapterUrl==chapterUrl && !TextIntegrity.damaged(it) }
        }.getOrNull()
    }

    fun needsRepair(chapterUrl: String): Boolean {
        val file = File(dir, fileName(chapterUrl))
        if (!file.exists()) return false
        return runCatching { TextIntegrity.damaged(json.decodeFromString(ChapterContent.serializer(), file.readText())) }
            .getOrDefault(true)
    }

    /** 该章的缓存文件是否在（供下载指针重链接，不解析内容）。 */
    fun existsByUrl(chapterUrl: String): Boolean = File(dir, fileName(chapterUrl)).exists()

    /** An online comic manifest does not contain the actual image files. */
    fun offlineCompleteByUrl(chapterUrl: String): Boolean = readByUrl(chapterUrl)?.comicPages?.isEmpty() == true

    /** 该章缓存文件的相对路径（与 write 返回一致）。 */
    fun pathFor(chapterUrl: String): String = "$CHAPTER_DIR/${fileName(chapterUrl)}"

    fun delete(path: String) {
        File(dir, File(path).name).delete()
    }

    /** 缓存文件占用字节（不存在返回 0）。 */
    fun sizeOf(path: String): Long = File(dir, File(path).name).let { if (it.exists()) it.length() else 0L }

    /** 清空全部缓存文件。 */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileName(chapterUrl:String):String = if(chapterUrl.startsWith("https://lightreader.invalid/comic-js-"))
        "comic-"+java.security.MessageDigest.getInstance("SHA-256").digest(chapterUrl.toByteArray()).joinToString("") { "%02x".format(it) }+".json"
        else legacyFileName(chapterUrl)

    private fun legacyFileName(chapterUrl: String): String =
        chapterUrl.replace(Regex("[^\\w]"), "_").take(100) + ".json"

    companion object {
        const val CHAPTER_DIR = "chapters"
    }
}

package com.will.lightreader.source.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.will.lightreader.data.LibraryRepository
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive

data class ImportResult(
    val bookKey: String,
    val title: String,
    val chapters: Int,
    /** 同一份文件之前导入过，直接复用。 */
    val duplicate: Boolean = false,
)

/**
 * 本地书导入：SAF Uri → 临时文件 → 解析 → 书架/目录/正文/缓存一次落库。
 * 正文是导入时就写好的离线缓存，所以阅读页拿到的是「已下载」状态，断网也能翻。
 */
class LocalImporter(
    private val context: Context,
    private val repo: LibraryRepository,
) {
    val root: File get() = File(context.filesDir, LocalIds.IMPORT_DIR)

    suspend fun import(uri: Uri, onProgress: (String) -> Unit = {}): ImportResult =
        withContext(Dispatchers.IO) {
            val name = displayName(uri)
            onProgress("读取 $name")
            val temp = File(context.cacheDir, "import-${System.nanoTime()}-${name.replace(Regex("[^\\w.-]"), "_").takeLast(48)}")
            var ownedDir: File? = null
            var committed = false
            val coroutine = kotlin.coroutines.coroutineContext
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            coroutine.ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            copied += n
                            if (copied > 1024L * 1024 * 1024) throw ImportException("文件超过 1GB，请拆分后导入")
                            out.write(buffer, 0, n)
                        }
                    }
                } ?: throw ImportException("读不到这个文件：$name")
                if (temp.length() == 0L) throw ImportException("文件是空的：$name")

                val head = temp.head(8)
                val format = if (head.size >= 2 && head[0] == 80.toByte() && head[1] == 75.toByte()) {
                    java.util.zip.ZipFile(temp).use { zip ->
                        if (zip.getEntry("META-INF/container.xml") != null) LocalFormat.EPUB else LocalFormat.COMIC
                    }
                } else LocalFormat.of(name, head)
                if (format == LocalFormat.UNKNOWN) {
                    if (name.substringAfterLast('.').lowercase() in setOf("cbr", "rar", "cb7", "7z"))
                        throw ImportException("暂不支持 RAR / CBR / 7z，请转为 CBZ（ZIP 图片集）后导入")
                    throw ImportException("暂不支持这种格式（支持 PDF / CBZ / ZIP / EPUB / TXT / HTML / Markdown）：$name")
                }

                val bookId = temp.sha1().take(16)
                val dir = File(root, bookId)
                val bookKey = LibraryRepository.bookKey(LocalIds.SOURCE, bookId)
                repo.book(bookKey)?.let { existing ->
                    val chapters = repo.chapters(bookKey)
                    var complete = chapters.isNotEmpty()
                    for (chapter in chapters) {
                        if (chapter.contentPath == null) { complete = false; break }
                        val content = repo.chapterContent(chapter.key)
                        if (content == null) { complete = false; break }
                        val refs = content.blocks.filterIsInstance<com.will.lightreader.domain.Block.Image>().map { it.ref } + listOfNotNull(content.fixedPage?.ref)
                        if (refs.any { it.startsWith("file:") && !runCatching { File(java.net.URI(it)).isFile }.getOrDefault(false) }) { complete = false; break }
                    }
                    // A restored catalogue is a placeholder until the original file is reimported.
                    if (complete) return@withContext ImportResult(bookKey, existing.title, chapters.size, true)
                }

                if (!dir.exists()) ownedDir = dir
                dir.mkdirs()
                onProgress("解析 $name")
                val book = when (format) {
                    LocalFormat.EPUB -> EpubParser(temp, dir).parse(TextBookParser.cleanTitle(name))
                    LocalFormat.TXT -> TextBookParser.parse(name, readCapped(temp))
                    LocalFormat.MARKDOWN -> MarkdownBookParser.parse(name, readCapped(temp))
                    LocalFormat.HTML -> HtmlBookParser.parse(name, readCapped(temp), null)
                    LocalFormat.PDF -> PdfBookParser(temp, dir).parse(TextBookParser.cleanTitle(name))
                    LocalFormat.COMIC -> ComicParser(temp, dir).parse(TextBookParser.cleanTitle(name)) { message ->
                        coroutine.ensureActive(); onProgress(message)
                    }
                    LocalFormat.UNKNOWN -> throw ImportException("暂不支持这种格式：$name")
                }
                if (book.chapters.isEmpty()) throw ImportException("《${book.title}》里没有解析出正文")
                if (format == LocalFormat.COMIC) book.chapters.forEachIndexed { index, chapter ->
                    coroutine.ensureActive()
                    val image = File(java.net.URI(requireNotNull(chapter.fixedPage).ref))
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(image.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw ImportException("漫画第 ${index + 1} 页不是有效的图片")
                }

                onProgress("整理目录（${book.chapters.size} ${if (book.chapters.first().fixedPage != null) "页" else "章"}）")
                val brief = BookBrief(
                        sourceId = LocalIds.SOURCE, bookId = bookId,
                        bookUrl = LocalIds.bookUrl(bookId),
                        title = book.title, author = book.author,
                        coverUrl = book.cover?.let { "file://${it.absolutePath}" },
                        tags = listOf(format.label),
                        status = "本地",
                    )
                val refs = book.chapters.mapIndexed { i, c ->
                        ChapterRef(LocalIds.chapterUrl(bookId, i), c.title, c.volume)
                    }
                val contents = book.chapters.mapIndexed { i, c ->
                    coroutine.ensureActive()
                    val url = LocalIds.chapterUrl(bookId, i)
                    if (i % 20 == 0) onProgress("写入正文 ${i + 1}/${book.chapters.size}")
                    ChapterContent(url, c.title, c.blocks, c.fixedPage)
                }
                LocalMetaStore.write(dir, LocalMeta(intro = book.intro, language = book.language, format = format.label))
                repo.saveImportedBook(brief, refs, contents)
                committed = true
                ImportResult(bookKey, book.title, book.chapters.size)
            } finally {
                temp.delete()
                if (!committed) ownedDir?.deleteRecursively()
            }
        }

    private fun readCapped(f: File): ByteArray = FileInputStream(f).use { it.readAllBytesCapped() }

    private fun File.head(n: Int): ByteArray = runCatching {
        FileInputStream(this).use { input ->
            val buf = ByteArray(n)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))

    private fun File.sha1(): String {
        val md = MessageDigest.getInstance("SHA-1")
        FileInputStream(this).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "未命名书籍"
    }

    companion object {
        const val IMPORT_DIR = LocalIds.IMPORT_DIR
    }
}

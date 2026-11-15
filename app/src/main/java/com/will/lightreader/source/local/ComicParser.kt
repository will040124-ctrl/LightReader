package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.FixedPage
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/** Numeric runs compare without integer conversion; folder names participate in page ordering. */
object NaturalPageOrder : Comparator<String> {
    private val parts = Regex("[0-9]+|[^0-9]+")
    override fun compare(a: String, b: String): Int {
        val left = parts.findAll(a.lowercase()).map { it.value }.toList()
        val right = parts.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]; val y = right[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            } else x.compareTo(y)
            if (result != 0) return result
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }
}

class ComicParser(private val file: File, private val outDir: File) {
    fun parse(fallbackTitle: String, progress: (String) -> Unit = {}): ImportedBook {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                if (entries.size > 20_000) throw ImportException("压缩包条目过多，请拆分后导入")
                val pages = entries.filter { entry ->
                    val name = entry.name.replace('\\', '/')
                    name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") &&
                        name.split('/').none { it.startsWith('.') || it == "__MACOSX" }
                }.sortedWith { a, b -> NaturalPageOrder.compare(a.name, b.name) }
                if (pages.isEmpty()) throw ImportException("压缩包中没有可阅读的图片（支持 JPG / PNG / WebP / GIF / BMP）")
                if (pages.size > 5_000) throw ImportException("漫画超过 5000 页，请拆分后导入")
                var written = 0L
                val chapters = pages.mapIndexed { index, entry ->
                    val name = entry.name.replace('\\', '/')
                    if (name.startsWith('/') || ':' in name || name.split('/').any { it == ".." })
                        throw ImportException("压缩包包含无效的图片路径")
                    val ext = name.substringAfterLast('.').lowercase()
                    val dest = File(outDir, "pages/${index.toString().padStart(5, '0')}.$ext")
                    dest.parentFile!!.mkdirs()
                    var pageBytes = 0L
                    zip.getInputStream(entry).use { input -> dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            pageBytes += n; written += n
                            if (pageBytes > 64L * 1024 * 1024 || written > 1024L * 1024 * 1024)
                                throw ImportException("漫画解压后的图片过大，请拆分或压缩后导入")
                            output.write(buffer, 0, n)
                        }
                    } }
                    if (pageBytes == 0L) throw ImportException("漫画第 ${index + 1} 页是空文件")
                    progress("整理漫画 ${index + 1}/${pages.size} 页")
                    val ref = dest.toURI().toString()
                    ImportedChapter("第 ${index + 1} 页", blocks = listOf(Block.Image(ref)), fixedPage = FixedPage(ref))
                }
                val info = entries.firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", true) }
                    ?.takeIf { it.size in 1..1_048_576 }
                    ?.let { zip.getInputStream(it).use { stream -> Jsoup.parse(stream.readBytes().toString(Charsets.UTF_8), "", Parser.xmlParser()) } }
                return ImportedBook(title = info?.selectFirst("Title")?.text()?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                    author = info?.selectFirst("Writer")?.text().orEmpty(),
                    intro = info?.selectFirst("Summary")?.text().orEmpty(),
                    cover = File(java.net.URI(chapters.first().fixedPage!!.ref)), chapters = chapters)
            }
        } catch (e: ImportException) { throw e }
        catch (e: java.io.IOException) { throw ImportException("漫画压缩包无法读取，可能已损坏或加密", e) }
    }
}

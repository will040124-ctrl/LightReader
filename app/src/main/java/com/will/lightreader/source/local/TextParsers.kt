package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 中文小说常见编码兜底：BOM → 严格 UTF-8 → GB18030。 */
internal object TextDecode {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { strict.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charset.forName("GB18030")) }
    }
}

/** 行尾统一成 \n；"\r\r\n"（多数下载器的双 CR）也只算一次换行，不然会凭空多出空行。 */
internal fun String.normalizeNewlines(): String =
    replace(Regex("\\r+\\n"), "\n").replace('\r', '\n')

/** 章节标题行：第一章 / 第12话 / 卷一 / 序章 / 番外三 …… */
internal val CHAPTER_HEADING = Regex(
    "^[ \\t\\u3000]*(?:" +
        "第\\s*[0-9０-９零〇一二三四五六七八九十百千万两]{1,8}\\s*[章节回卷话幕部篇集]|" +
        "[卷楔引序终尾]\\s*[章言语子声幕篇]|番外[^\\s]{0,12}|" +
        "[Cc]hapter\\s+\\d{1,4}|[Pp]rologue|[Ee]pilogue" +
        ")[^\\n]{0,30}$",
    RegexOption.MULTILINE,
)

/**
 * TXT / Markdown 这类纯文本：按章节标题行切章，正文按空行分段。
 * 行宽硬折行的中文小说会把一段拆成多行，所以连续非空行合并成一段。
 */
internal object PlainText {
    fun chapters(text: String, fallbackTitle: String): List<ImportedChapter> {
        val normalized = text.normalizeNewlines()
        val lines = normalized.split('\n')
        val marks = lines.indices.filter { CHAPTER_HEADING.matches(lines[it]) }
        if (marks.size < 2) {
            val blocks = blocksOf(lines, 0, lines.size)
            if (blocks.isEmpty()) throw ImportException("这本 $fallbackTitle 里没有可读的正文")
            return listOf(ImportedChapter(fallbackTitle, "", blocks))
        }
        val out = mutableListOf<ImportedChapter>()
        // 第一个标题之前的内容（简介/前言）单独成章，太短就丢掉
        val preface = blocksOf(lines, 0, marks.first())
        if (preface.sumOf { if (it is Block.Paragraph) it.text.length else 0 } > 40) {
            out += ImportedChapter("前言", "", preface)
        }
        marks.forEachIndexed { i, start ->
            val end = marks.getOrNull(i + 1) ?: lines.size
            val title = lines[start].trim().trim('\u3000').take(60)
            out += ImportedChapter(title, "", blocksOf(lines, start + 1, end))
        }
        return out
    }

    private fun blocksOf(lines: List<String>, from: Int, to: Int): List<Block> {
        val out = mutableListOf<Block>()
        val buf = StringBuilder()
        fun flush() {
            val t = buf.toString().trim()
            if (t.isNotEmpty()) out += Block.Paragraph(t)
            buf.clear()
        }
        for (i in from until to) {
            val line = lines[i].trim('\u3000', ' ', '\t')
            if (line.isEmpty()) { flush(); continue }
            if (buf.isNotEmpty() && needsSpace(buf.last(), line.first())) buf.append(' ')
            buf.append(line)
        }
        flush()
        return out
    }

    /** 中文之间不加空格，西文单词之间补一个。 */
    private fun needsSpace(a: Char, b: Char): Boolean =
        (a.isLetterOrDigit() && a.code < 0x2E80) && (b.isLetterOrDigit() && b.code < 0x2E80)
}

internal object TextBookParser {
    fun parse(fileName: String, bytes: ByteArray): ImportedBook {
        val text = TextDecode.decode(bytes)
        val title = cleanTitle(fileName)
        val author = AUTHOR.find(text.take(2000))?.groupValues?.get(1)?.trim().orEmpty()
        val chapters = PlainText.chapters(text, title)
        return ImportedBook(title = title, author = author, chapters = chapters)
    }

    private val AUTHOR = Regex("作者\\s*[:：]\\s*([^\\n\\r]{1,30})")

    fun cleanTitle(fileName: String): String =
        fileName.substringBeforeLast('.')
            .removeSurrounding("《", "》")
            .substringAfterLast(']').substringAfterLast('】')
            .trim()
            .ifBlank { fileName }
}

internal object MarkdownBookParser {
    fun parse(fileName: String, bytes: ByteArray): ImportedBook {
        val text = TextDecode.decode(bytes).normalizeNewlines()
        val title = TextBookParser.cleanTitle(fileName)
        val lines = text.split('\n')
        val marks = lines.indices.filter { Regex("^#{1,2}\\s+\\S").containsMatchIn(lines[it]) }
        val chapters = if (marks.size >= 2) {
            marks.mapIndexed { i, start ->
                val end = marks.getOrNull(i + 1) ?: lines.size
                val heading = lines[start].trimStart('#', ' ').trim()
                ImportedChapter(heading, "", paragraphs(lines, start + 1, end))
            }
        } else {
            listOf(ImportedChapter(title, "", paragraphs(lines, 0, lines.size)))
        }
        return ImportedBook(title = title, chapters = chapters.filter { it.blocks.isNotEmpty() })
    }

    private fun paragraphs(lines: List<String>, from: Int, to: Int): List<Block> {
        val out = mutableListOf<Block>()
        val buf = StringBuilder()
        fun flush() {
            val t = buf.toString().trim()
            if (t.isNotEmpty()) out += Block.Paragraph(stripInline(t))
            buf.clear()
        }
        for (i in from until to) {
            val line = lines[i]
            if (line.isBlank()) { flush(); continue }
            if (Regex("^\\s*([-*_]\\s*){3,}$").matches(line)) { flush(); out += Block.Divider; continue }
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line.trim())
        }
        flush()
        return out
    }

    private fun stripInline(s: String): String = s
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`]{1,3}"), "")
        .replace(Regex("^>\\s?"), "")
        .trim()
}

internal object HtmlBookParser {
    fun parse(fileName: String, bytes: ByteArray, baseDir: java.io.File?): ImportedBook {
        val raw = TextDecode.decode(bytes)
        val doc = Jsoup.parse(raw)
        val title = doc.title().trim().ifBlank { TextBookParser.cleanTitle(fileName) }
        val resolve: (String) -> java.io.File? = { src ->
            if (src.startsWith("http")) null
            else baseDir?.resolve(src.substringBefore('#').substringBefore('?'))
                ?.takeIf { it.isFile }
        }
        val body = doc.body() ?: doc
        val headings = HtmlBlocks.headings(body).filter { CHAPTER_HEADING.matches(it.text().trim()) }
        val chapters = if (headings.size >= 2) {
            headings.map { h ->
                val blocks = mutableListOf<Block>()
                var n = h.nextElementSibling()
                while (n != null && n.tagName() !in setOf("h1", "h2", "h3")) {
                    HtmlBlocks.toBlocks(n, resolve).forEach { blocks += it }
                    n = n.nextElementSibling()
                }
                ImportedChapter(h.text().trim(), "", blocks)
            }
        } else {
            listOf(ImportedChapter(HtmlBlocks.firstHeading(body) ?: title, "", HtmlBlocks.toBlocks(body, resolve)))
        }
        return ImportedBook(title = title, chapters = chapters.filter { it.blocks.isNotEmpty() })
    }
}

/** 读流到内存（导入的文件都在几百 MB 以内，音频/视频不在此列）。 */
internal fun java.io.InputStream.readAllBytesCapped(limit: Long = 64L * 1024 * 1024): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw ImportException("文件太大（超过 ${limit / 1024 / 1024} MB）")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

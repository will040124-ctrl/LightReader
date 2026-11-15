package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import java.io.File

/** 解析结果：与站点书源解耦，导入器只负责落库。 */
data class ImportedBook(
    val title: String,
    val author: String = "",
    val intro: String = "",
    val language: String = "",
    val cover: File? = null,
    val chapters: List<ImportedChapter> = emptyList(),
)

data class ImportedChapter(
    val title: String,
    val volume: String = "",
    val blocks: List<Block>,
    val fixedPage: com.will.lightreader.domain.FixedPage? = null,
)

/** 支持的本地格式。 */
enum class LocalFormat(val label: String) {
    EPUB("EPUB"),
    TXT("TXT"),
    HTML("HTML"),
    MARKDOWN("Markdown"),
    PDF("PDF"),
    COMIC("CBZ / ZIP 漫画"),
    UNKNOWN("未知格式");

    companion object {
        fun of(fileName: String, head: ByteArray? = null): LocalFormat {
            if (head != null && head.take(4).toByteArray().contentEquals("%PDF".toByteArray())) return PDF
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "epub" -> EPUB
                "txt", "text" -> TXT
                "html", "htm", "xhtml" -> HTML
                "md", "markdown", "mdown" -> MARKDOWN
                "pdf" -> PDF
                "cbz", "zip" -> COMIC
                else -> UNKNOWN
            }
        }
    }
}

/** 导入失败原因（UI 直接展示中文）。 */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

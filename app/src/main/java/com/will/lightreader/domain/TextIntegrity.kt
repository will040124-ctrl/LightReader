package com.will.lightreader.domain

/** Do not guess from language: Japanese, rare ideographs and illustrations are valid content. */
object TextIntegrity {
    fun damaged(text: String): Boolean {
        if (text.isEmpty()) return false
        val replacements = text.count { it == '\uFFFD' }
        val controls = text.count { it.code < 32 && it !in "\n\r\t" }
        return replacements >= 4 && replacements.toDouble() / text.length > .02 ||
            controls >= 4 && controls.toDouble() / text.length > .02
    }

    fun damaged(content: ChapterContent): Boolean = damaged(content.blocks.mapNotNull { (it as? Block.Paragraph)?.text }.joinToString("\n"))

    fun titleOnly(content: ChapterContent, expectedTitle: String): Boolean {
        if (expectedTitle.isBlank() || content.blocks.size != 1) return false
        val text = (content.blocks.single() as? Block.Paragraph)?.text?.trim() ?: return false
        val title = expectedTitle.trim()
        return text == title || text.endsWith(title) &&
            Regex("^第[^\\s]{1,16}[卷册部篇]\\s*$").matches(text.removeSuffix(title))
    }
}

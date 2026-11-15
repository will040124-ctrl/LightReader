package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File

/**
 * XHTML / HTML → 渲染块。段落、标题、列表项、引用都归一成 [Block.Paragraph]，
 * 插图交给调用方解析成本地文件（EPUB 内嵌图 or HTML 同目录图片）。
 */
internal object HtmlBlocks {
    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "blockquote", "li", "dd", "dt",
        "h1", "h2", "h3", "h4", "h5", "h6", "pre", "td", "th", "figure", "figcaption",
    )

    fun toBlocks(root: Element, resolveImage: (String) -> File?): List<Block> {
        val out = mutableListOf<Block>()
        walk(root, resolveImage, out)
        return out
    }

    private fun walk(el: Element, resolveImage: (String) -> File?, out: MutableList<Block>) {
        when (el.tagName()) {
            "script", "style", "head", "title", "meta", "link" -> return
            "img" -> imageBlock(el, resolveImage, out)
            "hr" -> out += Block.Divider
            "br" -> return
        }

        val kids = el.children()
        val nestedBlock = kids.any { it.tagName() in BLOCK_TAGS }
        if (!nestedBlock) {
            val text = plainText(el)
            if (text.isNotBlank()) out += Block.Paragraph(text)
            kids.filter { it.tagName() == "img" }.forEach { imageBlock(it, resolveImage, out) }
            return
        }
        kids.forEach { walk(it, resolveImage, out) }
    }

    private fun imageBlock(el: Element, resolveImage: (String) -> File?, out: MutableList<Block>) {
        val src = el.attr("src").ifBlank { el.attr("xlink:href") }.ifBlank { el.attr("data-src") }
        if (src.isBlank()) return
        val file = resolveImage(src) ?: return
        out += Block.Image(ref = "file://${file.absolutePath}")
    }

    /** 章节标题用：第一个 h1~h3 的纯文本。 */
    fun firstHeading(root: Element): String? =
        root.selectFirst("h1, h2, h3")?.let { plainText(it) }?.takeIf { it.isNotBlank() }

    /** 段内全部标题（HTML/Markdown 切章用）。 */
    fun headings(root: Element): List<Element> = root.select("h1, h2, h3")

    private fun plainText(el: Element): String {
        val copy = el.clone()
        copy.select("br").forEach { it.replaceWith(TextNode(" ")) }
        copy.select("rt, rp").remove()          // 注音假名不进正文
        return copy.text()
            .replace('\u00a0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .trim()
            .trimStart('\u3000')
    }
}

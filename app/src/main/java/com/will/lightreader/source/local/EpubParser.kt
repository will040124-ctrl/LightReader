package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 2 / 3 解析：container.xml → OPF（元数据/清单/spine）→ nav.xhtml 或 NCX 目录 → 正文块。
 * 插图与封面落盘到 [outDir]/img、[outDir]/cover.*，块里只留本地 file:// 路径。
 */
class EpubParser(private val file: File, private val outDir: File) {

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private data class TocEntry(
        val path: String,
        val fragment: String?,
        val title: String,
        val volume: String,
        val level: Int,
    )

    private var opfBase: String = ""

    fun parse(fallbackTitle: String): ImportedBook {
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            throw ImportException("这个 EPUB 打不开（压缩包损坏或不是 EPUB）", e)
        }
        zip.use { z ->
            val opfPath = rootfilePath(z) ?: throw ImportException("EPUB 缺少 container.xml / OPF")
            opfBase = opfPath.substringBeforeLast('/', "")
            val opfBytes = z.read(opfPath) ?: throw ImportException("EPUB 里找不到 $opfPath")
            val opf = parseXml(opfBytes) ?: throw ImportException("EPUB 的 OPF 解析失败")

            val metadata = firstLocal(opf, "metadata")
            val title = firstText(metadata, "title").ifBlank { fallbackTitle }
            val author = allTexts(metadata, "creator").joinToString("、")
            val language = firstText(metadata, "language")
            val intro = cleanIntro(firstText(metadata, "description"))

            val manifest = allLocal(opf, "item").mapNotNull { e ->
                val href = e.getAttribute("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ManifestItem(
                    id = e.getAttribute("id"),
                    href = resolve(opfBase, href),
                    mediaType = e.getAttribute("media-type"),
                    properties = e.getAttribute("properties"),
                )
            }
            val byId = manifest.associateBy { it.id }

            val spineTocId = firstLocal(opf, "spine")?.getAttribute("toc").orEmpty()
            val spine = allLocal(opf, "itemref").mapNotNull { ref ->
                if (ref.getAttribute("linear").equals("no", true)) return@mapNotNull null
                byId[ref.getAttribute("idref")]
            }.filter { it.mediaType.contains("html") }

            val navItem = manifest.firstOrNull { it.properties.split(' ').contains("nav") }
            val ncxItem = manifest.firstOrNull { it.id.isNotBlank() && it.id == spineTocId }
                ?: manifest.firstOrNull { it.mediaType.contains("dtbncx") }
            val toc = navItem?.let { parseNav(z, it) }.orEmpty()
                .ifEmpty { ncxItem?.let { parseNcx(z, it) }.orEmpty() }

            val chapters = mutableListOf<ImportedChapter>()
            val imageCache = mutableMapOf<String, File?>()
            for (item in spine) {
                if (navItem != null && item.href == navItem.href) continue
                val bytes = z.read(item.href) ?: continue
                val doc = Jsoup.parse(TextDecode.decode(bytes))
                val body = doc.body() ?: doc
                val resolveImg: (String) -> File? = { src ->
                    if (src.startsWith("data:") || src.startsWith("http")) {
                        null
                    } else {
                        val path = resolve(item.href.substringBeforeLast('/', ""), src)
                        imageCache.getOrPut(path) {
                            extractImage(z, path).takeIf { it.exists() }
                        }
                    }
                }

                val entries = toc.filter { it.path == item.href }
                val split = if (entries.size > 1 && entries.all { !it.fragment.isNullOrBlank() }) {
                    splitByAnchors(body, entries, resolveImg)
                } else null

                if (split != null) {
                    for ((entry, blocks) in split) {
                        if (blocks.isEmpty()) continue
                        chapters += ImportedChapter(entry.title, entry.volume, dropDuplicateTitle(blocks, entry.title))
                    }
                    continue
                }

                val blocks = HtmlBlocks.toBlocks(body, resolveImg)
                if (!hasContent(blocks)) continue
                val entry = entries.firstOrNull()
                val title = entry?.title?.takeIf { it.isNotBlank() }
                    ?: HtmlBlocks.firstHeading(body)
                    ?: "第${chapters.size + 1}章"
                chapters += ImportedChapter(
                    title = title,
                    volume = entry?.volume.orEmpty(),
                    blocks = dropDuplicateTitle(blocks, title),
                )
            }
            if (chapters.isEmpty()) throw ImportException("EPUB 里没有解析出正文（可能是纯图漫画或加密文件）")

            return ImportedBook(
                title = title, author = author, intro = intro, language = language,
                cover = extractCover(z, opf, manifest, byId), chapters = chapters,
            )
        }
    }

    private fun hasContent(blocks: List<Block>): Boolean =
        blocks.any { (it is Block.Paragraph && it.text.isNotBlank()) || it is Block.Image }

    /** 同文件多目录项（书内锚点）时按锚点切成多章；锚点找不到返回 null 走整篇。 */
    private fun splitByAnchors(
        body: Element,
        entries: List<TocEntry>,
        resolveImg: (String) -> File?,
    ): List<Pair<TocEntry, List<Block>>>? {
        // Boundaries belong to DOM nodes, not body children: many books wrap every
        // chapter in a single div, or place anchors inside paragraphs.
        val order = java.util.IdentityHashMap<org.jsoup.nodes.Node, Int>()
        val subtreeEnd = java.util.IdentityHashMap<org.jsoup.nodes.Node, Int>()
        fun number(node: org.jsoup.nodes.Node) {
            order[node] = order.size
            node.childNodes().forEach(::number)
            subtreeEnd[node] = order.size
        }
        number(body)
        val positioned = entries.map { e ->
            val el = body.getElementById(e.fragment!!) ?: return null
            order.getValue(el) to e
        }.distinctBy { it.first }.sortedBy { it.first }
        fun slice(node: org.jsoup.nodes.Node, from: Int, until: Int): org.jsoup.nodes.Node? {
            val index = order.getValue(node)
            if (index >= until || subtreeEnd.getValue(node) <= from) return null
            if (node !is Element) return if (index >= from) node.clone() else null
            val copy = Element(node.tag(), node.baseUri(), node.attributes().clone())
            node.childNodes().mapNotNull { slice(it, from, until) }.forEach { copy.appendChild(it) }
            return copy.takeIf { index >= from || it.childNodeSize() > 0 }
        }
        return positioned.mapIndexed { i, (start, entry) ->
            val end = positioned.getOrNull(i + 1)?.first ?: order.size
            val from = if (i == 0) 0 else start
            val section = slice(body, from, end) as? Element ?: return null
            val blocks = HtmlBlocks.toBlocks(section, resolveImg)
            entry to dropDuplicateTitle(blocks, entry.title)
        }
    }

    /** 锚点处常有一个和目录同名的标题段落，去掉免得章名重复出现。 */
    private fun dropDuplicateTitle(blocks: List<Block>, title: String): List<Block> {
        val first = blocks.firstOrNull() as? Block.Paragraph ?: return blocks
        return if (first.text.replace(" ", "") == title.replace(" ", "")) blocks.drop(1) else blocks
    }

    // ---------- 容器 / OPF ----------

    private fun rootfilePath(z: ZipFile): String? {
        val doc = z.read("META-INF/container.xml")?.let { parseXml(it) } ?: return null
        return firstLocal(doc, "rootfile")?.getAttribute("full-path")?.takeIf { it.isNotBlank() }
    }

    private fun parseXml(bytes: ByteArray): Document? = runCatching {
        // 有些 EPUB 的 XML 前面带 BOM 或空白，严格解析器会当成「声明不在开头」直接报错
        var start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        while (start < bytes.size && bytes[start].toInt().toChar().let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) start++
        val body = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newDocumentBuilder().parse(ByteArrayInputStream(body))
    }.getOrNull()

    // ---------- 目录 ----------

    private fun parseNav(z: ZipFile, nav: ManifestItem): List<TocEntry> {
        val bytes = z.read(nav.href) ?: return emptyList()
        val doc = Jsoup.parse(TextDecode.decode(bytes))
        val navEl = doc.select("nav").firstOrNull {
            it.attr("epub:type").contains("toc") || it.attr("type").contains("toc")
        } ?: doc.selectFirst("nav") ?: return emptyList()
        val list = navEl.selectFirst("ol, ul") ?: return emptyList()
        val out = mutableListOf<TocEntry>()
        walkNavList(list, nav.href.substringBeforeLast('/', ""), "", 0, out)
        return out
    }

    private fun walkNavList(list: Element, base: String, volume: String, level: Int, out: MutableList<TocEntry>) {
        for (li in list.children().filter { it.tagName() == "li" }) {
            val anchor = li.children().firstOrNull { it.tagName() == "a" }
            val nested = li.children().firstOrNull { it.tagName() == "ol" || it.tagName() == "ul" }
            // 卷名常见写法：<li><span>第一卷</span><ol>…</ol></li>
            val label = (anchor?.text() ?: li.selectFirst("span")?.text())?.trim()
            if (nested != null) {
                walkNavList(nested, base, label?.takeIf { it.isNotBlank() } ?: volume, level + 1, out)
            } else if (anchor != null) {
                val href = anchor.attr("href")
                val path = href.substringBefore('#')
                if (path.isBlank()) continue
                out += TocEntry(
                    path = resolve(base, path),
                    fragment = href.substringAfter('#', "").ifBlank { null },
                    title = label.orEmpty(),
                    volume = volume,
                    level = level,
                )
            }
        }
    }

    private fun parseNcx(z: ZipFile, ncx: ManifestItem): List<TocEntry> {
        val doc = z.read(ncx.href)?.let { parseXml(it) } ?: return emptyList()
        val out = mutableListOf<TocEntry>()
        val base = ncx.href.substringBeforeLast('/', "")
        val navMap = firstLocal(doc, "navMap") ?: return emptyList()

        fun walk(point: org.w3c.dom.Element, volume: String, level: Int) {
            val title = directChildText(point, "navLabel")?.let { firstText(it, "text") }.orEmpty()
            val src = firstLocal(point, "content")?.getAttribute("src").orEmpty()
            val children = directChildren(point, "navPoint")
            if (src.isNotBlank()) {
                out += TocEntry(
                    resolve(base, src.substringBefore('#')),
                    src.substringAfter('#', "").ifBlank { null },
                    title, volume, level,
                )
            }
            // 有子节点 = 这一点是卷名，子节点继承它
            val vol = if (children.isNotEmpty()) title.ifBlank { volume } else volume
            children.forEach { walk(it, vol, level + 1) }
        }
        directChildren(navMap, "navPoint").forEach { walk(it, "", 0) }
        return out
    }

    // ---------- 图片 ----------

    private fun extractImage(z: ZipFile, path: String): File {
        val dir = File(outDir, "img").apply { mkdirs() }
        val name = path.replace(Regex("[^\\w.-]"), "_").takeLast(64).ifBlank { path.hashCode().toString() }
        val out = File(dir, name)
        if (!out.exists()) {
            val bytes = z.read(path) ?: return File(dir, "__missing__")
            out.writeBytes(bytes)
        }
        return out
    }

    private fun extractCover(
        z: ZipFile,
        opf: Document,
        manifest: List<ManifestItem>,
        byId: Map<String, ManifestItem>,
    ): File? {
        val coverItem = manifest.firstOrNull { it.properties.split(' ').contains("cover-image") }
            ?: allLocal(opf, "meta").firstOrNull { it.getAttribute("name").equals("cover", true) }
                ?.let { byId[it.getAttribute("content")] }
            ?: allLocal(opf, "reference").firstOrNull { it.getAttribute("type").equals("cover", true) }
                ?.let { ref -> manifest.firstOrNull { it.href == resolve(opfBase, ref.getAttribute("href")) } }
            ?: manifest.firstOrNull { it.mediaType.startsWith("image/") && it.href.contains("cover", true) }
            ?: return null
        val bytes = z.read(coverItem.href) ?: return null
        if (bytes.isEmpty()) return null
        val ext = coverItem.href.substringAfterLast('.', "jpg").take(4).lowercase()
        return File(outDir, "cover.$ext").apply { writeBytes(bytes) }
    }

    // ---------- DOM 小工具 ----------

    private fun directChildren(parent: org.w3c.dom.Element, name: String): List<org.w3c.dom.Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? org.w3c.dom.Element }
            .filter { it.localName == name || it.nodeName == name || it.nodeName.endsWith(":$name") }
    }

    private fun directChildText(parent: org.w3c.dom.Element, name: String): org.w3c.dom.Element? =
        directChildren(parent, name).firstOrNull()

    private fun allLocal(parent: org.w3c.dom.Node?, name: String): List<org.w3c.dom.Element> {
        val list: NodeList = when (parent) {
            is Document -> parent.getElementsByTagName("*")
            is org.w3c.dom.Element -> parent.getElementsByTagName("*")
            else -> return emptyList()
        }
        return (0 until list.length).map { list.item(it) as org.w3c.dom.Element }
            .filter { it.localName == name || it.nodeName == name || it.nodeName.endsWith(":$name") }
    }

    private fun firstLocal(parent: org.w3c.dom.Node?, name: String): org.w3c.dom.Element? =
        allLocal(parent, name).firstOrNull()

    private fun firstText(parent: org.w3c.dom.Node?, name: String): String =
        allLocal(parent, name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun allTexts(parent: org.w3c.dom.Node?, name: String): List<String> =
        allLocal(parent, name).mapNotNull { it.textContent?.trim() }.filter { it.isNotBlank() }

    private fun cleanIntro(raw: String): String =
        Jsoup.parse(raw).text().replace(Regex("\\s+"), " ").trim()

    /** zip 内相对路径解析（处理 ./ ../ 与 URL 转义）。 */
    private fun resolve(baseDir: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val joined = if (decoded.startsWith("/")) decoded.substring(1)
        else if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
        val parts = ArrayDeque<String>()
        for (seg in joined.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun ZipFile.read(path: String): ByteArray? {
        val entry = getEntry(path) ?: return null
        if (entry.isDirectory) return null
        return runCatching { getInputStream(entry).readAllBytesCapped() }.getOrNull()
    }
}

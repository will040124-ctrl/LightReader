package com.will.lightreader.source.local

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.FixedPage
import kotlinx.serialization.json.Json

class ComicParserTest {
    @get:Rule val temp = TemporaryFolder()
    private fun archive(vararg names: String): File = temp.newFile().also { f ->
        ZipOutputStream(f.outputStream()).use { z -> names.forEach { name ->
            z.putNextEntry(ZipEntry(name)); z.write(if (name.endsWith("xml")) "<ComicInfo><Title>测试漫画</Title><Writer>作者</Writer></ComicInfo>".toByteArray() else name.toByteArray()); z.closeEntry()
        } }
    }
    @Test fun `pages sort naturally across folders and large numbers without overflow`() {
        val values = listOf("vol10/1.png", "vol2/10.png", "vol2/2.png", "vol2/999999999999999999999999.png")
        assertEquals(listOf(values[2], values[1], values[3], values[0]), values.sortedWith(NaturalPageOrder))
    }
    @Test fun `archive uses natural order ignores mac junk and preserves metadata`() {
        val out = temp.newFolder()
        val book = ComicParser(archive("10.jpg", "2.jpg", "1.jpg", "__MACOSX/extra.jpg", ".hidden.jpg", "ComicInfo.xml"), out).parse("fallback")
        assertEquals("测试漫画", book.title); assertEquals("作者", book.author)
        assertEquals(3, book.chapters.size)
        val files = book.chapters.map { File(java.net.URI(it.fixedPage!!.ref)).readText() }
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), files)
        assertTrue(book.cover!!.isFile)
    }
    @Test(expected = ImportException::class) fun `archive with no image pages is rejected`() {
        ComicParser(archive("readme.txt"), temp.newFolder()).parse("empty")
    }
    @Test(expected = ImportException::class) fun `traversal entry cannot write outside extraction directory`() {
        ComicParser(archive("../escape.png"), temp.newFolder()).parse("bad")
    }
    @Test fun `format detection distinguishes PDF comic and EPUB extensions`() {
        assertEquals(LocalFormat.PDF, LocalFormat.of("unknown.bin", "%PDF-1.7".toByteArray()))
        assertEquals(LocalFormat.COMIC, LocalFormat.of("book.cbz"))
        assertEquals(LocalFormat.COMIC, LocalFormat.of("book.zip", "PK0000".toByteArray()))
        assertEquals(LocalFormat.EPUB, LocalFormat.of("book.epub", "PK0000".toByteArray()))
        assertEquals(LocalFormat.UNKNOWN, LocalFormat.of("book.cbr"))
    }
    @Test fun `old chapter JSON remains readable and fixed page round trips`() {
        val old = Json.decodeFromString(ChapterContent.serializer(), """{"chapterUrl":"old","title":"Title","blocks":[]}""")
        assertNull(old.fixedPage)
        val pdf = old.copy(fixedPage = FixedPage("file:///book.pdf", 7))
        assertEquals(pdf, Json.decodeFromString(ChapterContent.serializer(), Json.encodeToString(ChapterContent.serializer(), pdf)))
    }
}

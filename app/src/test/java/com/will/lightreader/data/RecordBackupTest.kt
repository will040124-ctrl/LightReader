package com.will.lightreader.data

import org.junit.Assert.*
import org.junit.Test

class RecordBackupTest {
    private fun records() = ReadingRecords(
        listOf(BookEntity("wenku8:1","wenku8","1","https://www.wenku8.net/book/1.htm","书")),
        listOf(ChapterEntity("chapter","wenku8:1","章",contentPath="chapters/a.json",downloadedAt=2)),
        listOf(PositionEntity("wenku8:1","chapter",blockIndex=3,offsetRatio=.4f,updatedAt=5)),
        listOf(BookmarkEntity(8,"wenku8:1","chapter",3,"文字",7)),
        listOf(ExcerptEntity(9,"wenku8:1","chapter",blockIndex=3,text="摘抄",createdAt=7)))
    @Test fun `records round trip retains progress and annotations but never claims offline files`() {
        val r=RecordBackupCodec.decode(RecordBackupCodec.encode(records(),1))
        assertEquals(records().positions,r.positions);assertEquals(records().bookmarks,r.bookmarks)
        assertNull(r.chapters.single().contentPath);assertNull(r.chapters.single().downloadedAt)
    }
    @Test fun `private local cover paths never enter the archive`() {
        val r=records().copy(books=records().books.map{it.copy(coverUrl="file:///data/user/0/private/cover.png")})
        val bytes=RecordBackupCodec.encode(r,1)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("private/cover"))
    }
    @Test(expected=IllegalArgumentException::class) fun `changed payload is rejected by checksum`() {
        val b=RecordBackupCodec.encode(records(),1).toString(Charsets.UTF_8).replace("wenku8:1","wenku8:2")
        RecordBackupCodec.decode(b.toByteArray())
    }
    @Test(expected=IllegalArgumentException::class) fun `unknown backup version cannot be applied`() {
        val b=RecordBackupCodec.encode(records(),1).toString(Charsets.UTF_8).replace("\"version\":3","\"version\":99")
        RecordBackupCodec.decode(b.toByteArray())
    }
    @Test(expected=IllegalArgumentException::class) fun `orphan annotations are rejected`() {
        RecordBackupCodec.encode(records().copy(bookmarks=listOf(records().bookmarks.single().copy(bookKey="missing"))))
    }
    @Test(expected=IllegalArgumentException::class) fun `unsafe local ids cannot be restored into file operations`() {
        RecordBackupCodec.encode(ReadingRecords(listOf(BookEntity("local:..","local","..","local://..","bad")),emptyList(),emptyList(),emptyList(),emptyList()))
    }
    @Test(expected=IllegalArgumentException::class) fun `invalid preferences rejected before writes`() {
        RecordBackupCodec.encode(records().copy(preferences=BackupPreferences(fontSize=999)))
    }
    @Test fun `annotation identity is independent of database ids`() {
        val a=records().bookmarks.single();assertEquals(bookmarkIdentity(a),bookmarkIdentity(a.copy(id=99,createdAt=999)))
        val e=records().excerpts.single();assertEquals(excerptIdentity(e),excerptIdentity(e.copy(id=99)))
    }
    @Test fun `personalization choices survive backup`() {
        val prefs=BackupPreferences(shelfArtwork="LAST_COVER",shelfWheel=false,widgetDetails=false)
        assertEquals(prefs,RecordBackupCodec.decode(RecordBackupCodec.encode(records().copy(preferences=prefs))).preferences)
    }
    @Test fun `v1 backups do not invent new personalization or privacy choices`() {
        val payload="""{"books":[],"chapters":[],"positions":[],"bookmarks":[],"excerpts":[],"preferences":{"fontSize":20}}"""
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        val escaped=kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(),payload)
        val bytes="""{"format":"lightreader-records","version":1,"createdAt":1,"payload":$escaped,"sha256":"$sha"}""".toByteArray()
        val prefs=RecordBackupCodec.decode(bytes).preferences
        assertEquals(20,prefs.fontSize);assertNull(prefs.shelfArtwork);assertNull(prefs.shelfWheel);assertNull(prefs.widgetDetails)
        assertNull(prefs.immersive);assertNull(prefs.hideStatusBar);assertNull(prefs.defaultComicMode);assertNull(prefs.shelfStartsClosed)
        assertTrue(prefs.comicAnchors.isEmpty())
    }
    @Test fun `global and per-comic preferences retain image-relative positions`() {
        val prefs=BackupPreferences(immersive=false,hideStatusBar=true,defaultComicMode="scroll",shelfStartsClosed=false,
            comicModes=mapOf("comic-js-qa:book" to "scroll"),
            comicAnchors=mapOf("comic://chapter" to BackupComicAnchor("page-2","page-2",.625f)))
        assertEquals(prefs,RecordBackupCodec.decode(RecordBackupCodec.encode(records().copy(preferences=prefs))).preferences)
    }
    @Test(expected=IllegalArgumentException::class) fun `invalid comic anchor rejected before preferences are applied`() {
        RecordBackupCodec.encode(records().copy(preferences=BackupPreferences(
            comicAnchors=mapOf("chapter" to BackupComicAnchor(fraction=Float.NaN)))))
    }
    @Test(expected=IllegalArgumentException::class) fun `unknown comic mode cannot be restored`() {
        RecordBackupCodec.encode(records().copy(preferences=BackupPreferences(defaultComicMode="invalid")))
    }

    @Test fun `legacy v2 backup leaves immersive choices unspecified`() {
        val payload="""{"books":[],"chapters":[],"positions":[],"bookmarks":[],"excerpts":[],"preferences":{"widgetDetails":false,"paper":"dark"}}"""
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        val escaped=kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(),payload)
        val bytes="""{"format":"lightreader-records","version":2,"createdAt":1,"payload":$escaped,"sha256":"$sha"}""".toByteArray()
        val prefs=RecordBackupCodec.decode(bytes).preferences
        assertEquals(false,prefs.widgetDetails);assertEquals("dark",prefs.paper)
        assertNull(prefs.immersive);assertNull(prefs.defaultComicMode)
    }

}

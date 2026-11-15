package com.will.lightreader.qa

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.LightReaderApp
import com.will.lightreader.source.local.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Isolated APK only. Original fixtures pass through the real importer and repository. */
class ReaderFormatsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as LightReaderApp).container
        val store = getSharedPreferences("reader_formats_qa", MODE_PRIVATE)
        val operation = intent.getStringExtra("operation") ?: "prepare"
        lifecycleScope.launch {
            try {
                if (operation == "prepare") {
                    val report = withContext(Dispatchers.IO) {
                        val dir = File(cacheDir, "reader-formats-qa").apply { mkdirs() }
                        val files = fixtures(dir)
                        val records = JSONObject()
                        for ((format, file) in files) {
                            val result = app.localImporter.import(Uri.fromFile(file))
                            val chapters = app.library.chapters(result.bookKey)
                            check(chapters.size == if (format == "pdf") 5 else if (format in listOf("cbz", "zip")) 4 else 3)
                            val duplicate = app.localImporter.import(Uri.fromFile(file))
                            check(duplicate.duplicate && duplicate.bookKey == result.bookKey)
                            store.edit().putString(format, result.bookKey).commit()
                            // Per-fixture setting; never change the user's default mode.
                            getSharedPreferences("reader_prefs", MODE_PRIVATE).edit()
                                .putString("comic-mode:${result.bookKey}", "paged").commit()
                            app.library.savePosition(result.bookKey, chapters.first().key, chapters.first().title, 0, 0f)
                            records.put(format, JSONObject().put("key", result.bookKey).put("chapters", chapters.size).put("duplicate", true))
                        }
                        for ((name, bytes) in mapOf("empty.txt" to byteArrayOf(), "broken.pdf" to "%PDF broken".toByteArray(), "unsupported.cbr" to "RAR".toByteArray())) {
                            val bad = File(dir, name).apply { writeBytes(bytes) }
                            check(runCatching { app.localImporter.import(Uri.fromFile(bad)) }.exceptionOrNull() is ImportException)
                        }
                        val badImage = File(dir, "invalid.cbz")
                        zip(badImage, mapOf("1.png" to "not an image".toByteArray()))
                        check(runCatching { app.localImporter.import(Uri.fromFile(badImage)) }.exceptionOrNull() is ImportException)
                        records.put("invalidFilesRejected", 4)
                    }
                    result(report.toString())
                } else if (operation == "online-real") {
                    val manager=app.comicScripts
                    val pkg=manager.store.all().first { it.key=="mangadex_public" }
                    val source=com.will.lightreader.source.script.ScriptComicSource(manager,pkg)
                    val url=com.will.lightreader.source.script.ComicScriptRoutes.book(source.id,"9fca3c19-ac41-4ebf-8735-f4e3becc2b3e")
                    val detail=source.detail(url)
                    val refs=source.episodes(url)
                    val key=com.will.lightreader.data.LibraryRepository.bookKey(source.id,detail.bookId)
                    app.library.saveBook(com.will.lightreader.domain.BookBrief(source.id,detail.bookId,url,detail.title,detail.author,detail.coverUrl))
                    app.library.saveChapters(key,refs)
                    val pages=source.pages(refs.first().chapterUrl)
                    app.library.saveChapterContent(refs.first().chapterUrl,com.will.lightreader.domain.ChapterContent(refs.first().chapterUrl,refs.first().title,emptyList(),comicPages=pages))
                    app.library.savePosition(key,refs.first().chapterUrl,refs.first().title,2,0f)
                    store.edit().putString("onlinecomic",key).commit()
                    getSharedPreferences("reader_prefs",MODE_PRIVATE).edit().putString("comic-mode:$key","paged").commit()
                    result("PASS MangaDex source directory ${refs.size}, chapter pages ${pages.size}")
                } else if (operation == "real-media") {
                    withContext(Dispatchers.IO) {
                        val dir=File(cacheDir,"real-presentation").apply { mkdirs() }
                        fun fetch(name:String):ByteArray=java.net.URL("http://127.0.0.1:8775/assets/$name").openStream().use { it.readBytes() }
                        val pdf=File(dir,"Earth at Night.pdf").apply { writeBytes(fetch("earth-at-night.pdf")) }
                        val comic=File(dir,"Give My Regards to Black Jack.cbz")
                        zip(comic,(1..3).associate { "$it.jpg" to fetch("manga-$it.jpg") })
                        for((format,file) in listOf("realpdf" to pdf,"realcomic" to comic)) {
                            val imported=app.localImporter.import(Uri.fromFile(file))
                            store.edit().putString(format,imported.bookKey).commit()
                            getSharedPreferences("reader_prefs",MODE_PRIVATE).edit().putString("comic-mode:${imported.bookKey}","paged").commit()
                            val chapter=app.library.chapters(imported.bookKey)[if(format=="realpdf")40 else 2]
                            app.library.savePosition(imported.bookKey,chapter.key,chapter.title,0,0f)
                        }
                    }
                    result("PASS real PDF 200 pages and real MangaDex images imported through LocalImporter")
                } else if (operation == "presentation-comic") {
                    val summary=withContext(Dispatchers.IO) {
                        val dir=File(cacheDir,"presentation-comic").apply { mkdirs() }
                        fun fetch(name:String):ByteArray=java.net.URL("http://127.0.0.1:8777/source-comic/$name").openStream().use { it.readBytes() }
                        val comic=File(dir,"月夜书屋.cbz")
                        zip(comic,(1..3).associate { "$it.jpg" to fetch("original-$it.jpg") })
                        store.getString("realcomic",null)?.let { app.library.removeBook(it) }
                        val imported=app.localImporter.import(Uri.fromFile(comic))
                        store.edit().putString("realcomic",imported.bookKey).commit()
                        getSharedPreferences("reader_prefs",MODE_PRIVATE).edit().putString("comic-mode:${imported.bookKey}","paged").commit()
                        val chapter=app.library.chapters(imported.bookKey).first()
                        app.library.savePosition(imported.bookKey,chapter.key,chapter.title,0,0f)
                        com.will.lightreader.ui.reader.ReaderPrefs(this@ReaderFormatsQaActivity).paper = "dark"
                        "comic ${imported.title}, ${app.library.chapters(imported.bookKey).size} chapters"
                    }
                    result("PASS $summary")
                } else if (operation == "presentation") {
                    com.will.lightreader.ui.reader.ReaderPrefs(this@ReaderFormatsQaActivity).apply {
                        fontSize = 18; lineHeight = 1.7f; paper = "sepia"
                        immersive = true; hideStatusBar = true; twoPage = false; shelfStartsClosed = true
                    }
                    com.will.lightreader.ui.theme.AppAppearance(this@ReaderFormatsQaActivity).mode = com.will.lightreader.ui.theme.AppearanceMode.LIGHT
                    withContext(Dispatchers.IO) {
                        val formats = listOf("epub3", "txt", "html", "md", "pdf", "cbz", "zip", "epub2")
                        val titles = listOf("月光图书馆", "风停在书页之间", "雨夜来信", "第七次夏天", "纸上的旅行", "星河画集", "窗边的猫", "慢慢读")
                        val colors = listOf(0xff626f92, 0xffd9cfb7, 0xffbac4af, 0xffcfb9b0, 0xff8996ae, 0xffc5bdce, 0xffb4c9c9, 0xffe0d5c1)
                        val dir = File(filesDir, "qa-presentation").apply { mkdirs() }
                        for (i in formats.indices.reversed()) {
                            val key = requireNotNull(store.getString(formats[i], null))
                            val old = requireNotNull(app.library.book(key))
                            val cover = File(dir, "${formats[i]}.png")
                            val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
                            try {
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(colors[i].toInt())
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                                if (i == 0) {
                                    val art = android.graphics.BitmapFactory.decodeResource(resources, com.will.lightreader.R.drawable.watercolor_reading_room)
                                    try { canvas.drawBitmap(art, null, android.graphics.Rect(-280, 0, 1060, 900), paint) } finally { art.recycle() }
                                    paint.color = 0x7739425a
                                    canvas.drawRect(0f, 0f, 600f, 900f, paint)
                                }
                                paint.color = if (i in listOf(0, 4)) Color.WHITE else 0xff394252.toInt()
                                paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                                paint.textSize = 72f
                                titles[i].chunked(4).forEachIndexed { line, value -> canvas.drawText(value, 54f, 160f + line * 106f, paint) }
                                paint.textSize = 18f
                                canvas.drawText("LIGHTREADER / ORIGINAL DEMO", 54f, 824f, paint)
                                cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            } finally { bitmap.recycle() }
                            app.library.saveBook(com.will.lightreader.domain.BookBrief(old.sourceId, old.bookId, old.bookUrl,
                                titles[i], "轻阅 · 原创演示", cover.toURI().toString()))
                            app.library.touchRead(key)
                        }
                    }
                    result("PRESENTATION original demo titles and covers only")
                } else {
                    val format = intent.getStringExtra("format") ?: "epub3"
                    val key = requireNotNull(store.getString(format, null))
                    if (operation == "open") {
                        startActivity(Intent(this@ReaderFormatsQaActivity, com.will.lightreader.MainActivity::class.java)
                            .setAction(com.will.lightreader.ui.ReadingRequests.ACTION)
                            .putExtra(com.will.lightreader.ui.ReadingRequests.EXTRA_BOOK, key))
                        finish()
                    } else if (operation == "reset") {
                        val chapters = withContext(Dispatchers.IO) { app.library.chapters(key) }
                        val chapter = chapters[intent.getIntExtra("chapter", 0).coerceIn(chapters.indices)]
                        withContext(Dispatchers.IO) { app.library.savePosition(key, chapter.key, chapter.title, 0, 0f) }
                        result("RESET $format")
                    } else {
                        val pos = withContext(Dispatchers.IO) { app.library.position(key) }
                        result(JSONObject().put("format", format).put("chapter", pos?.chapterUrl).put("block", pos?.blockIndex).put("ratio", pos?.offsetRatio).toString())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReaderFormatsQA", "FAIL", e)
                result("FAIL ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun result(value: String) {
        android.util.Log.i("ReaderFormatsQA", "RESULT=$value")
        setContentView(android.widget.TextView(this).apply { text = value; textSize = 16f })
    }

    private fun zip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (path, bytes) ->
                out.putNextEntry(ZipEntry(path).apply { time = 0 })
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private fun image(page: Int, tall: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(600, if (tall) 2400 else 900, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (page % 2 == 0) 0xffe8d9c7.toInt() else 0xffccd6e8.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff303b50.toInt(); textSize = 38f }
            canvas.drawText("READER QA / PAGE $page", 40f, 110f, paint)
            canvas.drawText("PAGE END $page", 40f, bitmap.height - 70f, paint)
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally { bitmap.recycle() }
    }

    private fun fixtures(dir: File): Map<String, File> {
        val sentence = "风吹过书房的窗台，纸页留下细小的声音。我们记住这一行文字的位置，再继续下一段旅程。"
        val paragraphs = (1..3).map { chapter -> (1..32).map { n -> "第${chapter}章第${n}段，" + sentence.repeat(3) } }
        val names = listOf("第一章 纸间", "第二章 月光", "第三章 归来")
        val files = linkedMapOf<String, File>()
        fun text(format: String, body: String) { files[format] = File(dir, "Reader Acceptance $format.$format").apply { writeText(body) } }
        text("txt", names.indices.joinToString("\n\n") { i -> names[i] + "\n\n" + paragraphs[i].joinToString("\n\n") })
        text("md", names.indices.joinToString("\n\n") { i -> "# ${names[i]}\n\n" + paragraphs[i].joinToString("\n\n") })
        text("html", "<html><head><title>Reader Acceptance html</title></head><body>" + names.indices.joinToString("") { i ->
            "<h1>${names[i]}</h1>" + paragraphs[i].joinToString("") { "<p>$it</p>" }
        } + "</body></html>")
        for (version in listOf(2, 3)) {
            val entries = linkedMapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='content.opf'/></rootfiles></container>".toByteArray(),
                "content.opf" to ("<package version='$version.0'><metadata><title>Reader Acceptance epub$version</title></metadata><manifest>" +
                    "<item id='all' href='all.xhtml' media-type='application/xhtml+xml'/>" +
                    (if (version == 3) "<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/>" else "<item id='toc' href='toc.ncx' media-type='application/x-dtbncx+xml'/>") +
                    "</manifest><spine toc='toc'><itemref idref='all'/></spine></package>").toByteArray(),
                "all.xhtml" to ("<html><body><div><section>" + names.indices.joinToString("") { i ->
                    "<h2 id='c$i'>${names[i]}</h2>" + paragraphs[i].joinToString("") { "<p>$it</p>" } +
                        (if (i == 1) "<img src='illustration.png'/><p>${sentence.repeat(450)}</p>" else "")
                } + "</section></div></body></html>").toByteArray(),
                "illustration.png" to image(1),
            )
            entries[if (version == 3) "nav.xhtml" else "toc.ncx"] = if (version == 3)
                ("<nav><ol>" + names.indices.joinToString("") { "<li><a href='all.xhtml#c$it'>${names[it]}</a></li>" } + "</ol></nav>").toByteArray()
            else ("<ncx><navMap>" + names.indices.joinToString("") { "<navPoint><navLabel><text>${names[it]}</text></navLabel><content src='all.xhtml#c$it'/></navPoint>" } + "</navMap></ncx>").toByteArray()
            files["epub$version"] = File(dir, "Reader Acceptance epub$version.epub").also { zip(it, entries) }
        }
        files["pdf"] = File(dir, "Reader Acceptance pdf.pdf").also { file ->
            val doc = PdfDocument()
            try {
                for (n in 1..5) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(if (n == 3) 900 else 600, 900, n).create())
                    page.canvas.drawColor(Color.WHITE)
                    val paint = Paint().apply { textSize = 30f }
                    page.canvas.drawText("PDF QA / PAGE $n", 50f, 100f, paint)
                    page.canvas.drawText("PAGE END $n", 50f, 830f, paint)
                    doc.finishPage(page)
                }
                file.outputStream().use(doc::writeTo)
            } finally { doc.close() }
        }
        for (ext in listOf("cbz", "zip")) {
            // Distinct bytes, same natural numeric ordering requirement.
            files[ext] = File(dir, "Reader Acceptance $ext.$ext").also { file ->
                zip(file, linkedMapOf("10.png" to image(4), "2.png" to image(2, true), "1.png" to image(1), "3.png" to image(3), "note.txt" to ext.toByteArray()))
            }
        }
        return files
    }
}

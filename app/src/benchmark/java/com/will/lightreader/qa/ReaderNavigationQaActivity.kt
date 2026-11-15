package com.will.lightreader.qa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.data.*
import com.will.lightreader.domain.*
import com.will.lightreader.source.local.*
import com.will.lightreader.ui.reader.ReaderPrefs
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

/** Original local fixtures, no network or modifications to existing books. */
class ReaderNavigationQaActivity:ComponentActivity() {
    private fun id(format:String)=MessageDigest.getInstance("SHA-256").digest("ReaderNavigationQA-$format-v1".toByteArray()).joinToString("") { "%02x".format(it) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val run=intent.getStringExtra("qaRunId").orEmpty()
        val action=intent.getStringExtra("operation") ?: "prepare"
        val format=intent.getStringExtra("format") ?: "novel"
        require(format in listOf("novel","comic"))
        val app=(application as com.will.lightreader.LightReaderApp).container
        val repo=app.library
        lifecycleScope.launch {
            val result=try {
                withContext(Dispatchers.IO) {
                    val store=getSharedPreferences("reader_prefs",MODE_PRIVATE)
                    val original=File(cacheDir,"reader-navigation-original-mode.txt")
                    val originalTwo=File(cacheDir,"reader-navigation-original-two-page.txt")
                    if(action=="prepare") {
                        require(!original.exists()) { "Unfinished QA session; restore first" }
                        original.writeText(store.getString("comic_mode_default",null) ?: "UNSET")
                        originalTwo.writeText(store.all["twoPage"]?.toString() ?: "UNSET")
                        check(store.edit().putBoolean("twoPage",false).commit())
                        for(type in listOf("novel","comic")) {
                            val value=id(type);val key="local:$value"
                            require(repo.book(key)==null) { "Fixture already exists; refusing overwrite" }
                            val dir=File(filesDir,"imports/$value").apply { mkdirs() }
                            val refs=mutableListOf<ChapterRef>();val contents=mutableListOf<ChapterContent>()
                            if(type=="novel") {
                                val url=LocalIds.chapterUrl(value,0)
                                refs+=ChapterRef(url,"纸间练习")
                                contents+=ChapterContent(url,"纸间练习",(1..400).map { n ->
                                    Block.Paragraph("这是第${n.toString().padStart(3,'0')}段原创测试文字。风吹过窗边的纸页，书签停在了刚才读到的地方。我们用这些文字检查直接拖动进度条和阅读位置。".repeat(2))
                                })
                            } else for(n in 1..12) {
                                val file=File(dir,"$n.png")
                                val bitmap=android.graphics.Bitmap.createBitmap(600,900,android.graphics.Bitmap.Config.ARGB_8888)
                                try {
                                    val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(0xffd6d9e4.toInt())
                                    val paint=android.graphics.Paint().apply { color=0xff39425a.toInt();textSize=34f }
                                    canvas.drawText("READER QA / PAGE $n",40f,120f,paint)
                                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                                } finally { bitmap.recycle() }
                                val url=LocalIds.chapterUrl(value,n-1);refs+=ChapterRef(url,"第 $n 页")
                                contents+=ChapterContent(url,"第 $n 页",emptyList(),FixedPage(file.toURI().toString()))
                            }
                            LocalMetaStore.write(dir,LocalMeta(format=if(type=="comic")"漫画 CBZ" else "TXT"))
                            repo.saveImportedBook(BookBrief("local",value,LocalIds.bookUrl(value),"Reader Navigation QA $type","QA"),refs,contents)
                            repo.savePosition(key,refs.first().chapterUrl,refs.first().title,0,0f)
                        }
                        check(store.edit().putString("comic-mode:local:${id("comic")}","paged").commit())
                    } else if(action=="cleanup") {
                        for(type in listOf("novel","comic")) {
                            val value=id(type);val key="local:$value"
                            if(repo.book(key)?.title=="Reader Navigation QA $type")repo.removeBook(key)
                            val edit=store.edit();store.all.keys.filter { it.contains(value) }.forEach(edit::remove);check(edit.commit())
                        }
                        if(original.exists()) {
                            val old=original.readText();val edit=store.edit()
                            if(old=="UNSET")edit.remove("comic_mode_default") else edit.putString("comic_mode_default",old)
                            check(edit.commit());original.delete()
                        }
                        if(originalTwo.exists()) {
                            val old=originalTwo.readText();val edit=store.edit()
                            if(old=="UNSET")edit.remove("twoPage") else edit.putBoolean("twoPage",old.toBoolean())
                            check(edit.commit());originalTwo.delete()
                        }
                    }
                    val prefs=ReaderPrefs(this@ReaderNavigationQaActivity)
                    val mode=prefs.comicMode("local:${id("comic")}")
                    val fresh=prefs.comicMode("qa-never-opened-comic")
                    if(action=="inspect")intent.getStringExtra("expectedMode")?.let { expected ->
                        check(mode==expected && fresh==expected && prefs.defaultComicMode==expected)
                    }
                    "PASS operation=$action mode=$mode default=${prefs.defaultComicMode} fresh=$fresh"
                }
            } catch(e:Exception) { "FAIL ${e.javaClass.simpleName}: ${e.message?.take(120)}" }
            android.util.Log.i("ReaderNavigationQA","RUN=$run $result")
            if(result.startsWith("PASS") && action=="open") {
                startActivity(Intent(this@ReaderNavigationQaActivity,com.will.lightreader.MainActivity::class.java)
                    .setAction(com.will.lightreader.ui.ReadingRequests.ACTION)
                    .putExtra(com.will.lightreader.ui.ReadingRequests.EXTRA_BOOK,"local:${id(format)}"))
                finish()
            } else setContentView(android.widget.TextView(this@ReaderNavigationQaActivity).apply { text=result;textSize=18f })
        }
    }
}

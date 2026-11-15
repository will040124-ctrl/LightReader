package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelStore
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.will.lightreader.source.script.*
import com.will.lightreader.source.SourceRegistry
import com.will.lightreader.data.LibraryRepository
import com.will.lightreader.ui.reader.*
import kotlinx.coroutines.*

class ComicOfflineQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val openOnline = intent.getBooleanExtra("openOnline", false)
        val container = if (openOnline) (application as com.will.lightreader.LightReaderApp).container else null
        val manager = container?.comicScripts ?: ComicScriptManager(this, true)
        val repo = container?.library ?: LibraryRepository(this)
        var report by mutableStateOf("验证漫画离线…")
        lifecycleScope.launch {
            if(intent.getBooleanExtra("inspectScroll",false)) {
                val key="comic-js-watercolor_demo:"+ComicScriptRoutes.encode("sample")
                val position=repo.position(key)
                val prefs=ReaderPrefs(this@ComicOfflineQaActivity)
                val chapter=position?.chapterUrl.orEmpty();val page=prefs.comicPageId(chapter).orEmpty()
                val fraction=prefs.comicScrollFraction(chapter,page)
                val ok=prefs.comicMode(key)=="scroll" && position?.blockIndex==1 && fraction in .05f.. .9f
                report=(if(ok)"PASS" else "FAIL")+" 连续阅读位置：page=${position?.blockIndex} fraction=$fraction mode=${prefs.comicMode(key)}"
                android.util.Log.i("ComicScrollQA","RUN="+intent.getStringExtra("qaRunId").orEmpty()+" "+report)
                return@launch
            }
            val models=ViewModelStore()
            var installedKey:String?=null;var createdBook:String?=null
            try {
                if(openOnline)require(manager.store.get("watercolor_demo")==null)
                val raw=withContext(Dispatchers.IO) { manager.download("http://127.0.0.1:18767/source.js") }
                val script=if(openOnline)raw else raw.replace("key=\"watercolor_demo\"","key=\"offline_${intent.getStringExtra("qaRunId").orEmpty().take(12)}\"")
                val pkg=manager.stage(script,"QA");require(manager.store.get(pkg.key)==null);manager.install(pkg);installedKey=pkg.key
                val source=ScriptComicSource(manager,pkg)
                val book=source.search("qa").single();val key=LibraryRepository.bookKey(book.sourceId,book.bookId)
                require(repo.book(key)==null) { "测试书已存在，拒绝覆盖" }
                repo.saveBook(book);createdBook=key;val chapters=source.episodes(book.bookUrl);repo.saveChapters(key,chapters)
                val url=chapters.first().chapterUrl
                if (openOnline) {
                    repo.savePosition(key, url, "第一话", 0, 0f)
                    val reader = android.content.Intent(this@ComicOfflineQaActivity, com.will.lightreader.MainActivity::class.java)
                        .setAction(com.will.lightreader.ui.ReadingRequests.ACTION)
                        .putExtra(com.will.lightreader.ui.ReadingRequests.EXTRA_BOOK, key)
                    startActivity(reader)
                    finish()
                    return@launch
                }
                suspend fun fresh()=com.will.lightreader.domain.ChapterContent(url,"第一话",emptyList(),comicPages=source.pages(url))
                var content=fresh();repo.saveChapterContent(url,content)
                require(repo.downloadedChapters(key).isEmpty())
                withContext(Dispatchers.IO) { manager.network.request("http://127.0.0.1:18767/fail-enable",listOf("127.0.0.1")) }
                var failed=false
                try { withContext(Dispatchers.IO) { repo.downloadComicChapter(url,content,source) } }
                catch(e:CancellationException) { throw e } catch(_:Exception) { failed=true }
                require(failed && repo.comicChapterContent(url)==null && repo.downloadedChapters(key).isEmpty())
                withContext(Dispatchers.IO) { manager.network.request("http://127.0.0.1:18767/fail-disable",listOf("127.0.0.1")) }
                content=fresh()
                val onlineReader = ReaderViewModel(repo,
                    SourceRegistry(listOf(com.will.lightreader.source.ComicSourceBridge(source))),
                    ReaderPrefs(this@ComicOfflineQaActivity), {}, book.sourceId, key, url)
                models.put("online-reader", onlineReader)
                withTimeout(15_000) { while (onlineReader.state.value.loading) delay(50) }
                require(onlineReader.state.value.error == null && onlineReader.supportsComicDownload)
                onlineReader.downloadCurrent()
                withTimeout(30_000) { while (onlineReader.state.value.offlineComicChapter != url) delay(50) }
                require(repo.downloadedChapters(key).size==1)
                val saved=withContext(Dispatchers.IO) { requireNotNull(repo.comicChapterContent(url)) }
                val broken=java.io.File(java.net.URI(saved.comicPages[1].localRef!!))
                val damaged=broken.readBytes().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() };broken.writeBytes(damaged)
                require(withContext(Dispatchers.IO) { repo.comicChapterContent(url) }==null)
                require(repo.downloadedChapters(key).isEmpty())
                withContext(Dispatchers.IO) { repo.downloadComicChapter(url,content,source) }
                withContext(Dispatchers.IO) { manager.network.request("http://127.0.0.1:18767/offline-enable",listOf("127.0.0.1")) }
                manager.remove(pkg.key)
                repo.savePosition(key,url,"第一话",1,.5f)
                val reader=ReaderViewModel(repo,SourceRegistry(emptyList()),ReaderPrefs(this@ComicOfflineQaActivity),{},book.sourceId,key,url)
                models.put("reader",reader)
                withTimeout(15000) { while(reader.state.value.loading)delay(50) }
                val state=reader.state.value
                require(state.error==null && state.startBlockIndex==1)
                val local=requireNotNull(state.content).comicPages
                require(local.size==3 && local.all { it.localRef!=null })
                local.forEach { page ->
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(java.io.File(java.net.URI(page.localRef!!)).absolutePath,bounds)
                    require(bounds.outWidth==600 && bounds.outHeight==900)
                }
                report="PASS 阅读器下载本话、失败不标记离线、部分续传、哈希损坏检测/修复、断网且移除源后本地读取、页序/进度恢复、三张本地图解码\n全部通过"
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { report="FAIL ${e.javaClass.simpleName}: ${e.message?.take(180)}" }
            finally {
                models.clear()
                if(!openOnline)withContext(NonCancellable+Dispatchers.IO) {
                    createdBook?.let { repo.removeBook(it) }
                    installedKey?.let { if(manager.store.get(it)?.origin=="QA")manager.remove(it) }
                }
            }
            android.util.Log.i("ComicOfflineQA","RUN="+intent.getStringExtra("qaRunId").orEmpty()+" "+report.replace("\n"," | "))
        }
        setContent { Text(report) }
    }
}

package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.will.lightreader.source.script.*
import kotlinx.coroutines.*

/** Disposable benchmark only: public API smoke check, no user library writes. */
class ComicPublicQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var report by mutableStateOf("验证公开漫画 API…")
        val manager=ComicScriptManager(this,true)
        lifecycleScope.launch {
            try {
                require(manager.store.get("mangadex_public")==null) { "测试源标识已存在，拒绝覆盖" }
                val script=withContext(Dispatchers.IO) { manager.download("http://127.0.0.1:18767/mangadex.js") }
                val pkg=manager.stage(script,"QA");manager.install(pkg)
                val source=ScriptComicSource(manager,pkg)
                val books=source.search("Yotsuba")
                require(books.isNotEmpty()) { "公开搜索没有返回结果" }
                val book=books.first()
                val info=source.detail(book.bookUrl)
                require(info.title.isNotBlank())
                val episodes=source.episodes(book.bookUrl)
                require(episodes.isNotEmpty())
                val pages=source.pages(episodes.first().chapterUrl)
                require(pages.isNotEmpty())
                val first=withContext(Dispatchers.IO) {
                    manager.network.request(pages.first().url,pkg.domains,limit=24L*1024*1024)
                }
                require(first.status==200 && first.bytes.isNotEmpty())
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeByteArray(first.bytes,0,first.bytes.size,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0) { "首图不是可解码的图片" }
                report="PASS 公开搜索、详情、Map 分卷目录 ${episodes.size} 话、图片列表 ${pages.size} 页、首图解码 ${bounds.outWidth}×${bounds.outHeight}\n全部通过"
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { report="FAIL ${e.javaClass.simpleName}: ${e.message?.take(180)}" }
            finally { withContext(NonCancellable+Dispatchers.IO) {
                if(manager.store.get("mangadex_public")?.origin=="QA")manager.remove("mangadex_public")
            } }
            android.util.Log.i("ComicPublicQA","RUN="+intent.getStringExtra("qaRunId").orEmpty()+" "+report.replace("\n"," | "))
        }
        setContent { Text(report) }
    }
}

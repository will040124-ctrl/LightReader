package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.data.ComicOfflineStore
import com.will.lightreader.domain.*
import com.will.lightreader.source.script.*
import kotlinx.coroutines.*
import java.io.File

/** Synthetic images only. Kept out of all production APKs. */
class ComicPerformanceQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val run=intent.getStringExtra("qaRunId").orEmpty()
            val manager=ComicScriptManager(this@ComicPerformanceQaActivity,true)
            val pkg=ComicScriptPackage("performance_qa","Performance QA","1.0.0","PerformanceQa",
                "class PerformanceQa extends ComicSource {}",listOf("127.0.0.1"),origin="QA")
            val folder=File(cacheDir,"performance-$run")
            val result=try {
                require(manager.store.get(pkg.key)==null);manager.install(pkg)
                withContext(Dispatchers.IO+com.will.lightreader.source.net.RequestPriority.Background) {
                    suspend fun measure(workers:Int):Long {
                        val url="http://127.0.0.1:18768/$run/$workers"
                        val content=ChapterContent(url,"Synthetic",emptyList(),comicPages=(1..12).map { ComicPage("$it","$url/$it.png") })
                        val store=ComicOfflineStore(File(folder,"$workers"),parallelism=workers,validateImage={ it.isNotEmpty() })
                        val start=System.nanoTime()
                        val saved=store.download(content) { manager.fetchImage(pkg,it.url,emptyMap(),null) }
                        require(saved.comicPages.map { it.id }==content.comicPages.map { it.id })
                        require(store.read(url)!=null)
                        return (System.nanoTime()-start)/1_000_000
                    }
                    val serial=measure(1);val parallel=measure(3)
                    require(parallel<serial) { "Concurrent transfer did not improve the synthetic test" }
                    coroutineScope { (1..8).map { async { manager.fetchImage(pkg,"http://127.0.0.1:18768/$run/dedup.png",emptyMap(),null) } }.awaitAll() }
                    try { manager.fetchImage(pkg,"http://127.0.0.1:18768/$run/denied.png",emptyMap(),null);error("403 accepted") }
                    catch(e:ComicImageFailure) { require(!e.transient) }
                    "PASS serialMs=$serial parallelMs=$parallel workers=3 order=preserved dedup=checked permanent403=checked"
                }
            } catch(e:Exception) { "FAIL ${e.javaClass.simpleName}: ${e.message?.take(160)}" }
            finally {
                withContext(NonCancellable+Dispatchers.IO) {
                    if(manager.store.get(pkg.key)?.origin=="QA")manager.remove(pkg.key)
                    if(folder.canonicalFile.parentFile==cacheDir.canonicalFile)folder.deleteRecursively()
                }
            }
            android.util.Log.i("ComicPerformanceQA","RUN=$run $result")
            setContentView(android.widget.TextView(this@ComicPerformanceQaActivity).apply { text=result;textSize=18f })
        }
    }
}

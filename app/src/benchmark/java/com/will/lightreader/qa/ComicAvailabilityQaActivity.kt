package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelStore
import com.will.lightreader.source.script.*
import com.will.lightreader.source.*
import com.will.lightreader.data.LibraryRepository
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.ui.book.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class ComicAvailabilityQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var report by mutableStateOf("验证空目录提示…")
        val manager=ComicScriptManager(this,true);val models=ViewModelStore()
        lifecycleScope.launch {
            try {
                require(manager.store.get("mangadex_public")==null)
                val script=withContext(Dispatchers.IO) { manager.download("http://127.0.0.1:18767/mangadex.js") }
                val pkg=manager.stage(script,"QA");manager.install(pkg)
                manager.saveSettings(pkg,buildJsonObject { put("language","en") })
                val id="6fef1f74-a0ad-4f0d-99db-d32a7cd24098";val url=ComicScriptRoutes.book(pkg.sourceId,id)
                val source=ScriptComicSource(manager,pkg)
                val book=BookBrief(pkg.sourceId,ComicScriptRoutes.encode(id),url,"炎拳")
                BookOpening.prime(book)
                val repo=LibraryRepository(this@ComicAvailabilityQaActivity)
                val vm=BookViewModel(SourceRegistry(listOf(ComicSourceBridge(source))),repo,{},this,book.sourceId,url)
                models.put("book",vm)
                withTimeout(40000) { while(vm.state.value.loading)delay(50) }
                val failure=vm.state.value.error as? SourceError.ContentUnavailable ?: error("预期无内容错误，实际：${vm.state.value.error?.javaClass?.simpleName} ${vm.state.value.error?.message}")
                require(failure.reason.contains("英语") && failure.reason.contains("捷克语"))
                manager.saveSettings(pkg,buildJsonObject { put("language","all") })
                val chapters=source.episodes(url)
                require(chapters.isNotEmpty() && chapters.any { it.volume.contains("捷克语") })
                report="PASS 炎拳英语不可读时显示明确错误；全部可用语言返回 ${chapters.size} 话且按语言分卷\n全部通过"
            } catch(e:TimeoutCancellationException) { report="FAIL 验证执行超时" }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { report="FAIL ${e.javaClass.simpleName}: ${e.message?.take(200)}" }
            finally { models.clear() }
            android.util.Log.i("ComicAvailabilityQA","RUN="+intent.getStringExtra("qaRunId").orEmpty()+" "+report.replace("\n"," | "))
        }
        setContent { Text(report) }
    }
}

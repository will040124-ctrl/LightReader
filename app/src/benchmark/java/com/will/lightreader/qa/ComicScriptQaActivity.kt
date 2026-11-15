package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.will.lightreader.source.script.ComicScriptManager
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class ComicScriptQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val manager=ComicScriptManager(this,true)
        var report by mutableStateOf("验证脚本引擎中…")
        lifecycleScope.launch {
                try {
                    val script="""class Sample extends ComicSource { key='sample';name='Sample';version='1.0.0';domains=['127.0.0.1'];search={load:async()=>JSON.parse((await Network.get('http://127.0.0.1:18767/search')).body)};comic={loadInfo:async()=>({title:'Sample'}),loadEp:async()=>({images:[]})}; }"""
                    val metadata=manager.stage(script,"QA")
                    require(metadata.key=="sample")
                    val result=manager.engine.evaluate(script,"Sample","__lrSource.search.load('qa',1,[])",listOf("127.0.0.1")).jsonObject
                    require(result["comics"]!!.jsonArray.size==1)
                    report="PASS 脚本元数据、Promise 与受控网络桥接"
                    var timedOut=false
                    try { manager.stage("while(true) {}\n$script","QA") }
                    catch(_:TimeoutCancellationException) { timedOut=true }
                    require(timedOut)
                    require(manager.stage(script,"QA").key=="sample")
                    report+="\nPASS 无限循环终止及引擎恢复"
                    var denied=false
                    try { manager.engine.evaluate(script,"Sample","Network.get('http://127.0.0.1:18767/private')",emptyList()) }
                    catch(e:CancellationException) { throw e }
                    catch(_:Exception) { denied=true }
                    require(denied)
                    report+="\nPASS 未声明域名访问拒绝\n全部通过"
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) { report="FAIL ${e.javaClass.simpleName}: ${e.message?.take(160)}" }
            android.util.Log.i("ComicScriptQA", "RUN=" + intent.getStringExtra("qaRunId").orEmpty() + " " + report.replace("\n", " | "))
        }
        setContent { LightReaderTheme {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(32.dp)) { Text(report) }
        } }
    }
}

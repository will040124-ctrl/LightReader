package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.PersistentCookieJar
import com.will.lightreader.source.net.RequestPriority
import com.will.lightreader.source.web.WebViewFetcher
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.*
import java.io.File

/** Synthetic localhost checks, included only in benchmark / optimized QA builds. */
class WebFetchQaActivity : ComponentActivity() {
    private var fetcher: WebViewFetcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var report by mutableStateOf("验证浏览器取页…")
        val engine = WebViewFetcher(PersistentCookieJar(File(cacheDir, "web-fetch-qa-cookies.json")),
            captureDiagnostics = false) { this }
        fetcher = engine
        lifecycleScope.launch {
            try {
                val root = "http://127.0.0.1:18767"
                val first = engine.getText("$root/web-first")
                check(first.browserProcessed && "WEB_FIRST_QA" in first.text)
                val warm = withContext(RequestPriority.Background) { engine.getText("$root/web-first") }
                check(warm.browserProcessed)
                val second = withContext(RequestPriority.Background) { engine.getText("$root/web-second") }
                check(!second.browserProcessed && "WEB_SECOND_QA" in second.text && "WEB_FIRST_QA" !in second.text)
                try {
                    engine.getText("$root/web-blocked")
                    error("Block page returned as content")
                } catch (e: SourceError.ChallengeRequired) { check(e.accessBlocked) }
                try {
                    engine.getText("$root/web-unavailable")
                    error("HTTP failure returned as content")
                } catch (_: SourceError.Network) { }
                report = "PASS 页面归属、后台 JavaScript 桥接、站点拦截与 HTTP 错误分类"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report = "FAIL ${e.javaClass.simpleName}: ${e.message?.take(150)}" }
            android.util.Log.i("WebFetchQA", "RUN=" + intent.getStringExtra("qaRunId").orEmpty() + " " + report)
        }
        setContent { LightReaderTheme { Text(report) } }
    }

    override fun onDestroy() {
        fetcher?.release()
        fetcher = null
        super.onDestroy()
    }
}

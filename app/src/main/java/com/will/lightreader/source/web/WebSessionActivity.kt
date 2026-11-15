package com.will.lightreader.source.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.will.lightreader.LightReaderApp
import com.will.lightreader.source.net.HttpClient
import com.will.lightreader.ui.SystemBarsAppearance
import com.will.lightreader.ui.theme.LightReaderTheme

/**
 * WebView 登录/过验证屏：Cloudflare 挑战、wenku8 登录都走这里。
 * 只复用用户自己的浏览器会话（登录/过挑战），不绕付费墙。关闭时 cookie 同步进 PersistentCookieJar。
 */
class WebSessionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        fun intent(context: Context, url: String, title: String = "登录 / 验证"): Intent =
            Intent(context, WebSessionActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // 与主应用一致：内容延伸到状态栏下，顶部栏避开安全区
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "登录 / 验证"
        setContent {
            LightReaderTheme {
                var currentHost by remember { mutableStateOf(runCatching { java.net.URI(url).host }.getOrNull().orEmpty()) }
                val dark = com.will.lightreader.ui.theme.LocalAppDark.current
                SystemBarsAppearance(lightContent = dark)
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                    com.will.lightreader.ui.components.WatercolorChoice(
                        text = "完成：保存「$title」登录态并返回",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { syncCookies(url); finish() },
                    )
                    Text("当前网站：$currentHost",modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall)
                    WebViewScreen(url) { changed -> currentHost=runCatching { java.net.URI(changed).host }.getOrNull().orEmpty() }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { intent.getStringExtra(EXTRA_URL)?.let(::syncCookies) }
        super.onDestroy()
    }

    private fun syncCookies(url: String) {
        val raw = CookieManager.getInstance().getCookie(url) ?: return
        val jar = (application as LightReaderApp).container.cookieJar
        jar.importRaw(java.net.URI(url).host, raw)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewScreen(url: String, onUrlChanged: (String) -> Unit) {
    // P0 修复：onRelease 销毁 WebView，防 AndroidView 泄漏
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = HttpClient.UA
                // Cloudflare Turnstile 跑在第三方 iframe（challenges.cloudflare.com），
                // WebView 默认禁第三方 cookie 会导致验证无限转圈；放行才能完成挑战。
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
                webViewClient = object:WebViewClient() {
                    override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) { url?.let(onUrlChanged) }
                    override fun onPageFinished(view:WebView?,url:String?) { url?.let(onUrlChanged) }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

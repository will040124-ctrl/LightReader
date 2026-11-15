package com.will.lightreader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.will.lightreader.ui.LightReaderNav
import com.will.lightreader.ui.VolumeKeyBridge
import com.will.lightreader.ui.theme.LightReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LightReaderApp).container
        container.activityProvider = { this }   // WebView 引擎的上下文供给
        handleImportIntent(intent)
        // 微信式：内容延伸到状态栏/导航栏下方。导航栏透明并关掉系统对比 scrim，
        // 否则浅色页面底部会多出一条比页面更白的横带。
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            LightReaderTheme {
                // 背景铺满到底（含手势区，去掉底部白边）；安全区避让交给各屏内容自行处理
                // （阅读页已用 navigationBarsPadding 避让控制栏/把手，背景 fillMaxSize 到底）
                Surface(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                    ) {
                        LightReaderNav(container)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /** 文件管理器「打开方式 / 分享到轻阅」→ 排队导入，由书架页消费。 */
    @Suppress("DEPRECATION")
    private fun handleImportIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == com.will.lightreader.ui.ReadingRequests.ACTION) {
            com.will.lightreader.ui.ReadingRequests.pending.value = com.will.lightreader.ui.ReadingRequests.Request(
                intent.getStringExtra(com.will.lightreader.ui.ReadingRequests.EXTRA_BOOK).orEmpty())
            // Consume extras so recreation does not reopen an already handled widget request.
            intent.removeExtra(com.will.lightreader.ui.ReadingRequests.EXTRA_BOOK)
            intent.action = Intent.ACTION_MAIN
            return
        }
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        com.will.lightreader.ui.ImportRequests.pending.value = uri
    }

    // 音量键翻页：阅读器在前台时拦截（音量上=上一页，音量下=下一页），否则走系统音量
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP ->
                if (VolumeKeyBridge.handler?.invoke(-1) == true) return true
            KeyEvent.KEYCODE_VOLUME_DOWN ->
                if (VolumeKeyBridge.handler?.invoke(1) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // P0 修复：销毁时释放 WebView + 置空 Activity 引用 + 清音量键桥，防 Activity/WebView 泄漏
    override fun onDestroy() {
        val container = (application as LightReaderApp).container
        container.webFetcher.release()
        container.accountWebFetcher.release()
        container.activityProvider = { null }
        VolumeKeyBridge.clear()
        super.onDestroy()
    }
}

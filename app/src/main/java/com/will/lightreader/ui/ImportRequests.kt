package com.will.lightreader.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 从文件管理器「用轻阅打开 / 分享到轻阅」送进来的 Uri。
 * MainActivity 写入 → HomeScreen 切到书架 → ShelfScreen 消费并走导入管线。
 */
object ImportRequests {
    val pending = MutableStateFlow<Uri?>(null)
}

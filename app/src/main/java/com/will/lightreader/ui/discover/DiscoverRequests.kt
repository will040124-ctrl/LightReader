package com.will.lightreader.ui.discover

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 跨页面「去发现页看这个标签」的请求通道。
 * 详情页点标签 → 写入 pendingTag → Nav 弹回 home；HomeScreen 消费后清空并切到发现页。
 */
object DiscoverRequests {
    data class Tag(val sourceId: String, val label: String)
    val pendingTag = MutableStateFlow<Tag?>(null)
}

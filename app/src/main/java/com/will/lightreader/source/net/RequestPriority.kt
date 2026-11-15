package com.will.lightreader.source.net

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 请求优先级标记：挂在协程上下文里，取页层据此给后台任务（离线下载）降速让路。
 * 交互请求不加标记——用户正在等的页面永远先走。
 */
class RequestPriority private constructor(val background: Boolean) :
    AbstractCoroutineContextElement(RequestPriority) {

    companion object Key : CoroutineContext.Key<RequestPriority> {
        val Background = RequestPriority(background = true)
    }
}

/** 当前协程是不是后台任务（默认交互）。 */
val CoroutineContext.isBackgroundRequest: Boolean
    get() = this[RequestPriority]?.background == true

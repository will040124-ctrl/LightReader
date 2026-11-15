package com.will.lightreader.ui

/**
 * 音量键翻页桥：MainActivity 拦截音量键 → 转发给阅读器翻页。
 * handler 返回 true = 已消费（阅读器在前台）；null/false = 不拦截（调系统音量）。
 * 约定：音量下 = 下一页，音量上 = 上一页（-1=上一页，+1=下一页）。
 *
 * 生命周期安全：Activity onStop/onDestroy 时必须调 [clear]，否则匿名 Activity/VM
 * 闭包被单例持有会泄漏 Activity、在前台外仍触发翻页副作用。
 */
object VolumeKeyBridge {
    @Volatile
    var handler: ((Int) -> Boolean)? = null

    fun clear() {
        handler = null
    }
}

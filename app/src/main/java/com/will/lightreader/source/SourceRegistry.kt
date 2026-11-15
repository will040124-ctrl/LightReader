package com.will.lightreader.source

class SourceRegistry(sources: List<NovelSource>) {
    @Volatile private var byId = sources.associateBy { it.id }
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    @Synchronized fun replaceScripts(sources: List<NovelSource>) {
        require(sources.all { it.id.startsWith("comic-js-") } && sources.distinctBy { it.id }.size==sources.size)
        byId=byId.filterKeys { !it.startsWith("comic-js-") } + sources.associateBy { it.id }
        revision.value++
    }
    fun all(): List<NovelSource> = byId.values.toList()
    fun get(id: String): NovelSource = byId[id] ?: throw IllegalArgumentException("未知书源：$id")
    /** 宽容查找：旧版书源的书还留在书架里时返回 null，由调用方降级提示而不是崩溃。 */
    fun find(id: String): NovelSource? = byId[id]
}

package com.will.lightreader.source.script

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A package is replaced atomically; source removal never touches library records. */
class ComicScriptStore(private val root: File) {
    private val json=Json { ignoreUnknownKeys=false }
    @Synchronized fun all(): List<ComicScriptPackage> = root.listFiles().orEmpty().filter { it.name.endsWith(".json") && !it.name.endsWith(".previous.json") }
        .mapNotNull { runCatching { read(it).also(ComicScriptPackage::validate) }.getOrNull() }.sortedBy { it.name }
    @Synchronized fun get(key: String)=all().firstOrNull { it.key==key }
    private fun path(key: String): File {
        require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{1,63}")))
        return File(root,"$key.json")
    }
    private fun read(file: File)=json.decodeFromString(ComicScriptPackage.serializer(),file.readText())
    private fun atomic(file: File, value: ComicScriptPackage) {
        root.mkdirs()
        val temp=File(root,file.name+".tmp")
        temp.writeText(json.encodeToString(ComicScriptPackage.serializer(),value))
        try { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        catch(_:java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
    }
    @Synchronized fun install(value: ComicScriptPackage) {
        value.validate()
        require(all().none { it.key!=value.key && it.sourceId==value.sourceId }) { "源标识大小写与现有源冲突" }
        require(get(value.key)!=null || all().size<32) { "最多安装 32 个漫画源" }
        var target=value
        get(value.key)?.let { old ->
            require(newerComicVersion(value.version,old.version)) { "新版本必须高于当前版本" }
            atomic(File(root,"${value.key}.previous.json"),old)
            target=value.copy(enabled=old.enabled)
        }
        atomic(path(value.key),target)
    }
    @Synchronized fun setEnabled(key: String, enabled: Boolean) {
        val value=requireNotNull(get(key)) { "源已不存在" };atomic(path(key),value.copy(enabled=enabled))
    }
    @Synchronized fun setDomains(key:String,domains:List<String>) {
        val value=requireNotNull(get(key)) { "源已不存在" }.copy(domains=domains)
        value.validate();atomic(path(key),value)
    }
    @Synchronized fun rollback(key: String) {
        val current=requireNotNull(get(key)) { "源已不存在" }
        val file=File(root,"$key.previous.json")
        require(file.isFile) { "没有可回退的版本" }
        val previous=read(file).also(ComicScriptPackage::validate)
        require(previous.key==key)
        atomic(path(key),previous.copy(enabled=current.enabled,domains=current.domains))
    }
    @Synchronized fun remove(key: String) {
        val file=path(key);require(!file.exists() || file.delete()) { "移除失败，请重试" }
        File(root,"$key.previous.json").delete()
    }
}

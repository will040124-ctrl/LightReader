package com.will.lightreader.source.script

import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Source-scoped JSON only; never reads application preferences or another source's state. */
class ComicScriptDataStore(private val root:File) {
    private fun path(key:String):File {
        require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{1,63}"))) { "源标识无效" }
        return File(root,"$key.json")
    }
    @Synchronized fun read(key:String):JsonObject {
        val file=path(key)
        if(!file.exists())return buildJsonObject { put("data",JsonObject(emptyMap()));put("settings",JsonObject(emptyMap())) }
        require(file.length()<=MAX_BYTES) { "本源存储超过大小限制，原文件保留" }
        return try { Json.parseToJsonElement(file.readText()).jsonObject.also(::validate) }
        catch(e:Exception) { throw java.io.IOException("本源存储无法读取，原文件保留；可移除该源后重新导入",e) }
    }
    @Synchronized fun write(key:String,state:JsonObject) {
        validate(state)
        val file=path(key);root.mkdirs()
        val temp=File(root,"$key.tmp")
        try {
            temp.writeText(state.toString())
            try { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch(_:java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }
    @Synchronized fun remove(key:String) { val file=path(key);require(!file.exists()||file.delete()) { "源数据清除失败，请重试" } }
    companion object {
        const val MAX_BYTES=128L*1024
        fun validate(state:JsonObject) {
            require(state.keys==setOf("data","settings")) { "源存储格式无效" }
            for(kind in listOf("data","settings")) {
                val values=state[kind] as? JsonObject ?: error("源存储格式无效")
                require(values.size<=128 && values.keys.all { it.length in 1..128 && it.none(Char::isISOControl) }) { "源存储条目超出限制" }
            }
            require(state.toString().toByteArray().size<=MAX_BYTES) { "本源存储超过 128 KB" }
        }
    }
}

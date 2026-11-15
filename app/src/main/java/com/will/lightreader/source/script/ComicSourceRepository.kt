package com.will.lightreader.source.script

import kotlinx.serialization.json.*
import java.net.URI

fun parseComicRepository(text:String,url:String,network:ComicScriptNetwork):List<ComicRepoEntry> {
        val array=Json.parseToJsonElement(text) as? JsonArray ?: error("仓库索引必须是 JSON 数组")
        require(array.size in 1..300) { "仓库条目过多或为空" }
        val base=URI(url).resolve(".")
        return array.map { item ->
            val entry=item.jsonObject
            fun field(k:String)=entry[k]?.jsonPrimitive?.contentOrNull
            val a=field("fileName");val b=field("filename");val absolute=field("url")
            require(a==null || b==null || a==b) { "仓库文件名字段冲突" }
            val relative=a ?: b
            require((absolute==null)!=(relative==null)) { "仓库条目必须只指定一个脚本地址" }
            val target=absolute ?: relative!!.let {
                require(it.matches(Regex("[A-Za-z0-9_./-]+")) && !it.startsWith('/') && it.split('/').none { p->p==".." }) { "仓库脚本路径无效" }
                base.resolve(it).toString()
            }
            val host=URI(target).host ?: error("脚本地址无效")
            network.validate(target,listOf(host))
            ComicRepoEntry(field("name")?.take(80) ?: "未命名源",target,field("version").orEmpty())
        }.distinctBy { it.url }
    }

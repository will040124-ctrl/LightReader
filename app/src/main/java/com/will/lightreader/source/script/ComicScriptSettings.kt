package com.will.lightreader.source.script

import kotlinx.serialization.json.*

data class ComicSettingOption(val value:JsonPrimitive,val label:String)
data class ComicSettingField(val key:String,val title:String,val type:String,
    val default:JsonElement=JsonNull,val options:List<ComicSettingOption> = emptyList(),val unsupported:String?=null)
data class ComicSettingsSnapshot(val fields:List<ComicSettingField>,val values:JsonObject)

fun parseComicSettings(result:JsonObject):ComicSettingsSnapshot {
    val definitions=result["definitions"] as? JsonObject ?: error("源设置格式无效")
    require(definitions.size<=64) { "源设置超过 64 项" }
    val values=result["values"] as? JsonObject ?: JsonObject(emptyMap())
    val fields=definitions.map { (key,raw) ->
        val field=raw as? JsonObject
        val title=(field?.get("title") as? JsonPrimitive)?.contentOrNull?.take(100)?.ifBlank { key } ?: key
        val type=(field?.get("type") as? JsonPrimitive)?.contentOrNull.orEmpty()
        try {
            require(key.length in 1..128 && key.none(Char::isISOControl)) { "设置标识无效" }
            require(type in setOf("select","switch","input")) { "此设置类型暂不支持：${type.ifBlank { "未声明" }}" }
            val options=if(type=="select") {
                val list=field?.get("options") as? JsonArray ?: error("选项列表无效")
                require(list.size in 1..64) { "选项数须为 1–64" }
                list.map { item ->
                    val option=item.jsonObject;val value=option["value"] as? JsonPrimitive ?: error("选项值无效")
                    require(value !is JsonNull && value.content.length<=2048) { "选项值无效" }
                    ComicSettingOption(value,(option["text"] as? JsonPrimitive)?.contentOrNull?.take(120) ?: value.content)
                }.also { require(it.distinctBy { option->option.value }.size==it.size) { "选项值重复" } }
            } else emptyList()
            val default=field?.get("default") ?: when(type) {
                "switch"->JsonPrimitive(false);"input"->JsonPrimitive("");else->options.first().value
            }
            val value=ComicSettingField(key,title,type,default,options)
            validateComicSettingValues(listOf(value),buildJsonObject { put(key,default) })
            value
        } catch(e:Exception) { ComicSettingField(key,title,type,unsupported=e.message ?: "设置格式无效") }
    }
    return ComicSettingsSnapshot(fields,buildJsonObject {
        fields.forEach { put(it.key,values[it.key] ?: it.default) }
    })
}

/** Reject malformed edits before script execution; regex validation runs inside the timed JS isolate. */
fun validateComicSettingValues(fields:List<ComicSettingField>,values:JsonObject) {
    val byKey=fields.associateBy { it.key }
    for((key,value) in values) {
        val field=byKey[key] ?: error("源设置已变化，请重新打开")
        require(field.unsupported==null) { "${field.title}：此设置暂不支持" }
        val primitive=value as? JsonPrimitive ?: error("${field.title}：设置值无效")
        val valid=when(field.type) {
            "select"->field.options.any { it.value==value }
            "switch"->!primitive.isString && primitive.booleanOrNull!=null
            "input"->primitive.isString && primitive.content.length<=2048
            else->false
        }
        require(valid) { "${field.title}：请选择或输入有效值" }
    }
}

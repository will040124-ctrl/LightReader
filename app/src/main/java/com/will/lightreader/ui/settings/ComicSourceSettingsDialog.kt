package com.will.lightreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.will.lightreader.source.script.*
import com.will.lightreader.ui.components.WatercolorChoice
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicSourceSettingsDialog(manager:ComicScriptManager,source:ComicScriptPackage,onDismiss:()->Unit,onSaved:()->Unit) {
    val scope=rememberCoroutineScope()
    var snapshot by remember(source) { mutableStateOf<ComicSettingsSnapshot?>(null) }
    var values by remember(source) { mutableStateOf(JsonObject(emptyMap())) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    suspend fun load() { snapshot=manager.settings(source);values=requireNotNull(snapshot).values }
    fun action(block:suspend ()->Unit) { scope.launch {
        busy=true;error=null
        try { withContext(Dispatchers.IO) { block() } }
        catch(e:TimeoutCancellationException) { error="源设置执行超时，未完成保存。" }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { error=(e.message ?: "设置操作失败").take(240) }
        finally { busy=false }
    } }
    LaunchedEffect(source) {
        try { withContext(Dispatchers.IO) { load() } }
        catch(e:TimeoutCancellationException) { error="读取源设置超时，请关闭后重试。" }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { error=(e.message ?: "无法读取源设置").take(240) }
        finally { busy=false }
    }
    fun change(key:String,value:JsonElement) { values=JsonObject(values.toMutableMap().apply { put(key,value) }) }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("${source.name} · 源设置")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("保存后用于本源的新请求。域名选项不会自动扩大已授权的访问范围。",style=MaterialTheme.typography.bodySmall)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(snapshot?.fields?.isEmpty()==true)Text("此源未声明可调整的设置。")
            snapshot?.fields?.forEach { field ->
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(sourceSettingLabel(field.title),style=MaterialTheme.typography.titleSmall)
                    if(field.unsupported!=null)Text(
                        if (field.type == "callback") {
                            if (field.title.contains("domain", ignoreCase = true)) "线路检测请返回“访问域名”，使用“检测当前线路”。"
                            else "此源的操作按钮暂未兼容，请在来源网站完成。"
                        } else field.unsupported,
                        style=MaterialTheme.typography.bodySmall,
                        color=if(field.type == "callback") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    else when(field.type) {
                        "select"->FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            field.options.forEach { option -> WatercolorChoice(option.label,active=values[field.key]==option.value,enabled=!busy) { change(field.key,option.value) } }
                        }
                        "switch"->Switch(modifier=Modifier.semantics { contentDescription = sourceSettingLabel(field.title) },checked=(values[field.key] as? JsonPrimitive)?.booleanOrNull==true,enabled=!busy,onCheckedChange={change(field.key,JsonPrimitive(it))})
                        "input"->OutlinedTextField(value=(values[field.key] as? JsonPrimitive)?.contentOrNull.orEmpty(),onValueChange={if(it.length<=2048)change(field.key,JsonPrimitive(it))},
                            enabled=!busy,label={Text(sourceSettingLabel(field.title))},modifier=Modifier.fillMaxWidth(),maxLines=3)
                    }
                }
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            if(snapshot!=null)TextButton(enabled=!busy,onClick={
                values=JsonObject(requireNotNull(snapshot).fields.associate { it.key to it.default });reset=true;error=null
            }) { Text("恢复默认设置") }
            if(reset)Text("已恢复默认值，保存后生效；取消将保留原设置。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=!busy&&snapshot!=null,onClick={action {
        val editable=requireNotNull(snapshot).fields.filter { it.unsupported==null }.map { it.key }.toSet()
        manager.saveSettings(source,JsonObject(values.filterKeys { it in editable }),reset)
        withContext(Dispatchers.Main) { onSaved() }
    }}) { Text("保存设置") }},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("取消")}})
}

private fun sourceSettingLabel(title: String): String = when (title.trim().lowercase(java.util.Locale.ROOT)) {
    "api domain" -> "API 线路"
    "image stream" -> "图片分流"
    "refresh domain list" -> "刷新线路列表"
    "refresh domain list on startup" -> "启动时刷新线路"
    "favorite order" -> "收藏排序"
    "daily check-in task" -> "每日签到"
    else -> title
}

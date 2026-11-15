package com.will.lightreader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.will.lightreader.source.script.*
import com.will.lightreader.ui.components.WatercolorChoice
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicSourcesPanel(manager:ComicScriptManager) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val revision by manager.revision.collectAsState()
    var installed by remember { mutableStateOf(emptyList<ComicScriptPackage>()) }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableStateOf<ComicScriptPackage?>(null) }
    var domains by remember { mutableStateOf("") }
    var lineHint by remember { mutableStateOf<String?>(null) }
    var repository by remember { mutableStateOf(emptyList<ComicRepoEntry>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ComicScriptPackage?>(null) }
    var configuring by remember { mutableStateOf<ComicScriptPackage?>(null) }
    var granting by remember { mutableStateOf<ComicScriptPackage?>(null) }
    LaunchedEffect(revision) { installed=withContext(Dispatchers.IO) { manager.store.all() } }
    fun action(block:suspend ()->Unit) {
        job=scope.launch {
            busy=true
            try { block() }
            catch(e:TimeoutCancellationException) { message="脚本或网络执行超时，原有源保留。" }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { message=(e.message ?: "操作未完成").take(240) }
            finally { busy=false }
        }
    }
    suspend fun stage(script:String,origin:String) {
        val value=withContext(Dispatchers.IO) { manager.stage(script,origin) }
        pending=value;domains=value.domains.joinToString("\n");lineHint=null
    }
    fun hosts() = domains.split(Regex("[\\s,，]+")).map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
    fun checkLines(source: ComicScriptPackage) = action {
        lineHint = "正在检测动态线路和图片分流…"
        try {
            val result = manager.checkJmLines(source)
            val merged = (hosts() + result.domains).distinct()
            domains = merged.joinToString("\n")
            val limitHint = if (merged.size > 16) "域名超过 16 个，请先删去不用的地址。" else ""
            lineHint = "线路 ${result.available.joinToString("、")} 有响应。确认域名后，在源设置把 Api Domain 选为 ${result.available.first()}，并开启启动时刷新域名列表。$limitHint"
        } catch (e: CancellationException) {
            lineHint = null
            throw e
        } catch (e: Exception) {
            lineHint = "线路检测失败。${e.message.orEmpty().take(100)}"
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)action {
            val script=withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=1_048_576) { "源文件超过 1 MB" };out.write(buffer,0,n) }
                    out.toString("UTF-8")
                } ?: error("源文件无法读取")
            }
            stage(script,"本地文件")
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("添加漫画源",style=MaterialTheme.typography.titleMedium)
        Text("支持 Venera 源的基础阅读、MD5 / AES-ECB 转换和分块图片重排。网络访问仅限你确认的域名，账号登录暂未开放。",style=MaterialTheme.typography.bodySmall)
        if(!manager.engine.supported())Text("当前系统不支持安全脚本引擎，请更新 Android System WebView。",color=MaterialTheme.colorScheme.error)
        OutlinedTextField(value=link,onValueChange={link=it},enabled=!busy,singleLine=true,
            label={Text("源脚本或仓库索引 HTTPS 链接")},modifier=Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            WatercolorChoice("读取链接",enabled=!busy&&link.isNotBlank()) { action {
                val address=link.trim()
                val text=withContext(Dispatchers.IO) { manager.download(address) }
                if(text.trimStart().startsWith("["))repository=withContext(Dispatchers.IO) { manager.repository(text,address) }
                else stage(text,address)
            } }
            WatercolorChoice("导入源文件",enabled=!busy) { picker.launch(arrayOf("*/*")) }
            if(busy)WatercolorChoice("取消") { job?.cancel() }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(installed.isEmpty())Text("尚未安装漫画源。",style=MaterialTheme.typography.bodySmall)
        installed.forEach { source ->
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${source.name} · ${source.version}",style=MaterialTheme.typography.titleSmall)
                Text(if(source.enabled)"已启用 · 在发现页选择此源后搜索" else "已停用 · 收藏和进度保留",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    WatercolorChoice(if(source.enabled)"停用" else "启用",enabled=!busy) { action { withContext(Dispatchers.IO) { manager.enable(source.key,!source.enabled) } } }
                    WatercolorChoice("源设置",enabled=!busy) { configuring=source }
                    WatercolorChoice("访问域名",enabled=!busy) { granting=source;domains=source.domains.joinToString("\n");lineHint=null }
                    if(source.origin.startsWith("https://"))WatercolorChoice("检查更新",enabled=!busy) { action { stage(withContext(Dispatchers.IO) { manager.download(source.origin) },source.origin) } }
                    WatercolorChoice("回退版本",enabled=!busy) { action { withContext(Dispatchers.IO) { manager.rollback(source.key) };message="已回退源版本。" } }
                    WatercolorChoice("移除",enabled=!busy) { removing=source }
                }
            }
        }
    }
    configuring?.let { source -> ComicSourceSettingsDialog(manager,source,onDismiss={configuring=null},onSaved={configuring=null;message="源设置已保存。"}) }
    granting?.let { source -> AlertDialog(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=1f),modifier=Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=1f),RoundedCornerShape(28.dp)),onDismissRequest={if(!busy)granting=null},title={Text("${source.name} · 访问域名")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("普通域名只授权自身，*.example.com 授权其子域名。请核对范围后保存。")
            if (JmLineCheck.supports(source)) {
                WatercolorChoice("检测当前线路", enabled = !busy) { checkLines(source) }
                lineHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedTextField(value=domains,onValueChange={domains=it},enabled=!busy,label={Text("允许域名，每行一个")},modifier=Modifier.fillMaxWidth().heightIn(max=280.dp),minLines=3)
        }
    },confirmButton={TextButton(enabled=!busy,onClick={action {
        val hosts=hosts()
        withContext(Dispatchers.IO) { manager.permissions(source.key,hosts) }
        granting=null;message="访问域名已保存。"
    }}){Text("保存授权")}},dismissButton={TextButton(enabled=!busy,onClick={granting=null}){Text("取消")}}) }
    pending?.let { source ->
        AlertDialog(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=1f),modifier=Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=1f),RoundedCornerShape(28.dp)),onDismissRequest={if(!busy)pending=null},title={Text("预览漫画源")},text={
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${source.name} · ${source.version}\n标识：${source.key}\n来源：${source.origin}")
                installed.firstOrNull{it.key==source.key}?.let { Text("当前版本 ${it.version}，确认后替换；可回退。") }
                Text("普通域名仅授权自身；*.example.com 授权其子域名，不含主域名。通配符会扩大范围，请核对网站和图片域名。",style=MaterialTheme.typography.bodySmall)
                if (JmLineCheck.supports(source)) {
                    WatercolorChoice("检测当前线路", enabled = !busy) { checkLines(source) }
                    lineHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedTextField(value=domains,onValueChange={domains=it},enabled=!busy,label={Text("允许域名，每行一个")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Text("安装后运行源脚本。此版本提供基础接口，部分 Venera 源仍需调整。",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(enabled=!busy,onClick={ action {
            val value=source.copy(domains=hosts())
            withContext(Dispatchers.IO) { value.validate();manager.install(value) }
            pending=null;message="漫画源已安装，可在发现页选择并搜索。"
        } }) { Text("确认安装") }},dismissButton={TextButton(enabled=!busy,onClick={pending=null}){Text("取消")}})
    }
    if(repository.isNotEmpty())AlertDialog(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=1f),modifier=Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=1f),RoundedCornerShape(28.dp)),onDismissRequest={if(!busy)repository=emptyList()},title={Text("选择仓库中的源")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            repository.forEach { entry -> TextButton(enabled=!busy,onClick={action {
                stage(withContext(Dispatchers.IO) { manager.download(entry.url) },entry.url)
                repository=emptyList()
            }}){Text("${entry.name} · ${entry.version}")} }
        }
    },confirmButton={TextButton(enabled=!busy,onClick={repository=emptyList()}){Text("关闭")}})
    removing?.let { source -> AlertDialog(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=1f),modifier=Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=1f),RoundedCornerShape(28.dp)),onDismissRequest={removing=null},title={Text("移除漫画源？")},text={Text("将移除 ${source.name} 的源文件及本源存储，书架和阅读进度保留。")},
        confirmButton={TextButton(enabled=!busy,onClick={action { withContext(Dispatchers.IO) { manager.remove(source.key) };removing=null }}){Text("移除源")}},
        dismissButton={TextButton(enabled=!busy,onClick={removing=null}){Text("取消")}}) }
    message?.let { text -> AlertDialog(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=1f),modifier=Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=1f),RoundedCornerShape(28.dp)),onDismissRequest={message=null},title={Text("漫画源")},text={Text(text)},confirmButton={TextButton(onClick={message=null}){Text("知道了")}}) }
}

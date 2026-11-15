package com.will.lightreader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.will.lightreader.data.AccountLibraryImporter
import com.will.lightreader.data.LightReaderDb
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.account.AccountLibrarySnapshot
import com.will.lightreader.source.account.AccountLibrarySource
import com.will.lightreader.source.web.WebSessionActivity
import com.will.lightreader.ui.components.WatercolorChoice
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountLibraryPanel(sources: List<AccountLibrarySource>) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val importer = remember(context) { AccountLibraryImporter(context) }
    var sourceId by remember { mutableStateOf(sources.firstOrNull()?.sourceId.orEmpty()) }
    val source = sources.firstOrNull { it.sourceId == sourceId }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AccountLibrarySnapshot?>(null) }
    var existing by remember { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var importPositions by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        message = "已返回轻阅。点击「读取网站书架」检查登录状态并预览记录。"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("把网站账号的收藏带回轻阅，先预览再导入。已有书籍和本地进度保留。",style=MaterialTheme.typography.bodyMedium)
        if (sources.size > 1) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            sources.forEach { candidate -> WatercolorChoice(candidate.displayName,active=sourceId==candidate.sourceId,enabled=!busy) { sourceId=candidate.sourceId } }
        }
        Text("当前支持：${source?.displayName ?: "暂无"}。只读取网站实际提供的书架和已读章节；不包含其他浏览器的历史记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            WatercolorChoice("登录 / 验证",enabled=!busy && source!=null) {
                source?.let { login.launch(WebSessionActivity.intent(context,it.loginUrl,it.displayName)) }
            }
            WatercolorChoice("读取网站书架",enabled=!busy && source!=null) {
                source?.let { adapter ->
                    job=scope.launch {
                        busy=true;preview=null;progress="正在读取网站账号书架…"
                        try {
                            val result=withTimeout(120_000) {
                                withContext(Dispatchers.IO) { adapter.readLibrary { pages,books ->
                                    scope.launch { progress="已读取 $pages 页 · $books 本书" }
                                } }
                            }
                            existing=withContext(Dispatchers.IO) { LightReaderDb.get(context).bookDao().snapshot().map { it.key }.toSet() }
                            selected=result.entries.map { it.book.bookId }.toSet();importPositions=false
                            if(result.entries.isEmpty()) message="网站书架为空，现有书架未改动。" else preview=result
                        } catch(e:TimeoutCancellationException) { message="读取超时，现有书架未改动。请稍后重试。" }
                        catch(e:CancellationException) { throw e }
                        catch(e:Exception) {
                            message=when(e) {
                                is SourceError.AuthRequired -> "登录状态不可用。请先登录网站，再重新读取；现有书架未改动。"
                                is SourceError.ChallengeRequired -> "网站需要验证。请完成登录 / 验证后重试；现有书架未改动。"
                                is SourceError.RateLimited -> "网站暂时限制访问，请稍后重试；现有书架未改动。"
                                is SourceError.ParseChanged -> "未能识别完整的网站书架，未导入任何记录。请稍后重试。"
                                else -> "读取未完成，现有书架未改动。请检查网络后重试。"
                            }
                        } finally { busy=false;progress="" }
                    }
                }
            }
            if(busy && !importing) WatercolorChoice("取消读取") { job?.cancel() }
        }
        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress,style=MaterialTheme.typography.bodySmall) }
    }
    preview?.let { snapshot ->
        AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text("预览网站书架")},text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                val duplicate=snapshot.entries.count { "${it.book.sourceId}:${it.book.bookId}" in existing }
                Text("网站 ${snapshot.entries.size} 本 · 本地已有 $duplicate 本\n已选择 ${selected.size} 本")
                Text("重复书籍不会再次添加。导入不会清空本地书架，也不会覆盖已有阅读位置。",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    TextButton(enabled=!busy,onClick={selected=snapshot.entries.map{it.book.bookId}.toSet()}){Text("全选")}
                    TextButton(enabled=!busy,onClick={selected=emptySet()}){Text("取消全选")}
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp)) {
                    items(snapshot.entries,key={it.book.bookId}) { entry ->
                        val chosen=entry.book.bookId in selected
                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=chosen,enabled=!busy,role=Role.Checkbox) {
                            selected=if(it)selected+entry.book.bookId else selected-entry.book.bookId
                        },verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(chosen,null,enabled=!busy)
                            Column(Modifier.weight(1f).padding(vertical=6.dp)) {
                                Text(entry.book.title,style=MaterialTheme.typography.bodyMedium)
                                Text(if("${entry.book.sourceId}:${entry.book.bookId}" in existing)"已在本地" else "将添加到书架",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if(snapshot.entries.any { it.readChapterUrl!=null }) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=importPositions,enabled=!busy,role=Role.Checkbox,onValueChange={importPositions=it}),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(importPositions,null,enabled=!busy);Text("补齐没有本地记录的已读章节",style=MaterialTheme.typography.bodySmall)
                    }
                } else Text("网站没有返回可识别的已读章节，本次仅导入收藏。",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(enabled=!busy&&selected.isNotEmpty(),onClick={
            job=scope.launch {
                busy=true;importing=true;progress="正在合并书架…"
                try {
                    val result=importer.merge(snapshot,selected,importPositions)
                    preview=null;message="已导入 ${result.added} 本书，保留 ${result.existing} 本已有书籍，补齐 ${result.positions} 处阅读位置。"
                } catch(e:CancellationException) { throw e }
                catch(_:Exception) { message="导入未完成，数据库未提交变更，请重试。" }
                finally { busy=false;importing=false;progress="" }
            }
        }){Text(if(importing)"导入中…" else "导入所选书籍")}},dismissButton={TextButton(enabled=!busy,onClick={preview=null}){Text("取消")}})
    }
    message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("网站书架")},text={Text(text)},confirmButton={TextButton(onClick={message=null}){Text("知道了")}}) }
}

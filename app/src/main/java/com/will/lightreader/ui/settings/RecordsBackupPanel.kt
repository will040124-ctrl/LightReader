package com.will.lightreader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.will.lightreader.data.*
import com.will.lightreader.ui.components.WatercolorChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RecordsBackupPanel(onPreferencesRestored: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val manager=remember(context){RecordBackupManager(context)}
    var busy by remember { mutableStateOf(false) }
    var exportName by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ReadingRecords?>(null) }
    var preferences by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name=exportName;exportName=null
        if(name!=null) scope.launch {
            busy=true
            try {
                if(uri!=null) withContext(Dispatchers.IO) {
                    val file=File(context.cacheDir,name)
                    require(file.exists()) { "导出状态已失效，请重新导出" }
                    context.contentResolver.openOutputStream(uri,"wt")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                        ?: error("无法写入选择的位置")
                }
                if(uri!=null) message="记录备份已导出。书籍原文件、正文、插图、背景图片和漫画源需另行保留。"
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){message="导出失败：${e.message ?: "请更换保存位置"}"}
            finally{withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO){File(context.cacheDir,name).delete()};busy=false}
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try {
                preview=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val n=input.read(buffer);if(n<0)break;require(bytes.size()+n<=RecordBackupCodec.MAX_BYTES){"备份文件超过32MB"};bytes.write(buffer,0,n)}
                        RecordBackupCodec.decode(bytes.toByteArray())
                    } ?: error("无法读取选择的文件")
                }
                preferences=false
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){message="备份未导入，现有记录未改动。" + when(e) {
                is kotlinx.serialization.SerializationException -> "文件格式不正确或版本不兼容，请选择轻阅导出的记录备份。"
                is IllegalArgumentException -> e.message?.take(100).orEmpty()
                else -> "无法读取文件，请确认文件仍在所选位置后重试。"
            }}
            finally{busy=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("备份书架、章节目录、阅读进度、书签、摘抄及偏好。",style=MaterialTheme.typography.bodyMedium)
        Text("不包含书籍原文件、下载正文、插图、背景图、漫画源或登录信息。换设备或更换签名前，请另存原书；恢复本地书记录后须重新导入同一原文件。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("备份含阅读记录与摘抄，请妥善保存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            WatercolorChoice("导出记录备份",enabled=!busy&&exportName==null) {
                scope.launch {busy=true
                    try{
                        val name="records-${java.util.UUID.randomUUID()}.json"
                        withContext(Dispatchers.IO){val file=File(context.cacheDir,name);try{file.writeBytes(manager.export())}catch(e:Exception){file.delete();throw e}}
                        exportName=name
                        export.launch("LightReader-records-${java.time.LocalDate.now()}.json")
                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){message="无法创建备份：${e.message?.take(100)}";exportName?.let{File(context.cacheDir,it).delete()};exportName=null}
                    finally{busy=false}
                }
            }
            WatercolorChoice("选择备份恢复",enabled=!busy&&exportName==null){import.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    preview?.let { records ->
        AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text("确认合并记录")},text={
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${records.books.size} 本书 · ${records.positions.size} 处进度\n${records.bookmarks.size} 条书签 · ${records.excerpts.size} 条摘抄")
                Text("保留已有书籍和较新的进度，重复书签、摘抄不再添加。备份不含正文和书籍文件，本地书需保留原文件并重新导入。")
                Row(Modifier.heightIn(min=48.dp).toggleable(value=preferences,enabled=!busy,
                    role=androidx.compose.ui.semantics.Role.Checkbox,onValueChange={preferences=it}),
                    verticalAlignment=Alignment.CenterVertically){Checkbox(preferences,null,enabled=!busy);Text("同时恢复阅读和外观偏好")}
            }
        },confirmButton={TextButton(enabled=!busy,onClick={scope.launch{busy=true
            try{
                val result=manager.restore(records)
                // The records transaction has committed. Preference failures must not
                // be reported as a database rollback.
                val applied=if(preferences)try {
                    val saved=withContext(Dispatchers.IO){manager.restorePreferences(records.preferences)}
                    onPreferencesRestored()
                    saved
                }catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){false}else true
                message=result+if(!applied)"。记录已合并，偏好写入未完成，请重试。" else "。重新打开书籍后使用恢复的进度。"
                preview=null
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){message="记录合并失败，未提交数据库变更。${e.message?.take(100)}"}
            finally{busy=false}
        }}){Text("合并恢复")}},dismissButton={TextButton(enabled=!busy,onClick={preview=null}){Text("取消")}})
    }
    message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("备份与恢复")},text={Text(text)},confirmButton={TextButton(onClick={message=null}){Text("知道了")}}) }
}

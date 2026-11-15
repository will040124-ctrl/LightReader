package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.will.lightreader.data.*
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.account.*
import com.will.lightreader.ui.settings.AccountLibraryPanel
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.*

/** Isolated benchmark-only harness. Refuses a library containing any non-QA books. */
class AccountImportQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db=LightReaderDb.get(this)
        setContent { LightReaderTheme {
            val scope=rememberCoroutineScope()
            var ready by remember { mutableStateOf(false) }
            var mode by remember { mutableStateOf("success") }
            var report by remember { mutableStateOf("准备测试数据") }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val books=db.bookDao().snapshot()
                    if (books.any { it.key !in setOf("wenku8:999900001","wenku8:999900002","wenku8:999900003") }) {
                        report="拒绝运行：测试应用中有其他书籍";return@withContext
                    }
                    if(books.isEmpty()) db.withTransaction {
                        db.bookDao().upsert(BookEntity("wenku8:999900001","wenku8","999900001","https://www.wenku8.net/book/999900001.htm","Account Existing QA",addedAt=1,lastReadAt=888))
                        db.positionDao().upsert(PositionEntity("wenku8:999900001","https://www.wenku8.net/novel/0/999900001/11.htm",updatedAt=888))
                    }
                    ready=true;report="准备完成"
                }
            }
            val source=remember(mode) { object:AccountLibrarySource {
                override val sourceId="wenku8"
                override val displayName="隔离测试账号"
                override val loginUrl="https://www.wenku8.net/login.php"
                override suspend fun readLibrary(onProgress:(Int,Int)->Unit):AccountLibrarySnapshot {
                    onProgress(1,1);delay(250)
                    if(mode=="error")throw SourceError.AuthRequired(loginUrl)
                    val ids=if(mode=="fault")listOf("999900001","999900002","999900003")else listOf("999900001","999900002")
                    val entries=ids.map { id -> AccountLibraryEntry(BookBrief("wenku8",id,"https://www.wenku8.net/book/$id.htm",
                        if(id=="999900001")"Remote Existing QA" else "Account New QA $id"),"https://www.wenku8.net/novel/0/$id/22.htm","网站旧章节") }
                    onProgress(2,entries.size);return AccountLibrarySnapshot(sourceId,entries,2)
                }
            } }
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("账号导入隔离验证",style=MaterialTheme.typography.headlineSmall)
                if(ready) {
                    Row {
                        for((key,label) in listOf("success" to "模拟成功","error" to "模拟分页失败","fault" to "模拟事务故障")) {
                            TextButton(onClick={scope.launch {
                                withContext(Dispatchers.IO) {
                                    db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS qa_import_abort")
                                    if(key=="fault") db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_import_abort BEFORE INSERT ON books WHEN NEW.`key`='wenku8:999900003' BEGIN SELECT RAISE(ABORT,'qa rollback'); END")
                                }
                                mode=key
                            }}){Text(label)}
                        }
                    }
                    AccountLibraryPanel(listOf(source))
                    TextButton(onClick={scope.launch {
                        report=withContext(Dispatchers.IO) {
                            val books=db.bookDao().snapshot();val positions=db.positionDao().snapshot()
                            val old=positions.first{it.bookKey=="wenku8:999900001"}
                            val preserved=old.updatedAt==888L && old.chapterUrl.endsWith("/11.htm") && books.first{it.key==old.bookKey}.title=="Account Existing QA"
                            val new=positions.firstOrNull{it.bookKey=="wenku8:999900002"}
                            "${if(preserved)"验证通过" else "验证失败"}：${books.size}本 / 原进度与标题保留 / 新进度${if(new?.updatedAt==0L)"已补齐" else "未导入"}"
                        }
                    }}){Text("读取验证结果")}
                }
                Text(report)
            }
        } }
    }
}

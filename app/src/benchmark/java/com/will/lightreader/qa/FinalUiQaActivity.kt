package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.R
import com.will.lightreader.data.*
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.*
import com.will.lightreader.source.*
import com.will.lightreader.ui.author.AuthorScreen
import com.will.lightreader.ui.book.BookScreen
import com.will.lightreader.ui.discover.*
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.*

/** Offline synthetic pages. This Activity is never part of the production manifest. */
class FinalUiQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val page=intent.getStringExtra("page") ?: "discover"
        val mode=intent.getStringExtra("mode") ?: "normal"
        val presentation=intent.getBooleanExtra("presentation",false)
        val runId=intent.getStringExtra("runId") ?: "local"
        if (page == "backup") {
            var report by mutableStateOf("正在验证记录备份…")
            setContent { LightReaderTheme { Text(report) } }
            lifecycleScope.launch {
                try { withContext(Dispatchers.IO) { verifyBackup() };report="备份偏好验证通过";android.util.Log.i("FinalUiQA","$runId BACKUP_PASS") }
                catch(e:Exception) { report="备份验证失败";android.util.Log.e("FinalUiQA","$runId BACKUP_FAIL",e) }
            }
            return
        }
        val source=object:NovelSource {
            override val id="comic-js-final-ui-qa"
            override val displayName=if(presentation) "演示书单" else "离线界面验证"
            override val discovery=SourceDiscovery(ranks=listOf("day" to "今日热榜","month" to "本月热点"),supportsHome=!presentation)
            var authorCalls=0
            private suspend fun content():List<BookBrief> {
                delay(120)
                if(mode=="error")throw SourceError.Network(java.io.IOException("离线测试：请求失败"))
                if(mode=="empty")return emptyList()
                if(presentation) {
                    val library=(application as com.will.lightreader.LightReaderApp).container.library
                    val store=getSharedPreferences("reader_formats_qa",MODE_PRIVATE)
                    return withContext(Dispatchers.IO) {
                        listOf("epub3","txt","html","md","epub2").mapNotNull { format ->
                            store.getString(format,null)?.let { library.book(it) }?.let { book ->
                                BookBrief(id,book.bookId,"https://ui.qa/book/${book.bookId}",book.title,
                                    "轻阅 · 原创演示",book.coverUrl,listOf("日常","治愈"),"已完结")
                            }
                        }
                    }
                }
                return (1..40).map { i->BookBrief(id,"$i","https://ui.qa/book/$i",
                    "第 $i 本 · 雨停以后我们一起走过通往月光图书馆的漫长小路",
                    "这是一位名字比较长的原创测试作者",
                    "android.resource://$packageName/${R.drawable.watercolor_reading_room}",listOf("校园","日常"),"已完结") }
            }
            override suspend fun authorWorks(authorName:String,authorUrl:String?):List<BookBrief> {
                authorCalls++;android.util.Log.i("FinalUiQA","$runId AUTHOR_CALL=$authorCalls")
                if(mode=="refresh-error"&&authorCalls>1)throw SourceError.Network(java.io.IOException("刷新失败"))
                if(mode=="timeout")delay(9_000)
                return content()
            }
            override suspend fun popular(kind:String)=content()
            override suspend fun search(query:String)=if(presentation) content().filter { query in it.title || query in it.author.orEmpty() }
                else content().map { it.copy(title="$query · ${it.title}") }
            override suspend fun popularPage(kind:String,page:Int)=BrowsePage(content(),false)
            override suspend fun tags():List<TagGroup> {
                if(mode=="error")throw SourceError.Network(java.io.IOException("离线标签错误"))
                return if(presentation) listOf(TagGroup("日常系",listOf("校园","青春","恋爱","治愈","群像","旅行","音乐","日常")),
                    TagGroup("幻想系",listOf("奇幻","魔法","异世界","冒险","科幻","悬疑")))
                else listOf(TagGroup("题材",(1..30).map { "校园标签$it" }),TagGroup("风格",listOf("日常","奇幻","这是一个需要换行显示的长标签名字")))
            }
            override suspend fun tagBooks(tag:String,page:Int,completedOnly:Boolean,popular:Boolean)=BrowsePage(content(),false)
            override suspend fun completedBooks(page:Int)=BrowsePage(content(),false)
            override suspend fun discoveryHome()=BrowsePage(emptyList(),false,listOf(BrowseSection("连载更新 → 右滑看更多 →",content().take(8),"featured")))
            override suspend fun bookDetail(bookUrl:String):BookDetail {
                val book=content().firstOrNull() ?: return BookDetail(id,"1",bookUrl,"空白作品")
                return BookDetail(id,"1",bookUrl,book.title,book.author,book.coverUrl,
                    intro="这是用于验证长简介、目录、作者与标签布局的原创离线资料。".repeat(20),tags=book.tags,status=book.status)
            }
            override suspend fun chapterList(bookUrl:String)=(1..200).map {
                ChapterRef("https://ui.qa/chapter/$it","第${it}章 · 将尚未说出口的话写进雨声里，然后在下一页与你重逢","第${(it-1)/50+1}卷 · 夜晚的来信")
            }
            override suspend fun chapterContent(chapterUrl:String)= run { delay(intent.getLongExtra("chapterDelayMs",150).coerceIn(0,2000)); ChapterContent(chapterUrl,"原创测试章节",List(20){Block.Paragraph("雨水沿着窗檐落下，书页上的故事仍在继续。")}) }
        }
        val container=AppContainer(this)
        container.sources.replaceScripts(listOf(source))
        val secondary=object:NovelSource by source {
            override val id="comic-js-final-ui-qa-2"
            override val displayName="另一份名称比较长的内容来源"
        }
        val vm=DiscoverViewModel(SourceRegistry(if(presentation)listOf(source) else listOf(source,secondary)))
        if(presentation) intent.getStringExtra("query")?.takeIf { it.isNotBlank() }?.let { vm.search(it) }
        setContent {
            LightReaderTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                when(page) {
                    "author"->AuthorScreen(container,source.id,"这是一位需要完整换行的原创长名字作者",null,
                        onBack={finish()},onOpenBook={selected=it.title})
                    "book"->BookScreen(container,source.id,intent.getStringExtra("bookUrl") ?: "https://ui.qa/book/1",onBack={finish()},
                        onOpenChapter={_,chapter->selected=chapter},onOpenAuthor={name,_->selected=name},onOpenTag={selected=it})
                    else->Surface(Modifier.fillMaxSize()) {
                        com.will.lightreader.ui.SystemBarsAppearance(com.will.lightreader.ui.theme.LocalAppDark.current)
                        if(presentation) Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).statusBarsPadding().consumeWindowInsets(WindowInsets.navigationBars)) {
                                DiscoverScreen(vm,onOpenBook={selected=it.title})
                            }
                            com.will.lightreader.ui.components.WatercolorRule()
                            com.will.lightreader.ui.BunkoNavigation(com.will.lightreader.ui.HomeTab.Discover,false,{})
                        } else Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            DiscoverScreen(vm,onOpenBook={selected=it.title})
                        }
                    }
                }
                selected?.let { value->AlertDialog(onDismissRequest={selected=null},title={Text("已选择作品")},
                    text={Text(value)},confirmButton={TextButton(onClick={selected=null}){Text("返回")}}) }
            }
        }
    }
    private suspend fun verifyBackup() {
        val store=getSharedPreferences("reader_prefs",MODE_PRIVATE)
        val appearance=getSharedPreferences("app_appearance",MODE_PRIVATE)
        val keys=listOf("comic_immersive","reader_hide_status_bar","comic_mode_default","shelf_starts_closed",
            "comic-mode:qa:rc1","comic-page:qa-chapter","comic-scroll-page:qa-chapter","comic-scroll-fraction:qa-chapter")
        val old=store.all;val oldAppearance=appearance.getString("mode",null)
        try {
            check(store.edit().putBoolean("comic_immersive",false).putBoolean("reader_hide_status_bar",true)
                .putString("comic_mode_default","scroll").putBoolean("shelf_starts_closed",false)
                .putString("comic-mode:qa:rc1","scroll").putString("comic-page:qa-chapter","page-2")
                .putString("comic-scroll-page:qa-chapter","page-2").putFloat("comic-scroll-fraction:qa-chapter",.625f).commit())
            val manager=RecordBackupManager(this)
            val records=RecordBackupCodec.decode(manager.export())
            check(records.preferences.immersive==false && records.preferences.hideStatusBar==true)
            check(records.preferences.comicAnchors["qa-chapter"]?.fraction==.625f)
            val clear=store.edit();keys.forEach(clear::remove);check(clear.commit())
            check(manager.restorePreferences(records.preferences))
            check(store.getString("comic_mode_default",null)=="scroll")
            check(store.getString("comic-mode:qa:rc1",null)=="scroll")
            check(store.getFloat("comic-scroll-fraction:qa-chapter",0f)==.625f)
        } finally {
            val edit=store.edit()
            for(key in keys) when(val value=old[key]) {
                null->edit.remove(key)
                is Boolean->edit.putBoolean(key,value)
                is String->edit.putString(key,value)
                is Float->edit.putFloat(key,value)
            }
            check(edit.commit())
            val editAppearance=appearance.edit()
            if(oldAppearance==null)editAppearance.remove("mode") else editAppearance.putString("mode",oldAppearance)
            check(editAppearance.commit())
        }
    }
}

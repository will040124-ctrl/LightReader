package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.*
import com.will.lightreader.source.ComicSource
import com.will.lightreader.ui.reader.ReaderScreen
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.launch

/** Only in the disposable benchmark package. Uses synthetic images from adb reverse. */
class ComicReaderQaActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source=object: ComicSource {
            override val id="comic-qa"
            override val displayName="Comic QA"
            override suspend fun search(query:String)=listOf(BookBrief(id,"1","https://comic.qa/book","Comic QA"))
            override suspend fun detail(bookUrl:String)=BookDetail(id,"1",bookUrl,"Comic QA")
            override suspend fun episodes(bookUrl:String)=listOf(ChapterRef("https://comic.qa/1","第一话"),ChapterRef("https://comic.qa/2","第二话"))
            override suspend fun pages(episodeUrl:String)=(1..3).map { ComicPage("${episodeUrl.last()}-$it","http://127.0.0.1:18767/${episodeUrl.last()}-$it.png") }
            override fun imageHeaders(episodeUrl:String,page:ComicPage)=mapOf("X-LightReader-Fixture" to "comic-test")
        }
        val container=AppContainer(this,listOf(source))
        lifecycleScope.launch {
            container.library.saveBook(source.search("").single())
            container.library.saveChapters("comic-qa:1",source.episodes(""))
            if(intent.getBooleanExtra("resetComicTest",false)) {
                val prefs=com.will.lightreader.ui.reader.ReaderPrefs(this@ComicReaderQaActivity)
                prefs.saveComicMode("comic-qa:1","scroll")
                prefs.saveComicPageId("https://comic.qa/1","1-1")
                prefs.saveComicScroll("https://comic.qa/1","1-1",0f)
                container.library.savePosition("comic-qa:1","https://comic.qa/1","第一话",0,0f)
                coil.Coil.imageLoader(this@ComicReaderQaActivity).memoryCache?.clear()
                coil.Coil.imageLoader(this@ComicReaderQaActivity).diskCache?.clear()
            }
            val target=container.library.position("comic-qa:1")?.chapterUrl ?: "https://comic.qa/1"
            setContent { LightReaderTheme { ReaderScreen(container,source.id,"comic-qa:1",target,onBack={finish()}) } }
            kotlinx.coroutines.delay(2000)
            val downloads=container.library.downloadedChapters("comic-qa:1").size
            android.util.Log.i("ComicQA", "COMIC_OFFLINE=$downloads")
        }
    }
}

package com.will.lightreader.data

import com.will.lightreader.domain.ChapterRef
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Process-wide tasks owned by the repository, so leaving/reopening detail does not lose control. */
class BookDownloadTasks {
    private val states=ConcurrentHashMap<String,MutableStateFlow<BookDownloader.Progress?>>()
    private val jobs=ConcurrentHashMap<String,Job>()
    private val failures=ConcurrentHashMap<String,List<ChapterRef>>()
    fun state(key:String)=states.getOrPut(key) { MutableStateFlow(null) }
    fun failed(key:String)=failures[key].orEmpty()
    fun page(key:String,done:Int,total:Int) { val flow=state(key);flow.value=flow.value?.copy(pageDone=done,pageTotal=total) }
    @Synchronized fun forget(key:String) { require(jobs[key]?.isCompleted!=false);state(key).value=null;failures.remove(key);jobs.remove(key) }
    @Synchronized fun start(key:String,scope:CoroutineScope,chapters:List<ChapterRef>,downloader:BookDownloader):Boolean {
        if(jobs[key]?.isCompleted==false)return false
        val flow=state(key);flow.value=BookDownloader.Progress(0,chapters.size)
        val job=scope.launch(start=CoroutineStart.LAZY) {
            try {
                val result=downloader.download(chapters) { flow.value=it }
                failures[key]=chapters.filter { it.chapterUrl in result.failedUrls }
            } catch(e:CancellationException) {
                failures[key]=chapters.filter { it.chapterUrl in flow.value?.failedUrls.orEmpty() }
                flow.value=flow.value?.copy(finished=true,cancelled=true);throw e
            } catch(e:Exception) {
                failures[key]=chapters
                flow.value=(flow.value ?: BookDownloader.Progress(0,chapters.size)).copy(finished=true,failed=chapters.size,
                    failedUrls=chapters.map { it.chapterUrl },lastError=e.message?.take(180))
            }
        }
        jobs[key]=job
        job.invokeOnCompletion { cause ->
            synchronized(this@BookDownloadTasks) {
                if(cause is CancellationException && jobs[key]===job && flow.value?.finished==false)
                    flow.value=flow.value?.copy(finished=true,cancelled=true)
            }
        }
        job.start();return true
    }
    fun cancel(key:String) { jobs[key]?.cancel() }
    suspend fun cancelAndJoin(key:String) { jobs[key]?.cancelAndJoin() }
}

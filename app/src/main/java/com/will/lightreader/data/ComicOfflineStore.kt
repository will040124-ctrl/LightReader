package com.will.lightreader.data

import com.will.lightreader.domain.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

/** Durable episode store. Only the atomic manifest is a completion marker; headers are never stored. */
class ComicOfflineStore(private val root:File,private val maxBytes:Long=2L*1024*1024*1024,
    private val parallelism:Int=3, private val validateImage:(ByteArray)->Boolean) {
    @Serializable data class ImageEntry(val identity:String,val blob:String,val size:Long,val transformHash:String?=null)
    @Serializable data class Manifest(val version:Int=1,val content:ChapterContent,val images:List<ImageEntry>)
    private val json=Json { ignoreUnknownKeys=true }
    private val mutex=Mutex()
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun episode(url:String)=File(root,digest(url.toByteArray()))
    private fun identity(page:ComicPage)=digest((page.id.length.toString()+":"+page.id+page.url).toByteArray())
    private fun file(dir:File,entry:ImageEntry):File? = if(entry.blob.matches(Regex("[a-f0-9]{64}")) && entry.size in 1..24L*1024*1024)File(dir,entry.blob+".image") else null
    private fun verified(dir:File,entry:ImageEntry):File? = file(dir,entry)?.takeIf {
        it.isFile && it.length()==entry.size && digest(it.readBytes())==entry.blob
    }
    private fun atomic(file:File,text:String) {
        val temp=File(file.parentFile,file.name+".tmp")
        try { temp.writeText(text)
            try { Files.move(temp.toPath(),file.toPath(),REPLACE_EXISTING,ATOMIC_MOVE) }
            catch(_:java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(),file.toPath(),REPLACE_EXISTING) }
        } finally { temp.delete() }
    }
    fun read(url:String,verifyHashes:Boolean=true):ChapterContent? = runCatching {
        val dir=episode(url);val marker=File(dir,"manifest.json")
        if(!marker.isFile||marker.length()>4L*1024*1024)return null
        val manifest=json.decodeFromString(Manifest.serializer(),marker.readText())
        require(manifest.version==1 && manifest.content.chapterUrl==url)
        val pages=validateComicPages(manifest.content.comicPages)
        require(manifest.images.size==pages.size)
        manifest.content.copy(comicPages=pages.mapIndexed { index,page ->
            val entry=manifest.images[index];require(entry.identity==identity(page))
            val local=if(verifyHashes)verified(dir,entry) else file(dir,entry)?.takeIf { it.isFile && it.length()==entry.size }
            page.copy(localRef=requireNotNull(local).toURI().toString())
        })
    }.getOrNull()
    suspend fun download(content:ChapterContent,onPage:(Int,Int)->Unit={_,_->},fetch:suspend (ComicPage)->ByteArray):ChapterContent = mutex.withLock {
        validateComicPages(content.comicPages)
        val dir=episode(content.chapterUrl).apply { mkdirs() };val draft=File(dir,"progress.json")
        val previous=runCatching {
            require(draft.length()<=4L*1024*1024)
            json.decodeFromString(Manifest.serializer(),draft.readText()).images.associateBy { it.identity }
        }.getOrDefault(emptyMap())
        var total=root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val entries=arrayOfNulls<ImageEntry>(content.comicPages.size)
        val commit=Mutex();val slots=Semaphore(parallelism.coerceIn(1,5));var completed=0
        onPage(0,content.comicPages.size)
        coroutineScope { val batch=this;content.comicPages.mapIndexed { index,page -> async { try { slots.withPermit {
            currentCoroutineContext().ensureActive()
            val key=identity(page);val old=previous[key]
            val transformHash=page.imageTransform?.let { digest(it.toByteArray()) }
            val entry=if(old!=null && old.transformHash==transformHash && verified(dir,old)!=null)old else {
                val bytes=fetch(page)
                require(bytes.isNotEmpty() && bytes.size<=24*1024*1024 && validateImage(bytes)) { "下载的漫画图片无效或损坏" }
                commit.withLock {
                val hash=digest(bytes);val target=File(dir,"$hash.image")
                if(!target.isFile || target.length()!=bytes.size.toLong() || digest(target.readBytes())!=hash) {
                    require(total+bytes.size<=maxBytes && dir.usableSpace>bytes.size+32L*1024*1024) { "漫画离线空间不足，请先清理下载（上限 2 GB）" }
                    val temp=File(dir,"$hash.tmp")
                    try { temp.writeBytes(bytes);currentCoroutineContext().ensureActive();Files.move(temp.toPath(),target.toPath(),REPLACE_EXISTING) }
                    finally { temp.delete() }
                    total+=bytes.size
                }
                ImageEntry(key,hash,bytes.size.toLong(),transformHash)
                }
            }
            commit.withLock {
                entries[index]=entry;completed++
                atomic(draft,json.encodeToString(Manifest.serializer(),Manifest(content=content,images=(previous.values+entries.filterNotNull()).associateBy { it.identity }.values.toList())))
                onPage(completed,content.comicPages.size)
            }
        } } catch(e:CancellationException) { batch.cancel(e);throw e } } }.awaitAll() }
        currentCoroutineContext().ensureActive()
        atomic(File(dir,"manifest.json"),json.encodeToString(Manifest.serializer(),Manifest(content=content,images=entries.map { requireNotNull(it) })))
        requireNotNull(read(content.chapterUrl)) { "漫画离线文件校验失败" }
    }
    fun size(url:String):Long=episode(url).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun delete(url:String) {
        val target=episode(url).canonicalFile;val base=root.canonicalFile
        require(target!=base && target.toPath().startsWith(base.toPath()))
        require(!target.exists() || target.deleteRecursively()) { "漫画离线文件删除失败" }
    }
}

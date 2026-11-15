package com.will.lightreader.source.script

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fixed lock stripes bound memory while coalescing simultaneous requests for the same image. */
internal class ComicImageCache(private val root:File, private val maxBytes:Long=128L*1024*1024,private val validate:(ByteArray)->Boolean={true}) {
    private val locks=Array(32) { Mutex() }
    private val trimLock=Mutex()
    private val sizes=LinkedHashMap<String,Long>(16,.75f,true)
    private var totalBytes=0L
    private var indexed=false
    private suspend fun track(file:File)=trimLock.withLock {
        if(!indexed) {
            root.listFiles().orEmpty().filter { it.extension=="image" }.sortedBy { it.lastModified() }.forEach {
                sizes[it.name]=it.length();totalBytes+=it.length()
            }
            indexed=true
        }
        val size=if(file.isFile)file.length() else 0L
        totalBytes-=sizes.remove(file.name) ?: 0L
        if(size>0) { sizes[file.name]=size;totalBytes+=size }
        val iterator=sizes.entries.iterator()
        while(totalBytes>maxBytes && iterator.hasNext()) {
            val entry=iterator.next();if(entry.key==file.name)continue
            val old=File(root,entry.key)
            if(!old.exists() || old.delete()) { totalBytes-=entry.value;iterator.remove() }
        }
    }
    data class Hit(val bytes:ByteArray,val disk:Boolean)
    suspend fun get(key:String,read:Boolean=true,write:Boolean=true,network:Boolean=true,fetch:suspend ()->ByteArray):Hit =
        locks[(key.hashCode() and Int.MAX_VALUE)%locks.size].withLock {
            require(key.matches(Regex("[a-f0-9]{64}")))
            root.mkdirs();val file=File(root,"$key.image")
            if(read && file.isFile && file.length() in 1..MaxComicImageBytes.toLong()) {
                val cached=runCatching { file.readBytes() }.getOrNull()
                if(cached!=null && validate(cached)) { file.setLastModified(System.currentTimeMillis());track(file);return@withLock Hit(cached,true) }
                file.delete();track(file)
            }
            require(network) { "图片未缓存" }
            val bytes=fetch();require(bytes.size in 1..MaxComicImageBytes && validate(bytes)) { "漫画图片缓存数据无效" }
            if(write && bytes.size<=maxBytes) {
                val temp=File(root,"$key.tmp")
                try { temp.writeBytes(bytes);Files.move(temp.toPath(),file.toPath(),REPLACE_EXISTING) } finally { temp.delete() }
                track(file)
            }
            Hit(bytes,false)
        }
}

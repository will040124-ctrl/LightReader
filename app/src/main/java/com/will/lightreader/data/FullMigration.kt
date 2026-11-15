package com.will.lightreader.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Device-local full transfer. The old application remains intact throughout. */
internal class FullMigration(private val context:Context) {
    private val db:LightReaderDb get()=LightReaderDb.get(context)
    private val json=Json { ignoreUnknownKeys=false }
    private val maxTotal=4L*1024*1024*1024
    private val preferences:List<String> =listOf("reader_prefs","app_appearance")
    private val roots:Set<String> =setOf("imports","chapters","comic-offline","comic-sources","comic-source-data","bg.jpg","cookies.json")
    private suspend fun rows():MigrationRows=db.withTransaction {
        MigrationRows(db.bookDao().snapshot(),db.chapterDao().snapshot(),db.positionDao().snapshot(),
            db.bookmarkDao().snapshot(),db.excerptDao().snapshot(),db.bookDao().detailSnapshot())
    }
    private fun prefs():Map<String,Map<String,MigrationPreference>> =preferences.associateWith { name ->
        context.getSharedPreferences(name,Context.MODE_PRIVATE).all.mapValues { (_,v) -> when(v) {
            is String->MigrationPreference("string",JsonPrimitive(v))
            is Boolean->MigrationPreference("boolean",JsonPrimitive(v))
            is Int->MigrationPreference("int",JsonPrimitive(v))
            is Long->MigrationPreference("long",JsonPrimitive(v))
            is Float->MigrationPreference("float",JsonPrimitive(v))
            is Set<*>->MigrationPreference("set",JsonArray(v.map { JsonPrimitive(it as String) }))
            else->error("无法迁移此设置类型")
        } }
    }
    private fun ownedFiles():List<Pair<String,File>> = (roots.map { "files/$it" to File(context.filesDir,it) }+
        listOf("image_cache","comic-images").map { "cache/$it" to File(context.cacheDir,it) }).flatMap { (name,file) ->
        val base=if(name.startsWith("files/"))context.filesDir else context.cacheDir
        (if(file.isDirectory)file.walkTopDown().filter { it.isFile }.toList() else listOf(file).filter { it.isFile })
            .filter { !it.name.endsWith(".tmp") }.map { child ->
                require(child.canonicalFile.toPath().startsWith(base.canonicalFile.toPath()))
                name.substringBefore('/')+"/"+child.relativeTo(base).invariantSeparatorsPath to child
            }
    }.sortedBy { it.first }
    private fun sha(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input-> val buffer=ByteArray(65536);while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun export(onProgress:(String)->Unit):File = withContext(Dispatchers.IO) {
        val before=rows();val settings=prefs();val owned=ownedFiles()
        require(owned.size<=200_000) { "迁移文件数量超过限制" }
        val bytes=owned.sumOf { it.second.length() }
        require(bytes<=maxTotal && context.cacheDir.usableSpace>bytes+64L*1024*1024) { "生成迁移包所需空间不足" }
        val folder=File(context.cacheDir,"migration-transfer").apply { mkdirs() }
        val archive=File(folder,"library.zip");val temp=File(folder,"library.tmp")
        try {
            val entries=mutableListOf<MigrationFile>()
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                zip.setLevel(1)
                owned.forEachIndexed { index,(name,file) ->
                    currentCoroutineContext().ensureActive();onProgress("整理文件 ${index+1}/${owned.size}")
                    val hash=sha(file);val size=file.length()
                    zip.putNextEntry(ZipEntry(name));file.inputStream().use { it.copyTo(zip,65536) };zip.closeEntry()
                    require(file.length()==size && sha(file)==hash) { "数据仍在变化，请暂停下载后重试迁移" }
                    entries+=MigrationFile(name,size,hash)
                }
                require(rows()==before && prefs()==settings && ownedFiles().map { it.first }==owned.map { it.first }) {
                    "阅读记录仍在变化，请返回书架并暂停下载后重试迁移"
                }
                val manifest=MigrationManifest(filesPath=context.filesDir.absolutePath,rows=before,files=entries,preferences=settings)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(MigrationManifest.serializer(),manifest).toByteArray());zip.closeEntry()
            }
            java.nio.file.Files.move(temp.toPath(),archive.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            archive
        } finally { temp.delete() }
    }
    private fun safePath(path:String):Boolean = (path.startsWith("files/") || path.startsWith("cache/")) && path.split('/').all { it.isNotEmpty() && it!="." && it!=".." } &&
        '\\' !in path && ':' !in path && path.length<=1024 && (if(path.startsWith("files/"))path.split('/').getOrNull(1) in roots else path.split('/').getOrNull(1) in setOf("image_cache","comic-images"))

    suspend fun import(uri:Uri,onProgress:(String)->Unit):String = withContext(Dispatchers.IO) {
        val pending=File(context.filesDir,"migration-pending")
        require(!pending.exists()) { "上次迁移被系统中断。请仅清除新正式版的数据后重试；不要卸载或清除旧版。" }
        require(rows().let { it.books.isEmpty() && it.chapters.isEmpty() && it.positions.isEmpty() && it.bookmarks.isEmpty() && it.excerpts.isEmpty() }) {
            "正式版已有数据，已停止迁移以避免覆盖。旧版数据仍保留。"
        }
        val staging=File(context.cacheDir,"migration-incoming").canonicalFile
        require(staging.parentFile==context.cacheDir.canonicalFile)
        if(staging.exists())require(staging.deleteRecursively())
        require(staging.mkdirs())
        val archive=File(staging,"transfer.zip");val extracted=File(staging,"extracted").apply { mkdirs() }
        val moved=mutableListOf<File>();val originalPrefs=prefs();var committed=false
        try {
            onProgress("接收设备内迁移包")
            context.contentResolver.openInputStream(uri)!!.use { input->archive.outputStream().use { output ->
                val buffer=ByteArray(65536);var total=0L
                while(true) { currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                    total+=n;require(total<=maxTotal+64L*1024*1024 && context.cacheDir.usableSpace>n+64L*1024*1024) { "迁移包过大或存储空间不足" };output.write(buffer,0,n)
                }
            } }
            var restored:MigrationManifest?=null
            ZipFile(archive).use { zip ->
                val all=zip.entries().toList();require(all.size<=200_001 && all.map { it.name }.distinct().size==all.size)
                val meta=requireNotNull(zip.getEntry("manifest.json"));require(meta.size in 1..32L*1024*1024)
                val manifest=json.decodeFromString(MigrationManifest.serializer(),zip.getInputStream(meta).bufferedReader().use { it.readText() })
                require(manifest.version==1 && manifest.filesPath.matches(Regex("/data/(user/[0-9]+|data)/com[.]will[.]lightreader(?:[.]benchmark)?/files")))
                require(manifest.files.size<=200_000 && manifest.files.map { it.path }.distinct().size==manifest.files.size)
                require(manifest.files.all { safePath(it.path) && it.bytes in 0..maxTotal && it.sha256.matches(Regex("[a-f0-9]{64}")) })
                require(all.map { it.name }.toSet()==(manifest.files.map { it.path }+"manifest.json").toSet())
                require(manifest.files.sumOf { it.bytes }<=maxTotal && context.filesDir.usableSpace>manifest.files.sumOf { it.bytes }+64L*1024*1024)
                manifest.files.forEachIndexed { index,entry ->
                    currentCoroutineContext().ensureActive();onProgress("校验文件 ${index+1}/${manifest.files.size}")
                    val source=requireNotNull(zip.getEntry(entry.path));require(source.size==entry.bytes)
                    val file=File(extracted,entry.path);file.parentFile!!.mkdirs()
                    zip.getInputStream(source).use { input -> file.outputStream().use { output ->
                        val buffer=ByteArray(65536);var nTotal=0L
                        while(true) { val n=input.read(buffer);if(n<0)break;nTotal+=n;require(nTotal<=entry.bytes);output.write(buffer,0,n) }
                    } }
                    require(file.length()==entry.bytes && sha(file)==entry.sha256) { "迁移包文件校验失败" }
                    // Imported books and offline manifests contain absolute references owned by the old package.
                    if(file.extension=="json") {
                        require(file.length()<=32L*1024*1024)
                        file.writeText(file.readText().replace(manifest.filesPath,context.filesDir.absolutePath))
                    }
                }
                val rewritten=json.encodeToString(MigrationManifest.serializer(),manifest).replace(manifest.filesPath,context.filesDir.absolutePath)
                restored=json.decodeFromString(MigrationManifest.serializer(),rewritten)
            }
            val manifest=requireNotNull(restored)
            require(manifest.preferences.keys.all { it in preferences })
            // Refuse overwrites, including a previous interrupted migration; the original package is untouched.
            val files=listOf("files" to context.filesDir,"cache" to context.cacheDir).flatMap { (name,base) ->
                File(extracted,name).listFiles().orEmpty().map { it to File(base,it.name) }
            }
            files.forEach { (_,target) -> require(!target.exists() || target.isDirectory && target.listFiles().isNullOrEmpty()) { "目标已有同名文件，已停止迁移" } }
            withContext(NonCancellable) {
                pending.writeText("device-local migration in progress")
                for((file,target) in files) {
                    if(target.isDirectory)require(target.delete())
                    java.nio.file.Files.move(file.toPath(),target.toPath());moved+=target
                }
                applyPrefs(manifest.preferences)
                db.withTransaction {
                    require(db.bookDao().snapshot().isEmpty()) { "目标书架已变化，已停止迁移" }
                    val r=manifest.rows
                    r.books.forEach { db.bookDao().upsert(it) };db.chapterDao().upsertAll(r.chapters)
                    r.positions.forEach { db.positionDao().upsert(it) };r.bookmarks.forEach { db.bookmarkDao().add(it) }
                    r.excerpts.forEach { db.excerptDao().add(it) };r.details.forEach { db.bookDao().putCachedDetail(it) }
                    require(db.bookDao().snapshot().size==r.books.size && db.chapterDao().snapshot().size==r.chapters.size)
                }
                committed=true
            }
            "已迁移 ${manifest.rows.books.size} 本书、${manifest.rows.positions.size} 处进度、${manifest.files.size} 个文件。旧版仍完整保留。\n请关闭后重新打开正式版。桌面小组件需重新添加，网站登录可能需要重新验证。"
        } finally {
            if(!committed) { moved.forEach { it.deleteRecursively() };applyPrefs(originalPrefs) }
            staging.deleteRecursively();pending.delete()
        }
    }
    private fun applyPrefs(values:Map<String,Map<String,MigrationPreference>>) {
        values.forEach { (name,settings) ->
            require(name in preferences)
            val edit=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            settings.forEach { (key,p) -> when(p.type) {
                "string"->edit.putString(key,p.value.jsonPrimitive.content)
                "boolean"->edit.putBoolean(key,p.value.jsonPrimitive.boolean)
                "int"->edit.putInt(key,p.value.jsonPrimitive.int)
                "long"->edit.putLong(key,p.value.jsonPrimitive.long)
                "float"->edit.putFloat(key,p.value.jsonPrimitive.float)
                "set"->edit.putStringSet(key,p.value.jsonArray.map { it.jsonPrimitive.content }.toSet())
                else->error("未知设置类型")
            } }
            require(edit.commit()) { "设置保存失败" }
        }
    }
}

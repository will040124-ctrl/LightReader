package com.will.lightreader.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordBackupManager(private val context: Context) {
    private val db get() = LightReaderDb.get(context)
    private val reader get() = context.getSharedPreferences("reader_prefs",Context.MODE_PRIVATE)
    private val appearance get() = context.getSharedPreferences("app_appearance",Context.MODE_PRIVATE)
    suspend fun export(): ByteArray = withContext(Dispatchers.IO) {
        val prefs = com.will.lightreader.ui.reader.ReaderPrefs(context)
        val p = BackupPreferences(prefs.fontSize,prefs.lineHeight,prefs.serif,prefs.paper,prefs.twoPage,prefs.turnStyle,
            prefs.shelfLayoutMode.name,appearance.getString("mode","SYSTEM") ?: "SYSTEM",prefs.comicRtl,
            reader.all.filterKeys { it.startsWith("inline:") }.mapNotNull { (k,v) -> (v as? String)?.let { k to it } }.toMap(),
            shelfArtwork=prefs.shelfArtwork.name, shelfWheel=prefs.shelfWheelEnabled, widgetDetails=prefs.widgetShowDetails,
            immersive=prefs.immersive, hideStatusBar=prefs.hideStatusBar,
            defaultComicMode=prefs.defaultComicMode, shelfStartsClosed=prefs.shelfStartsClosed,
            comicModes=reader.all.filterKeys { it.startsWith("comic-mode:") }.mapNotNull { (key,value) ->
                (value as? String)?.takeIf { it in setOf("paged","scroll") }?.let { key.removePrefix("comic-mode:") to it }
            }.toMap(),
            comicAnchors=reader.all.keys.filter { it.startsWith("comic-page:") || it.startsWith("comic-scroll-page:") }
                .map { it.substringAfter(':') }.distinct().associateWith { chapter ->
                    BackupComicAnchor(reader.getString("comic-page:$chapter",null),reader.getString("comic-scroll-page:$chapter",null),
                        reader.getFloat("comic-scroll-fraction:$chapter",0f).takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f)
                })
        val records = db.withTransaction {
            val books=db.bookDao().snapshot(); val keys=books.map{it.key}.toSet()
            ReadingRecords(books,db.chapterDao().snapshot().filter{it.bookKey in keys},
                db.positionDao().snapshot().filter{it.bookKey in keys},db.bookmarkDao().snapshot().filter{it.bookKey in keys},
                db.excerptDao().snapshot().filter{it.bookKey in keys},p)
        }
        RecordBackupCodec.encode(records)
    }
    /** Validate again before writing; the entire records merge commits in one Room transaction. */
    suspend fun restore(records: ReadingRecords): String = withContext(Dispatchers.IO) {
        val safe=RecordBackupCodec.decode(RecordBackupCodec.encode(records))
        var added=0; var positions=0;var marks=0;var excerpts=0
        db.withTransaction {
            val books=db.bookDao().snapshot().associateBy{it.key}
            for (b in safe.books) if(b.key !in books) { db.bookDao().upsert(b);added++ }
            db.chapterDao().insertNew(safe.chapters)
            val old=db.positionDao().snapshot().associateBy{it.bookKey}
            for(p in safe.positions) if(old[p.bookKey]?.updatedAt?.let{it>=p.updatedAt} != true) { db.positionDao().upsert(p);positions++ }
            val knownMarks=db.bookmarkDao().snapshot().map(::bookmarkIdentity).toMutableSet()
            for(b in safe.bookmarks) if(knownMarks.add(bookmarkIdentity(b))) { db.bookmarkDao().add(b.copy(id=0));marks++ }
            val knownExcerpts=db.excerptDao().snapshot().map(::excerptIdentity).toMutableSet()
            for(e in safe.excerpts) if(knownExcerpts.add(excerptIdentity(e))) { db.excerptDao().add(e.copy(id=0));excerpts++ }
        }
        "已合并：新增 $added 本书、更新 $positions 处进度、新增 $marks 条书签和 $excerpts 条摘抄"
    }
    /** Preferences are an explicit, separate choice after the records transaction succeeds. */
    fun restorePreferences(p: BackupPreferences): Boolean {
        val edit=reader.edit().putInt("fontSize",p.fontSize).putFloat("lineHeight",p.lineHeight).putBoolean("lineHeight_v2",true)
            .putBoolean("serif",p.serif).putString("paper",p.paper).putBoolean("twoPage",p.twoPage)
            .putString("turnStyle",p.turnStyle).putString("shelf_layout_mode",p.shelfLayout).putBoolean("comic_rtl",p.comicRtl)
        p.shelfArtwork?.let { edit.putString("shelf_artwork",it) }
        p.shelfWheel?.let { edit.putBoolean("shelf_wheel",it) }
        p.widgetDetails?.let { edit.putBoolean("widget_details",it) }
        p.immersive?.let { edit.putBoolean("comic_immersive",it) }
        p.hideStatusBar?.let { edit.putBoolean("reader_hide_status_bar",it) }
        p.defaultComicMode?.let { edit.putString("comic_mode_default",it) }
        p.shelfStartsClosed?.let { edit.putBoolean("shelf_starts_closed",it) }
        p.comicModes.forEach { (book,mode) -> edit.putString("comic-mode:$book",mode) }
        p.comicAnchors.forEach { (chapter,anchor) ->
            anchor.pageId?.let { if (!reader.contains("comic-page:$chapter")) edit.putString("comic-page:$chapter",it) }
            anchor.scrollPageId?.let {
                if (!reader.contains("comic-scroll-page:$chapter")) {
                    edit.putString("comic-scroll-page:$chapter",it).putFloat("comic-scroll-fraction:$chapter",anchor.fraction)
                }
            }
        }
        p.anchors.forEach { (k,v) -> if(!reader.contains(k)) edit.putString(k,v) }
        return edit.commit() && appearance.edit().putString("mode",p.appearance).commit()
    }
}

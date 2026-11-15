package com.will.lightreader.data
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class MigrationRows(
    val books:List<BookEntity>,val chapters:List<ChapterEntity>,val positions:List<PositionEntity>,
    val bookmarks:List<BookmarkEntity>,val excerpts:List<ExcerptEntity>,val details:List<BookDetailCacheEntity>,
)
@Serializable data class MigrationFile(val path:String,val bytes:Long,val sha256:String)
@Serializable data class MigrationPreference(val type:String,val value:JsonElement)
@Serializable data class MigrationManifest(
    val version:Int=1,val filesPath:String,val rows:MigrationRows,val files:List<MigrationFile>,
    val preferences:Map<String,Map<String,MigrationPreference>>,
)

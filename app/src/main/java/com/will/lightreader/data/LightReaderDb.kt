package com.will.lightreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class, ChapterEntity::class, PositionEntity::class,
        BookmarkEntity::class, ExcerptEntity::class, BookDetailCacheEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class LightReaderDb : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun positionDao(): PositionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun excerptDao(): ExcerptDao

    companion object {
        @Volatile private var instance: LightReaderDb? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `book_detail_cache` (" +
                    "`bookKey` TEXT NOT NULL PRIMARY KEY, `detailJson` TEXT NOT NULL, " +
                    "`detailAt` INTEGER NOT NULL, `catalogueAt` INTEGER NOT NULL)")
            }
        }

        /** v2：新增摘抄表（已有书架/下载/书签不受影响）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `excerpts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookKey` TEXT NOT NULL, `chapterUrl` TEXT NOT NULL, " +
                        "`chapterTitle` TEXT NOT NULL, `blockIndex` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): LightReaderDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<LightReaderDb>(
                    context.applicationContext,
                    name = "lightreader.db",
                    // A direct reference makes missing KSP output a compile error and
                    // keeps the generated implementation reachable under R8.
                    factory = { LightReaderDb_Impl() },
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Missing migrations must fail without deleting the user library.
                    .build().also { instance = it }
            }
    }
}

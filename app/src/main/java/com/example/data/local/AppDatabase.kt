package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.EntityItem
import com.example.data.model.EntityMention
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.EventItem
import com.example.data.model.FacePersonAssociation
import com.example.data.model.JournalEntry
import com.example.data.model.MediaItem
import com.example.data.model.Relationship
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag

@Database(
    entities = [
        JournalEntry::class,
        Tag::class,
        EntryTagCrossRef::class,
        EntityItem::class,
        EntryEntityCrossRef::class,
        EntityMention::class,
        SuggestedTag::class,
        EventItem::class,
        FacePersonAssociation::class,
        Relationship::class,
        MediaItem::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entities ADD COLUMN aliases TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `entity_mentions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` INTEGER NOT NULL,
                        `entityId` INTEGER NOT NULL,
                        `startOffset` INTEGER NOT NULL,
                        `endOffset` INTEGER NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `normalizedValue` TEXT NOT NULL,
                        `confidence` REAL NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_entryId` ON `entity_mentions` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_entityId` ON `entity_mentions` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_mentions_entryId_startOffset` ON `entity_mentions` (`entryId`, `startOffset`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `suggested_tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_suggested_tags_entryId` ON `suggested_tags` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_suggested_tags_status` ON `suggested_tags` (`status`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `location` TEXT,
                        `entityIdsJson` TEXT NOT NULL,
                        `entryIdsJson` TEXT NOT NULL,
                        `entryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startDate` ON `events` (`startDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_endDate` ON `events` (`endDate`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `face_person_associations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mediaId` INTEGER NOT NULL,
                        `faceIndex` INTEGER NOT NULL,
                        `personEntityId` INTEGER NOT NULL,
                        `associatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_person_associations_mediaId` ON `face_person_associations` (`mediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_person_associations_personEntityId` ON `face_person_associations` (`personEntityId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_face_person_associations_mediaId_faceIndex` ON `face_person_associations` (`mediaId`, `faceIndex`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mnemosyne_journal.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

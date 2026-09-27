package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.EntityItem
import com.example.data.model.EntryEntityCrossRef
import com.example.data.model.EntryTagCrossRef
import com.example.data.model.JournalEntry
import com.example.data.model.MediaItem
import com.example.data.model.Relationship
import com.example.data.model.Tag

@Database(
    entities = [
        JournalEntry::class,
        Tag::class,
        EntryTagCrossRef::class,
        EntityItem::class,
        EntryEntityCrossRef::class,
        Relationship::class,
        MediaItem::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mnemosyne_journal.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HostEntity::class,
        SshKeyEntity::class,
        KnownHostEntity::class,
        SnippetEntity::class,
        SessionHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EasyTerminalDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun snippetDao(): SnippetDao
    abstract fun sessionHistoryDao(): SessionHistoryDao

    companion object {
        @Volatile
        private var instance: EasyTerminalDatabase? = null

        fun getInstance(context: Context): EasyTerminalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EasyTerminalDatabase::class.java,
                    "easy_terminal.db"
                ).build().also { instance = it }
            }
        }
    }
}

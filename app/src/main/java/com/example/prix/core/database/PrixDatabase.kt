package com.example.prix.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        SavedSignatureEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrixDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun signatureDao(): SignatureDao

    companion object {
        @Volatile
        private var INSTANCE: PrixDatabase? = null

        fun getInstance(context: Context): PrixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrixDatabase::class.java,
                    "prix_documents.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

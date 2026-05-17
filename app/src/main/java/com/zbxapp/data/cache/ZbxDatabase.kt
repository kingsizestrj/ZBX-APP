package com.zbxapp.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProblemEntity::class], version = 1, exportSchema = false)
abstract class ZbxDatabase : RoomDatabase() {

    abstract fun problemDao(): ProblemDao

    companion object {
        private const val DB_NAME = "zbx.db"

        fun create(context: Context): ZbxDatabase = Room.databaseBuilder(
            context.applicationContext,
            ZbxDatabase::class.java,
            DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}

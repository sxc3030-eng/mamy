package com.mamy.android.data.llm.cost

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mamy.android.data.db.converter.Converters

@Database(entities = [LlmCostEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class LlmCostTestDatabase : RoomDatabase() {
    abstract fun llmCostDao(): LlmCostDao
}

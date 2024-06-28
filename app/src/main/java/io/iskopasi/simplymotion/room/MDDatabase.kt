package io.iskopasi.simplymotion.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.iskopasi.simplymotion.utils.Converters

@Database(entities = [MDLog::class], version = 1)
@TypeConverters(Converters::class)
abstract class MDDatabase : RoomDatabase() {
    abstract fun mdDao(): MDDao
}
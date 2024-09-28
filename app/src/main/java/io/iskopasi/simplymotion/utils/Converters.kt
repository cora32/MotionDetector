package io.iskopasi.simplymotion.utils

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time?.toLong()
    }

//    @TypeConverter
//    fun toLogType(value: Int) = enumValues<LogType>()[value]
//
//    @TypeConverter
//    fun fromLogType(value: LogType) = value.ordinal
}
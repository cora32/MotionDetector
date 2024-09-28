package io.iskopasi.simplymotion.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class LogType {
    START,
    STOP,
    DELETED
}

@Entity
data class MDLog(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "date") val date: Date,
    @ColumnInfo(name = "type") val type: LogType,
)
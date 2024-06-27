package io.iskopasi.simplymotion.models

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import io.iskopasi.simplymotion.room.MDDatabase
import io.iskopasi.simplymotion.room.MDLog
import io.iskopasi.simplymotion.utils.e
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


class LogsModel(application: Application) : AndroidViewModel(application) {
    var logs by mutableStateOf<List<MDLog>>(listOf())

    private val room by lazy {
        Room.databaseBuilder(
            getApplication(),
            MDDatabase::class.java, "md_db"
        ).build()
    }

    init {
        viewModelScope.launch {
            room.logDao().getAll()
                .flowOn(Dispatchers.IO).collect {
                    "--> collecting ${it.size}".e
                    logs = it
                }
        }
    }
}
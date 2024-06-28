package io.iskopasi.simplymotion.models

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.iskopasi.simplymotion.room.MDDao
import io.iskopasi.simplymotion.room.MDLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsModel @Inject constructor(
    context: Application,
    private val dao: MDDao
) : AndroidViewModel(context) {
    var logs by mutableStateOf<List<MDLog>>(listOf())

    init {
        viewModelScope.launch {
            dao.getAll()
                .flowOn(Dispatchers.IO).collect {
                    logs = it
                }
        }
    }
}
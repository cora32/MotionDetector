package io.iskopasi.simplymotion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iskopasi.simplymotion.models.LogsModel
import io.iskopasi.simplymotion.room.MDLog
import io.iskopasi.simplymotion.ui.theme.bg1
import io.iskopasi.simplymotion.ui.theme.text1
import io.iskopasi.simplymotion.ui.theme.text2
import java.text.DateFormat

@Composable
fun LogScreen(model: LogsModel) {
    val logs = remember {
        model.logs
    }
    Box(modifier = Modifier
        .background(bg1)
        .padding(vertical = 48.dp, horizontal = 24.dp)
        .fillMaxSize()
    ) {
        LazyColumn {
            items(logs) {
                LogRow(it)
            }
        }
    }
}

@Composable
fun LogRow(mdLog: MDLog) {
    Column {
        Text(
            text = mdLog.text,
            color = text2,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = DateFormat.getDateTimeInstance().format(mdLog.date),
            color = text1,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Box(modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(Color.White))
    }
}

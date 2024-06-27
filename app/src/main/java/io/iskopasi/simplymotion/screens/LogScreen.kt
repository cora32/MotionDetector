package io.iskopasi.simplymotion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iskopasi.simplymotion.UIModel
import io.iskopasi.simplymotion.room.MDLog
import io.iskopasi.simplymotion.ui.theme.bg1
import io.iskopasi.simplymotion.ui.theme.text1
import java.text.DateFormat

@Composable
fun LogScreen(uiModel: UIModel) {
    Box(modifier = Modifier
        .background(bg1)
        .padding(vertical = 48.dp, horizontal = 24.dp)
        .fillMaxSize()
    ) {
        LazyColumn {
            items(uiModel.logs) {
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
            color = text1,
            fontSize = 16.sp
        )
        Text(
            text = DateFormat.getDateTimeInstance().format(mdLog.date),
            color = text1,
            fontSize = 13.sp
        )
    }
}

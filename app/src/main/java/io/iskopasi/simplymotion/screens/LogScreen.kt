package io.iskopasi.simplymotion.screens

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iskopasi.simplymotion.R
import io.iskopasi.simplymotion.models.LogsModel
import io.iskopasi.simplymotion.room.LogType
import io.iskopasi.simplymotion.room.MDLog
import io.iskopasi.simplymotion.ui.theme.bg1
import io.iskopasi.simplymotion.ui.theme.text1
import io.iskopasi.simplymotion.ui.theme.text2
import io.iskopasi.simplymotion.ui.theme.textGreen
import io.iskopasi.simplymotion.ui.theme.textRed
import io.iskopasi.simplymotion.ui.theme.textYellow
import io.iskopasi.simplymotion.utils.df

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(model: LogsModel) {
    Box(
        modifier = Modifier
            .background(bg1)
            .padding(vertical = 48.dp, horizontal = 24.dp)
            .fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(
                count = model.logs.size,
                key = { index ->
                    index
                },
                itemContent = { index ->
                    Column(
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        LogRow(model.logs[index])
                        Divider(
                            color = Color.White
                        )
                    }
                })
        }
    }
}

@Composable
fun LogRow(mdLog: MDLog) {
    val elapsed = remember {
        DateUtils
            .formatElapsedTime((System.currentTimeMillis() - mdLog.date.time) / 1000)
    }
    val dateString = remember {
        df.format(mdLog.date)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = mdLog.text,
            color = text2,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = dateString,
                color = text1,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = stringResource(id = R.string.ago, elapsed),
                color = when (mdLog.type) {
                    LogType.START -> textGreen
                    LogType.STOP -> textRed
                    LogType.DELETED -> textYellow
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

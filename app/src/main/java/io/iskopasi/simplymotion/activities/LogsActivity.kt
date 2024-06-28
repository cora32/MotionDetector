package io.iskopasi.simplymotion.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.iskopasi.simplymotion.models.LogsModel
import io.iskopasi.simplymotion.screens.LogScreen

@AndroidEntryPoint
class LogsActivity : ComponentActivity() {
    private val model: LogsModel by viewModels<LogsModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            LogScreen(model = model)
        }
    }
}
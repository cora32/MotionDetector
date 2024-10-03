package io.iskopasi.simplymotion.models

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.iskopasi.simplymotion.BuildConfig
import io.iskopasi.simplymotion.room.LogType
import io.iskopasi.simplymotion.room.MDDao
import io.iskopasi.simplymotion.room.MDLog
import io.iskopasi.simplymotion.utils.bg
import java.io.File
import java.util.Date
import javax.inject.Inject

class GeneralRepo @Inject constructor(
    private val dao: MDDao,
) {
    fun logDelete() = bg {
        dao.insert(
            MDLog(
                uid = 0,
                text = "Video deleted",
                date = Date(),
                type = LogType.DELETED
            )
        )
    }

    fun requestVideoPlay(file: File, activity: ComponentActivity) {
        val uri = FileProvider.getUriForFile(
            activity, "${BuildConfig.APPLICATION_ID}.provider", file
        )
        // Launch external activity via intent to play video recorded using our provider
        ContextCompat.startActivity(activity, Intent().apply {
            action = Intent.ACTION_VIEW
//            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
        }, null)
    }
}
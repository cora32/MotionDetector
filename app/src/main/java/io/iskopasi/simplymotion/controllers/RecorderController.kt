package io.iskopasi.simplymotion.controllers

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.camera.core.MirrorMode
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import io.iskopasi.simplymotion.utils.bg
import io.iskopasi.simplymotion.utils.e
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File


@SuppressLint("MissingPermission")
class RecorderController(
    private val context: Context,
    private val eventListener: MDEventCallback,
    rotation: Int
) {
    private var finisherJob: Job? = null
    private var recording: Recording? = null

    //    private var pendingRecording: PendingRecording
    var videoCapture: VideoCapture<Recorder>

    init {
        val recorder = Recorder.Builder().build()

        videoCapture = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
    }

    private fun getNewPendingRecording(): PendingRecording {
        // Create MediaStoreOutputOptions for our recorder
        val name = "motion_rec_${System.currentTimeMillis()}.mp4"
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Video.Media.DISPLAY_NAME, name)
//            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//        }
//        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
//            context.applicationContext.contentResolver,
//            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
////            context.dataDir.toUri()
//        ).setContentValues(contentValues)
//            .build()

        // 2. Configure Recorder and Start recording to the mediaStoreOutput.
//        pendingRecording = videoCapture.output
//            .prepareRecording(context, mediaStoreOutput)
//            .withAudioEnabled()

        // Saving to internals
        val newFile = File(context.filesDir.absolutePath + '/' + name)
        val fileOutputOptions = FileOutputOptions.Builder(newFile).build()

        return videoCapture.output
            .prepareRecording(context, fileOutputOptions)
            .withAudioEnabled()
    }


    private fun startCapturing() {
        val pendingRecording = getNewPendingRecording()

        recording =
            pendingRecording.start(ContextCompat.getMainExecutor(context.applicationContext)) { event ->

                when (event) {
                    is VideoRecordEvent.Start -> {
                        eventListener(MDEvent.VIDEO_START, null)
                    }

                    is VideoRecordEvent.Finalize -> {
                        finalize()

                        if (event.hasError()) {
                            Toast.makeText(
                                context,
                                "Failed to save video: ${event.error}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    fun startOrContinueCapturing() {
        // If no recording is currently ongoing, start a new capturing
        if (recording == null) {
            startCapturing()
        }

        // A job to stop recording and save video after several times passes
        finisherJob?.cancel()
        finisherJob = bg {
            delay(3000L)

            "--> Stopping recorder by timeout".e
            stop()
        }
    }

    private fun finalize() {
        eventListener(MDEvent.VIDEO_STOP, null)
        finisherJob?.cancel()
        recording?.stop()
        recording?.close()
    }

    fun stop() {
        finisherJob?.cancel()

        recording?.stop()
        recording = null
    }
}
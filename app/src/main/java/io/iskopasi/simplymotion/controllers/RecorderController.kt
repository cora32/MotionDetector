package io.iskopasi.simplymotion.controllers

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.MirrorMode
import androidx.camera.video.MediaStoreOutputOptions
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


@SuppressLint("MissingPermission")
class RecorderController(
    private val context: Application,
    private val eventListener: (MotionDetectorEvent) -> Unit,
    rotation: Int
) {
    private var finisherJob: Job? = null
    private var recording: Recording? = null
    private var pendingRecording: PendingRecording
    var videoCapture: VideoCapture<Recorder>

    init {
        // Create MediaStoreOutputOptions for our recorder
        val name = "motion_rec_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues)
            .build()

        val recorder = Recorder.Builder().build()

        videoCapture = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()

        // 2. Configure Recorder and Start recording to the mediaStoreOutput.
        pendingRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutput)
            .withAudioEnabled()
    }

    fun start() {
        if (recording == null) {
            recording =
                pendingRecording.start(ContextCompat.getMainExecutor(context.applicationContext)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            eventListener(MotionDetectorEvent.VIDEO_START)
                        }

                        is VideoRecordEvent.Finalize -> {
                            finalize()

                            val msg = if (!event.hasError()) {
                                "Video capture succeeded: ${event.outputResults.outputUri}".e

                            } else {
                                "err: ${event.error}".e
                            }

                            Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
        }

        finisherJob?.cancel()
        finisherJob = bg {
            delay(3000L)

            "--> Stopping recorder by timeout".e
            stop()
        }
    }

    private fun finalize() {
        eventListener(MotionDetectorEvent.VIDEO_STOP)
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
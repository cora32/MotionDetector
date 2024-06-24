package io.iskopasi.simplymotion.controllers

import android.os.CountDownTimer
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.window.layout.WindowMetricsCalculator
import io.iskopasi.simplymotion.MotionAnalyzer
import io.iskopasi.simplymotion.OnAnalyzeResult
import io.iskopasi.simplymotion.utils.e
import io.iskopasi.simplymotion.utils.getMinSizeFront
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class MotionDetectorEvent(var timer: Long? = null) {
    ARMED,
    DISARMED,
    VIDEO_START,
    VIDEO_STOP,
    TIMER,
}

class MotionDetectorController(
    val
    eventListener: (MotionDetectorEvent) -> Unit,
    private val sensitivity: Int,
) {
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var provider: ProcessCameraProvider
    private lateinit var preview: Preview
    private var camera: Camera? = null
    private var motionAnalyzer: MotionAnalyzer? = null
    private var recController: RecorderController? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private val RATIO_4_3_VALUE = 4.0 / 3.0
    private val RATIO_16_9_VALUE = 16.0 / 9.0
    private var armJob: CountDownTimer? = null
    private var isArmed = false

    fun onStart() {
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    fun onStop() {
        cameraExecutor.shutdown()
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    suspend fun setupCamera(
        context: ComponentActivity,
        surfaceProvider: Preview.SurfaceProvider,
        onAnalyzeResult: OnAnalyzeResult,
    ) {
        val rotation = ContextCompat.getDisplayOrDefault(context.application).rotation
        recController = RecorderController(context.application, eventListener, Surface.ROTATION_0)

        provider = ProcessCameraProvider.getInstance(context.application).await()
        lensFacing = CameraSelector.LENS_FACING_FRONT

        val metrics =
            WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(context.application).bounds
//        val aspect = aspectRatio(metrics.width(), metrics.height())

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val resolutionSelectorPreview = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    AspectRatio.RATIO_16_9,
                    AspectRatioStrategy.FALLBACK_RULE_NONE
                )
            )
//            .setResolutionStrategy(
//                ResolutionStrategy(
//                    getMaxSizeFront(this),
//                    ResolutionStrategy.FALLBACK_RULE_NONE
//                )
//            )
            .build()
        val resolutionSelectorAnalyze = ResolutionSelector.Builder()
//            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_16_9,
//                AspectRatioStrategy.FALLBACK_RULE_NONE
//            ))
            .setResolutionStrategy(
                ResolutionStrategy(
                    getMinSizeFront(context.application),
                    ResolutionStrategy.FALLBACK_RULE_NONE
                )
            )
            .build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setResolutionSelector(resolutionSelectorPreview)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        val imageAnalysis = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setResolutionSelector(resolutionSelectorAnalyze)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(Surface.ROTATION_90)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                motionAnalyzer = MotionAnalyzer(metrics.width(), metrics.height(), sensitivity)
                { bitmap, detectRect ->
                    onAnalyzeResult(bitmap, detectRect)
                }
                it.setAnalyzer(cameraExecutor, motionAnalyzer!!)
            }

        // Must unbind the use-cases before rebinding them
        provider.unbindAll()


        // Must remove observers from the previous camera instance
        if (camera != null) {
            camera!!.cameraInfo.cameraState.removeObservers(context)
        }

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = provider.bindToLifecycle(
                context,
                cameraSelector,
                preview,
                imageAnalysis,
                recController!!.videoCapture
            )

            // Attach the viewfinder's surface provider to preview use case
            preview.surfaceProvider = surfaceProvider
//            observeCameraState(camera!!.cameraInfo)
        } catch (exc: Exception) {
            "Use case binding failed".e
        }
    }

    fun startVideo(): Boolean {
        if (isArmed) {
            recController?.start()

            return true
        }

        return false
    }

    fun stopVideo() {
        recController?.stop()
    }

    fun resumeAnalyzer() {
        motionAnalyzer?.resume()
    }

    fun pauseAnalyzer() {
        motionAnalyzer?.pause()
    }

    fun isAnalyzeAllowed() = motionAnalyzer!!.isAllowed

    fun arm() {
        armJob?.cancel()
        isArmed = true
        eventListener(MotionDetectorEvent.ARMED)
    }

    fun disarm() {
        armJob?.cancel()
        isArmed = false

        recController?.stop()
        eventListener(MotionDetectorEvent.DISARMED)
    }

    fun armDelayed(initialDelay: Long) {
        armJob = object : CountDownTimer(initialDelay, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                eventListener(
                    MotionDetectorEvent.TIMER
                        .apply {
                            this.timer = millisUntilFinished
                        })
            }

            override fun onFinish() {
                arm()
                cancel()
                armJob = null
            }

        }.start()
    }

    fun setSensitivity(sensitivity: Int) {
        motionAnalyzer?.setSensitivity(sensitivity)
    }
}
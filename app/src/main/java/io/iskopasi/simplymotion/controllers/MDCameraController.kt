package io.iskopasi.simplymotion.controllers

import android.os.CountDownTimer
import android.view.Surface
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
import androidx.window.layout.WindowMetricsCalculator
import io.iskopasi.simplymotion.MotionAnalyzer
import io.iskopasi.simplymotion.MotionDetectorForegroundService
import io.iskopasi.simplymotion.ResultCallback
import io.iskopasi.simplymotion.utils.e
import io.iskopasi.simplymotion.utils.getMinSizeFront
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias MDEventCallback = (MDEvent, Long?) -> Unit

enum class MDCommand {
    //    START_VIDEO,
    ARM,
    ARM_DELAYED,
    DISARM,
    SET_THRESHOLD,
    REQUEST_STATE,
    SWITCH_CAMERA_TO_FRONT,
    SWITCH_CAMERA_TO_REAR,
}

enum class MDEvent {
    ARMED,
    DISARMED,
    VIDEO_START,
    VIDEO_STOP,
    TIMER,
    CAMERA_REAR,
    CAMERA_FRONT,
}

class MDCameraController(
    service: MotionDetectorForegroundService,
    var isFront: Boolean,
    val threshold: Int,
    val eventCallback: MDEventCallback,
) {
    companion object {
        private var resultCallback: ResultCallback? = null
        val preview: Preview by lazy {
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_16_9,
                        AspectRatioStrategy.FALLBACK_RULE_NONE
                    )
                )
                .build()

            Preview.Builder()
                // We request aspect ratio but no resolution
                .setResolutionSelector(selector)
                // Set initial target rotation
//                .setTargetRotation(rotation)
                .build()
        }

        fun setResultCallback(callback: ResultCallback) {
            resultCallback = callback
        }

        fun callbackResultCallback() {
            resultCallback = null
        }

        fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
            preview.surfaceProvider = surfaceProvider
        }

        fun clearSurfaceProvider() {
            preview.surfaceProvider = null
        }

        fun onPause() {
            callbackResultCallback()
            clearSurfaceProvider()
        }
    }

    private var firstDetectionTime = -1L
    private var lastDetectionTime = -1L
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var motionAnalyzer: MotionAnalyzer? = null
    private var armJob: CountDownTimer? = null
    private val recController by lazy {
        RecorderController(service.applicationContext, eventCallback, Surface.ROTATION_0)
    }
    private val metrics by lazy {
        WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(service).bounds
    }
    var isArmed = false

    private fun getCameraSelector(): CameraSelector {
        val lensFacing =
            if (isFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        return CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    private fun getImageAnalysis(service: MotionDetectorForegroundService): ImageAnalysis {
        val resolutionSelectorAnalyze = ResolutionSelector.Builder()
//            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_16_9,
//                AspectRatioStrategy.FALLBACK_RULE_NONE
//            ))
            .setResolutionStrategy(
                ResolutionStrategy(
                    getMinSizeFront(service),
                    ResolutionStrategy.FALLBACK_RULE_NONE
                )
            )
            .build()

        // ImageAnalysis
        return ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setResolutionSelector(resolutionSelectorAnalyze)
            // Set initial target rotation, we will have to call service again if rotation changes
            // during the lifecycle of service use case
//            .setTargetRotation(Surface.ROTATION_180)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                motionAnalyzer = MotionAnalyzer(
                    metrics.width(),
                    metrics.height(),
                    threshold,
                    isFront
                )
                { bitmap, detectRect ->
                    resultCallback?.invoke(bitmap, detectRect)

                    detectRect?.let { rect ->
                        if (!rect.isEmpty) {
                            if (System.currentTimeMillis() - lastDetectionTime > 2000L) {
                                firstDetectionTime = -1L
                            }

                            if (firstDetectionTime == -1L) {
                                firstDetectionTime = System.currentTimeMillis()
                            }

                            if (isArmed && detectionsAreStable()) {
                                newDetection()
                            }

                            lastDetectionTime = System.currentTimeMillis()
                        }
                    }
                }
                it.setAnalyzer(cameraExecutor, motionAnalyzer!!)
            }
    }

    private fun detectionsAreStable() = System.currentTimeMillis() - firstDetectionTime > 500L

    suspend fun startCamera(service: MotionDetectorForegroundService) {
        val provider = ProcessCameraProvider.getInstance(service).await()

        // Must unbind the use-cases before rebinding them
        provider.unbindAll()

        // Must remove observers from the previous camera instance
        if (camera != null) {
            camera!!.cameraInfo.cameraState.removeObservers(service)
        }

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = provider.bindToLifecycle(
                service,
                getCameraSelector(),
                preview,
                getImageAnalysis(service),
                recController.videoCapture
            )

            // Attach the viewfinder's surface provider to preview use case
//            preview.surfaceProvider = surfaceProvider
//            observeCameraState(camera!!.cameraInfo)
        } catch (exc: Exception) {
            "Use case binding failed".e
        }
    }

    private fun newDetection() {
        recController.startOrContinueCapturing()
    }

    fun arm() {
        armJob?.cancel()
        isArmed = true
        eventCallback(MDEvent.ARMED, null)
    }

    fun disarm() {
        armJob?.cancel()
        isArmed = false

        recController.stop()
        eventCallback(MDEvent.DISARMED, null)
    }

    fun armDelayed(initialDelay: Long) {
        armJob = object : CountDownTimer(initialDelay, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                eventCallback(MDEvent.TIMER, millisUntilFinished)
            }

            override fun onFinish() {
                arm()
                cancel()
                armJob = null
            }

        }.start()
    }

    fun setThreshold(threshold: Int) {
        motionAnalyzer?.setSensitivity(threshold)
    }

    suspend fun setCameraFront(service: MotionDetectorForegroundService) {
        isFront = true
        eventCallback(MDEvent.CAMERA_FRONT, null)

        startCamera(service)
    }

    suspend fun setCameraRear(service: MotionDetectorForegroundService) {
        isFront = false
        eventCallback(MDEvent.CAMERA_REAR, null)

        startCamera(service)
    }
}
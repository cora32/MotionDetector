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
    START_VIDEO,
    ARM,
    ARM_DELAYED,
    DISARM,
    SET_THRESHOLD,
}

enum class MDEvent {
    ARMED,
    DISARMED,
    VIDEO_START,
    VIDEO_STOP,
    TIMER,
}

class MDCameraController(val eventCallback: MDEventCallback) {
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

    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var motionAnalyzer: MotionAnalyzer? = null
    private var armJob: CountDownTimer? = null
    private var isArmed = false
    private var recController: RecorderController? = null

    suspend fun startCamera(service: MotionDetectorForegroundService) {
//        val rotation = ContextCompat.getDisplayOrDefault(service).rotation
        recController = RecorderController(service, eventCallback, Surface.ROTATION_0)

        val provider = ProcessCameraProvider.getInstance(service).await()
        val lensFacing = CameraSelector.LENS_FACING_FRONT

        val metrics =
            WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(service).bounds
//        val aspect = aspectRatio(metrics.width(), metrics.height())

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

//        val resolutionSelectorPreview = getSelectorPreview()
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
        val imageAnalysis = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setResolutionSelector(resolutionSelectorAnalyze)
            // Set initial target rotation, we will have to call service again if rotation changes
            // during the lifecycle of service use case
            .setTargetRotation(Surface.ROTATION_90)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                motionAnalyzer = MotionAnalyzer(metrics.width(), metrics.height(), 10)
                { bitmap, detectRect ->
                    resultCallback?.invoke(bitmap, detectRect)
                }
                it.setAnalyzer(cameraExecutor, motionAnalyzer!!)
            }

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
                cameraSelector,
                preview,
                imageAnalysis,
                recController!!.videoCapture
            )

            // Attach the viewfinder's surface provider to preview use case
//            preview.surfaceProvider = surfaceProvider
//            observeCameraState(camera!!.cameraInfo)
        } catch (exc: Exception) {
            "Use case binding failed".e
        }
    }

//    fun onStart() {
//        "--> MotionDetectorController onStart".e
//        if (!cameraExecutor.isShutdown) {
//            cameraExecutor.shutdown()
//            cameraExecutor = Executors.newSingleThreadExecutor()
//        }
//    }

//    fun onStop() {
//        "--> MotionDetectorController onStop shutting down executor".e
//        cameraExecutor.shutdown()
//    }

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
        eventCallback(MDEvent.ARMED, null)
    }

    fun disarm() {
        armJob?.cancel()
        isArmed = false

        recController?.stop()
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
}
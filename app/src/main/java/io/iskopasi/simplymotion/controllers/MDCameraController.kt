package io.iskopasi.simplymotion.controllers

import android.os.CountDownTimer
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
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

class MDCameraController(var isFront: Boolean, val eventCallback: MDEventCallback) {
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
    private var recController: RecorderController? = null
    var isArmed = false

    private fun getCameraSelector(): CameraSelector {
        val lensFacing =
            if (isFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        return CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    private fun getImageAnalysis(service: MotionDetectorForegroundService): ImageAnalysis {
        val metrics =
            WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(service).bounds

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
    }

    suspend fun startCamera(service: MotionDetectorForegroundService) {
//        val rotation = ContextCompat.getDisplayOrDefault(service).rotation
        recController = RecorderController(service, eventCallback, Surface.ROTATION_0)

        val provider = ProcessCameraProvider.getInstance(service).await()
//        val aspect = aspectRatio(metrics.width(), metrics.height())

//        val resolutionSelectorPreview = getSelectorPreview()

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
                recController!!.videoCapture
            )

            // Attach the viewfinder's surface provider to preview use case
//            preview.surfaceProvider = surfaceProvider
//            observeCameraState(camera!!.cameraInfo)
        } catch (exc: Exception) {
            "Use case binding failed".e
        }
    }

    private fun observeCameraState(context: ComponentActivity, cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(context) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Toast.makeText(
                            context,
                            "CameraState: Pending Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Toast.makeText(
                            context,
                            "CameraState: Opening",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Toast.makeText(
                            context,
                            "CameraState: Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Toast.makeText(
                            context,
                            "CameraState: Closing",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Toast.makeText(
                            context,
                            "CameraState: Closed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Toast.makeText(
                            context,
                            "Stream config error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(
                            context,
                            "Camera in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Toast.makeText(
                            context,
                            "Max cameras in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Toast.makeText(
                            context,
                            "Other recoverable error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(
                            context,
                            "Camera disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(
                            context,
                            "Fatal error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(
                            context,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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
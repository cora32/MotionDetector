package io.iskopasi.simplymotion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import io.iskopasi.simplymotion.ui.theme.SimplyMotionTheme
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class UIController : ViewModel() {
    var detectRectState by mutableStateOf<Rect?>(Rect())
}

class MainActivity : ComponentActivity() {
    private val controller: UIController by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private lateinit var provider: ProcessCameraProvider
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private val RATIO_4_3_VALUE = 4.0 / 3.0
    private val RATIO_16_9_VALUE = 16.0 / 9.0
    private lateinit var preview: androidx.camera.core.Preview

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.all { it }) {

            } else {
                Toast.makeText(this, "We need your permission", Toast.LENGTH_LONG)
            }
        }

    private fun checkPermissions(context: Context) = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onPause() {
        super.onPause()

        cameraExecutor.shutdown()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkPermissions(this)) {
            cameraPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

        enableEdgeToEdge()

        // Lock screen brightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val viewfinder = PreviewView(this).apply {
            post {
                setLayoutParams(
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
        val imageView = ImageView(this)

        lifecycleScope.launch {
            setupCamera(viewfinder.surfaceProvider, imageView)
        }

        setContent {
            SimplyMotionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = {
                                viewfinder
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val rect = controller.detectRectState

                                rect?.let {
                                    drawRect(
                                        color = Color.Red,
                                        topLeft = Offset(
                                            rect.left.toFloat(),
                                            rect.top.toFloat()
                                        ),
                                        size = Size(
                                            rect
                                                .width()
                                                .toFloat(),
                                            rect
                                                .height()
                                                .toFloat()
                                        ),
                                        style = Stroke(5.0f)
                                    )
                                }
                            }) {}
                    }
                }
            }
        }
    }

    private suspend fun setupCamera(
        surfaceProvider: Preview.SurfaceProvider,
        imageView: ImageView,
    ) {
        provider = ProcessCameraProvider.getInstance(this).await()
        lensFacing = CameraSelector.LENS_FACING_FRONT

        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
        val aspect = aspectRatio(metrics.width(), metrics.height())
        val rotation = ContextCompat.getDisplayOrDefault(this).rotation

        "--> rotation: $rotation".e
        val cameraProvider = provider

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
                    getMinSizeFront(this),
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
        val imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setResolutionSelector(resolutionSelectorAnalyze)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(Surface.ROTATION_90)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, MotionAnalyzer(metrics.width(), metrics.height())
                { bitmap, detectRect ->
                    bitmap.recycle()

                    if (!detectRect.isEmpty) {
                        controller.detectRectState = detectRect
                    } else {
                        controller.detectRectState = null
                    }
                })
            }

        // Must unbind the use-cases before rebinding them
        provider.unbindAll()


        // Must remove observers from the previous camera instance
        if (camera != null) {
            camera!!.cameraInfo.cameraState.removeObservers(this)
        }

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(surfaceProvider)
//            observeCameraState(camera!!.cameraInfo)
        } catch (exc: Exception) {
            "Use case binding failed".e
        }
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(this) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Toast.makeText(
                            this,
                            "CameraState: Pending Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Toast.makeText(
                            this,
                            "CameraState: Opening",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Toast.makeText(
                            this,
                            "CameraState: Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Toast.makeText(
                            this,
                            "CameraState: Closing",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Toast.makeText(
                            this,
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
                            this,
                            "Stream config error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(
                            this,
                            "Camera in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Toast.makeText(
                            this,
                            "Max cameras in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Toast.makeText(
                            this,
                            "Other recoverable error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(
                            this,
                            "Camera disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(
                            this,
                            "Fatal error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(
                            this,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
}
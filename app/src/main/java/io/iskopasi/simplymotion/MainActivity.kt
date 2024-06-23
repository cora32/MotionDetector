package io.iskopasi.simplymotion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.iskopasi.simplymotion.controllers.MotionDetectorController
import io.iskopasi.simplymotion.controllers.MotionDetectorEvent
import io.iskopasi.simplymotion.ui.theme.SimplyMotionTheme
import io.iskopasi.simplymotion.utils.OrientationListener
import io.iskopasi.simplymotion.utils.e
import io.iskopasi.simplymotion.utils.ui
import kotlinx.coroutines.launch


class UIModel : ViewModel() {
    var detectRectState by mutableStateOf<Rect?>(null)
    var isRecording by mutableStateOf(false)
    var isArmed by mutableStateOf(false)
    var timerValue by mutableStateOf<String?>(null)
    var isArming by mutableStateOf(false)
    var orientation by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    private val eventListener: (MotionDetectorEvent) -> Unit = { event ->
        when (event) {
            MotionDetectorEvent.ARMED -> {
                uiModel.isArmed = true
                uiModel.isArming = false
            }

            MotionDetectorEvent.DISARMED -> {
                uiModel.isArmed = false
            }

            MotionDetectorEvent.VIDEO_START -> uiModel.isRecording = true
            MotionDetectorEvent.VIDEO_STOP -> {
                uiModel.isRecording = false

                ui {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MotionDetectorEvent.TIMER -> {
                val value = (event.timer!! / 1000L).toInt()

                if (value == 0) {
                    uiModel.timerValue = null
                } else {
                    uiModel.timerValue = value.toString()
                }
            }
        }
    }
    private val uiModel: UIModel by viewModels()
    private var motionDetectorController: MotionDetectorController =
        MotionDetectorController(eventListener)
    private var orientationListener: OrientationListener? = null

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

    override fun onStart() {
        super.onStart()

        motionDetectorController.onStart()
    }

    override fun onStop() {
        super.onStop()

        motionDetectorController.onStop()
    }

    override fun onResume() {
        super.onResume()
        orientationListener?.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationListener?.disable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orientationListener = object : OrientationListener(this@MainActivity) {
            override fun onSimpleOrientationChanged(orientation: Int, currentOrientation: Int) {
                "---> ORIENT: $orientation".e
                uiModel.orientation = orientation
            }
        }

        if (!checkPermissions(this)) {
            cameraPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

//        enableEdgeToEdge()

        // Lock screen brightness
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

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

        lifecycleScope.launch {
            motionDetectorController.setupCamera(
                this@MainActivity,
                viewfinder.surfaceProvider,
            )
            { bitmap, detectRect ->
                if (!detectRect.isEmpty) {
                    uiModel.detectRectState = detectRect

                    if (motionDetectorController.startVideo()) {
                        ui {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                } else {
                    uiModel.detectRectState = null
                }
            }
        }

        setContent {
            SimplyMotionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UIComposable(innerPadding, viewfinder)
                }
            }
        }
    }

    @Composable
    private fun UIComposable(innerPadding: PaddingValues, viewfinder: PreviewView) {
        "---> uiModel.orientation: ${uiModel.orientation}".e
        val rotation: Float by animateFloatAsState(
            -uiModel.orientation.toAngle().toFloat(),
            label = ""
        )

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
                    val rect = uiModel.detectRectState

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
                })
            if (uiModel.isRecording) Box(
                modifier = Modifier
                    .padding(top = 32.dp, end = 32.dp)
                    .size(32.dp)
                    .clip(
                        RoundedCornerShape(32.dp)
                    )
                    .background(Color.Red)
                    .align(Alignment.TopEnd)
            )
            if (uiModel.timerValue != null) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .5f))
            ) {
                Crossfade(
                    targetState = uiModel.timerValue, label = uiModel.timerValue.toString(),
                    modifier = Modifier
                        .align(Alignment.Center)
                ) {
                    it?.let {
                        Text(
                            modifier = Modifier
                                .rotate(rotation),
                            text = it,
                            color = Color.White,
                            fontSize = 32.sp
                        )
                    }

                }
            }
            if (uiModel.isArmed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.0f),
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.3f),
                                )
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        modifier = Modifier
                            .size(48.dp),

                        onClick = {
                            motionDetectorController.disarm()
                        }) {
                        Icon(
                            Icons.Rounded.Clear,
                            "Disarm",
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation)
                        )
                    }
                }
            } else {
                if (!uiModel.isArming) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.0f),
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.3f),
                                )
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        modifier = Modifier
                            .size(48.dp),

                        onClick = {
                            motionDetectorController.arm()
                        }) {
                        Icon(
                            Icons.Rounded.Security,
                            "Arm",
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation)
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .size(48.dp),

                        onClick = {
                            motionDetectorController.armDelayed(10000L)
                            uiModel.isArming = true
                        }) {
                        Icon(
                            Icons.Rounded.Timer10,
                            "Arm in 10 seconds",
                            modifier = Modifier
                                .size(64.dp)
                        )
                    }
                }
            }
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
}

private fun Int.toAngle() = when (this) {
    Surface.ROTATION_0 -> 180
    Surface.ROTATION_90 -> 0
    Surface.ROTATION_180 -> 90
    Surface.ROTATION_270 -> 270
    else -> 0
}

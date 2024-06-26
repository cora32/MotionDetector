package io.iskopasi.simplymotion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.iskopasi.simplymotion.controllers.MDCameraController
import io.iskopasi.simplymotion.ui.theme.SimplyMotionTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private lateinit var drawerState: DrawerState
    private lateinit var scope: CoroutineScope
    private lateinit var focusManager: FocusManager
    private val uiModel: UIModel by viewModels()
    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.all { it }) {

            } else {
                Toast.makeText(this, "We need your permission", Toast.LENGTH_LONG)
            }
        }

    private fun checkPermissions(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkPermissions(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cameraPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                )
            } else {
                cameraPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

//        enableEdgeToEdge()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Lock screen brightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val viewfinder = PreviewView(this).apply {
            post {
                setLayoutParams(
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                MDCameraController.setSurfaceProvider(this.surfaceProvider)
            }
        }

        lifecycle.removeObserver(uiModel)
        lifecycle.addObserver(uiModel)
//        uiModel.setupCamera(this, viewfinder.surfaceProvider)

        uiModel.startService(this)

        lifecycleScope.launch {
            uiModel.isBrightnessUp.collect { isBrightnessUp ->
                if (isBrightnessUp) {
                    brightnessUp()
                } else {
                    brightnessDown()
                }
            }
        }

        setContent {
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            scope = rememberCoroutineScope()
            focusManager = LocalFocusManager.current

            BackHandler {
                closeDrawer()
            }

            SimplyMotionTheme {
                Scaffold(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            closeDrawer()
                        }
                    }) { innerPadding ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    MenuComposable()
                                }
                            },
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                UIComposable(
                                    innerPadding,
                                    viewfinder
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun brightnessDown() = lifecycleScope.launch {
//        context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.attributes = window.attributes.apply {
            dimAmount = 1f
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun brightnessUp() = lifecycleScope.launch {
        window.attributes = window.attributes.apply {
            dimAmount = 0.5f
            screenBrightness = 0.5f
        }
    }

    private fun closeDrawer() {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        focusManager.clearFocus(true)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun MenuComposable() {
        var sensitivityTF by rememberSaveable {
            mutableStateOf(uiModel.getSensitivity().toString())
        }
        val keyboardController = LocalSoftwareKeyboardController.current

        Box(
            modifier = Modifier
                .background(Color(0xFF071932))
                .fillMaxHeight()
                .width(250.dp)
                .padding(vertical = 64.dp, horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        closeDrawer()
                    }
                }
        ) {
            Column() {
                OutlinedTextField(
                    value = sensitivityTF,
                    singleLine = true,
                    label = { Text("Threshold:", fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
//                        focusedContainerColor = Color.White,
//                        unfocusedContainerColor = Color.White,
//                        disabledContainerColor = Color.White
                    ),
                    onValueChange = {
                        if (it.isNotEmpty()) {
                            sensitivityTF = it
                            uiModel.saveSensitivity(it.toInt())
                        } else {
                            sensitivityTF = "0"
                            uiModel.saveSensitivity(0)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }
                    ),
//                    modifier = Modifier
//                        .onFocusEvent {
//                            it.hasFocus
//                        }
//                        .onFocusChanged {
////                        if(!it.hasFocus) {
////                            keyboardController?.hide()
////                        }
//                    }
                )
            }
        }
    }

    @Composable
    private fun UIComposable(innerPadding: PaddingValues, viewfinder: PreviewView) {
        val rotation: Float by animateFloatAsState(
            uiModel.orientation.toAngle().toFloat(),
            label = ""
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures {
                        closeDrawer()
                    }
                }
        ) {
            AndroidView(
                factory = {
                    viewfinder
                },
                modifier = Modifier.fillMaxSize()
            )
//            uiModel.bitmap?.let {
//                Image(
//                    bitmap = it.asImageBitmap(),
//                    contentDescription = "",
//                    modifier = Modifier.fillMaxSize(),
//                    contentScale = ContentScale.FillBounds
//                )
//            }
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
            Box(
                modifier = Modifier
                    .height(120.dp)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.0f),
                            )
                        )
                    )
            ) {
                IconButton(
                    modifier = Modifier
                        .padding(top = 56.dp, end = 16.dp)
                        .size(48.dp)
                        .align(Alignment.TopEnd),

                    onClick = {
                        scope.launch { drawerState.open() }
                    }) {
                    Icon(
                        Icons.Rounded.Menu,
                        "Disarm",
                        modifier = Modifier
                            .size(64.dp)
                            .rotate(rotation),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
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
                            uiModel.disarm()
                        }) {
                        Icon(
                            Icons.Rounded.Clear,
                            "Disarm",
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation),
                            tint = Color.White.copy(alpha = 0.7f)
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
                            uiModel.arm()
                        }) {
                        Icon(
                            Icons.Rounded.Security,
                            "Arm",
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .size(48.dp),

                        onClick = {
                            uiModel.armDelayed(10000L)
                            uiModel.isArming = true
                        }) {
                        Icon(
                            Icons.Rounded.Timer10,
                            "Arm in 10 seconds",
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (uiModel.isRecording) Box(
                modifier = Modifier
                    .padding(top = 56.dp, start = 32.dp, end = 32.dp)
                    .size(32.dp)
                    .clip(
                        RoundedCornerShape(32.dp)
                    )
                    .background(Color.Red)
                    .align(Alignment.TopStart)
            )
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
    Surface.ROTATION_0 -> 0
    Surface.ROTATION_90 -> -90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 90
    else -> 0
}

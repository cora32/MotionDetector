package io.iskopasi.simplymotion.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CameraRear
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.iskopasi.simplymotion.R
import io.iskopasi.simplymotion.controllers.MDCameraController
import io.iskopasi.simplymotion.models.UIModel
import io.iskopasi.simplymotion.ui.theme.SimplyMotionTheme
import io.iskopasi.simplymotion.ui.theme.bg1
import io.iskopasi.simplymotion.ui.theme.text1
import io.iskopasi.simplymotion.utils.toRotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private lateinit var drawerState: DrawerState
private lateinit var scope: CoroutineScope
private lateinit var focusManager: FocusManager

@Composable
fun MainScreen(
    uiModel: UIModel,
    toLogs: () -> Unit
) {
    val context = LocalContext.current
    drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    scope = rememberCoroutineScope()
    focusManager = LocalFocusManager.current

    BackHandler {
        closeDrawer()
    }

    val viewfinder = PreviewView(context).apply {
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
                            MenuComposable(uiModel, toLogs)
                        }
                    },
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        UIComposable(
                            uiModel,
                            innerPadding,
                            viewfinder,
                            toLogs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UIComposable(
    uiModel: UIModel,
    innerPadding: PaddingValues,
    viewfinder: PreviewView,
    toLogs: () -> Unit,
) {
    val rotation: Float by animateFloatAsState(
        uiModel.orientation.toRotation().toFloat(),
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
                    "Menu",
                    modifier = Modifier
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
                            .rotate(rotation),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    modifier = Modifier
                        .size(48.dp),

                    onClick = {
                        uiModel.switchCamera()
                    }) {
                    Icon(
                        if (uiModel.isFront) Icons.Rounded.CameraFront else Icons.Rounded.CameraRear,
                        "Front/back camera",
                        modifier = Modifier
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

private fun closeDrawer() {
    if (drawerState.isOpen) scope.launch { drawerState.close() }
    focusManager.clearFocus(true)
}

@Composable
private fun MenuComposable(
    uiModel: UIModel,
    toLogs: () -> Unit
) {
    var sensitivityTF by rememberSaveable {
        mutableStateOf(uiModel.getSensitivity().toString())
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .background(bg1)
            .fillMaxHeight()
            .width(250.dp)
            .padding(vertical = 64.dp, horizontal = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    closeDrawer()
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                Label(stringResource(id = R.string.settings), Modifier.padding(bottom = 16.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
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
            Button(
                onClick = toLogs,
                modifier = Modifier
                    .height(75.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = bg1,
                    contentColor = text1
                ),
                shape = RectangleShape
            ) {
                Label(stringResource(id = R.string.go_to_logs))
            }
        }
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 25.sp,
        modifier = modifier
    )
}
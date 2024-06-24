package io.iskopasi.simplymotion

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.iskopasi.simplymotion.controllers.MotionDetectorController
import io.iskopasi.simplymotion.controllers.MotionDetectorEvent
import io.iskopasi.simplymotion.utils.OrientationListener
import io.iskopasi.simplymotion.utils.PreferencesManager
import io.iskopasi.simplymotion.utils.PreferencesManager.Companion.SENSO_KEY
import io.iskopasi.simplymotion.utils.ui
import kotlinx.coroutines.launch


class UIModel(context: Application) : AndroidViewModel(context), DefaultLifecycleObserver {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var detectRectState by mutableStateOf<Rect?>(null)
    var isRecording by mutableStateOf(false)
    var isArmed by mutableStateOf(false)
    var timerValue by mutableStateOf<String?>(null)
    var isArming by mutableStateOf(false)
    var orientation by mutableIntStateOf(0)

    private val orientationListener by lazy {
        object : OrientationListener(getApplication()) {
            override fun onSimpleOrientationChanged(orientation: Int, currentOrientation: Int) {
                this@UIModel.orientation = currentOrientation
            }
        }
    }
    private lateinit var eventListener: (MotionDetectorEvent) -> Unit
    private val motionDetectorController by lazy {
        MotionDetectorController(eventListener, getSensitivity())
    }
    private val sp by lazy {
        PreferencesManager(context = context)
    }

    fun getSensitivity(): Int {
        return sp.getInt(SENSO_KEY, 8)
    }

    fun saveSensitivity(sensitivity: Int) {
        sp.saveInt(SENSO_KEY, sensitivity)
        motionDetectorController.setSensitivity(sensitivity)

        Toast.makeText(getApplication(), "Settings saved", Toast.LENGTH_SHORT).show()
    }

    fun setupCamera(context: ComponentActivity, surfaceProvider: Preview.SurfaceProvider) {
        context.lifecycleScope.launch {
            eventListener = { event ->
                when (event) {
                    MotionDetectorEvent.ARMED -> {
                        isArmed = true
                        isArming = false
                    }

                    MotionDetectorEvent.DISARMED -> {
                        isArmed = false
                    }

                    MotionDetectorEvent.VIDEO_START -> isRecording = true
                    MotionDetectorEvent.VIDEO_STOP -> {
                        isRecording = false

                        brightnessDown(context)
                    }

                    MotionDetectorEvent.TIMER -> {
                        val value = (event.timer!! / 1000L).toInt()

                        if (value == 0) {
                            timerValue = null
                        } else {
                            timerValue = value.toString()
                        }
                    }
                }
            }

            motionDetectorController.setupCamera(
                context,
                surfaceProvider,
            )
            { bitmap, detectRect ->
//                uiModel.bitmap = bitmap
                if (!detectRect.isEmpty) {
                    detectRectState = detectRect

                    if (motionDetectorController.startVideo()) {
                        brightnessUp(context)
                    }
                } else {
                    detectRectState = null
                }
            }
        }
    }

    private fun brightnessDown(context: ComponentActivity) = ui {
        context.window.attributes = context.window.attributes.apply {
//            dimAmount = 0.9f
            screenBrightness = BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun brightnessUp(context: ComponentActivity) = ui {
        context.window.attributes = context.window.attributes.apply {
//            dimAmount = 0.5f
            screenBrightness = 0.5f
        }
    }

    fun disarm() {
        motionDetectorController.disarm()
    }

    fun arm() {
        motionDetectorController.arm()
    }

    fun armDelayed(delay: Long) {
        motionDetectorController.armDelayed(delay)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        motionDetectorController.onStart()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        motionDetectorController.onStop()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        orientationListener.enable()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        orientationListener.disable()
    }
}
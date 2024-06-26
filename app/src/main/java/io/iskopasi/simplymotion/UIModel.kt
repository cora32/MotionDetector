package io.iskopasi.simplymotion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.iskopasi.simplymotion.controllers.MDCameraController
import io.iskopasi.simplymotion.controllers.MDCommand
import io.iskopasi.simplymotion.controllers.MDEvent
import io.iskopasi.simplymotion.utils.OrientationListener
import io.iskopasi.simplymotion.utils.PreferencesManager
import io.iskopasi.simplymotion.utils.PreferencesManager.Companion.SENSO_KEY
import io.iskopasi.simplymotion.utils.ServiceCommunicator
import kotlinx.coroutines.flow.MutableStateFlow


class UIModel(context: Application) : AndroidViewModel(context), DefaultLifecycleObserver {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var detectRectState by mutableStateOf<Rect?>(null)
    var isRecording by mutableStateOf(false)
    var isArmed by mutableStateOf(false)
    var timerValue by mutableStateOf<String?>(null)
    var isArming by mutableStateOf(false)
    var orientation by mutableIntStateOf(0)
    var isBrightnessUp = MutableStateFlow(false)

    private val resultCallback: ResultCallback =
        { bitmap, detectRect ->
//                uiModel.bitmap = bitmap
            if (!detectRect.isEmpty) {
                detectRectState = detectRect

                serviceCommunicator.sendMsg(MDCommand.START_VIDEO.name)
            } else {
                detectRectState = null
            }
        }
    private val serviceCommunicator by lazy {
        ServiceCommunicator("UIModel") { data, obj, comm ->
            when (data) {
                MDEvent.VIDEO_START.name -> {
                    isRecording = true
                    isBrightnessUp.value = true
                }

                MDEvent.VIDEO_STOP.name -> {
                    isRecording = false
                    isBrightnessUp.value = false
                }

                MDEvent.ARMED.name -> {
                    isArmed = true
                    isArming = false
                }

                MDEvent.DISARMED.name -> {
                    isArmed = false
                }

                MDEvent.TIMER.name -> {
                    val value = ((obj as Long) / 1000L).toInt()

                    timerValue = if (value == 0) {
                        null
                    } else {
                        value.toString()
                    }
                }
            }
        }
    }
    private val orientationListener by lazy {
        object : OrientationListener(getApplication()) {
            override fun onSimpleOrientationChanged(orientation: Int, currentOrientation: Int) {
                this@UIModel.orientation = currentOrientation
            }
        }
    }

    private val sp by lazy {
        PreferencesManager(context = context)
    }

    fun getSensitivity(): Int {
        return sp.getInt(SENSO_KEY, 8)
    }

    fun saveSensitivity(sensitivity: Int) {
        sp.saveInt(SENSO_KEY, sensitivity)
        serviceCommunicator.sendMsg(MDCommand.SET_THRESHOLD.name, sensitivity)

        Toast.makeText(getApplication(), "Settings saved", Toast.LENGTH_SHORT).show()
    }

    fun startService(context: ComponentActivity) {
        context.bindService(
            Intent(
                context,
                MotionDetectorForegroundService::class.java
            ),
            serviceCommunicator.serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        ContextCompat.startForegroundService(
            context,
            Intent(context, MotionDetectorForegroundService::class.java)
        )

        MDCameraController.setResultCallback(resultCallback)
    }

    private fun stopService(context: ComponentActivity) {
        context.stopService(Intent(context, MotionDetectorForegroundService::class.java))
    }

    fun disarm() {
        serviceCommunicator.sendMsg(MDCommand.DISARM.name)
    }

    fun arm() {
        serviceCommunicator.sendMsg(MDCommand.ARM.name)
    }

    fun armDelayed(delay: Long) {
        serviceCommunicator.sendMsg(MDCommand.ARM_DELAYED.name, delay)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
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
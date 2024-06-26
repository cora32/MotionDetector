package io.iskopasi.simplymotion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import io.iskopasi.simplymotion.controllers.MDCameraController
import io.iskopasi.simplymotion.controllers.MDCommand
import io.iskopasi.simplymotion.utils.CommunicatorCallback
import io.iskopasi.simplymotion.utils.ServiceCommunicator
import io.iskopasi.simplymotion.utils.e
import io.iskopasi.simplymotion.utils.notificationManager
import kotlinx.coroutines.launch


class MotionDetectorForegroundService : LifecycleService() {

    private val pendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val commandHandler: CommunicatorCallback = { data, obj, comm ->
        when (data) {
            MDCommand.START_VIDEO.name -> {
                mdCameraController.startVideo()
            }

            MDCommand.ARM.name -> {
                mdCameraController.arm()
            }

            MDCommand.DISARM.name -> {
                mdCameraController.disarm()
            }

            MDCommand.ARM_DELAYED.name -> {
                mdCameraController.armDelayed(obj as Long)
            }

            MDCommand.SET_THRESHOLD.name -> {
                mdCameraController.setThreshold(obj as Int)
            }
        }
    }

    private val mdCameraController by lazy {
        MDCameraController { event, time ->
            serviceCommunicator.sendMsg(event.name, time)
        }
    }

    // Receives commands from activity
    private val serviceCommunicator = ServiceCommunicator("Service", commandHandler)

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)

        return serviceCommunicator.onBind()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        "---> onStartCommand ".e

        val notificationId = 123
        val channelId = getChannel()
        val notification = NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher_round)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setContentTitle("Detecting motions...")
            setContentText("Foreground service to keep camera on")
            setContentIntent(pendingIntent)
            setOngoing(true)
            setAutoCancel(false)
        }.build()

        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else 0
        )

        lifecycle.coroutineScope.launch {
            mdCameraController.startCamera(this@MotionDetectorForegroundService)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        "---> onDestroy ".e
        super.onDestroy()
    }

    private fun getChannel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "motion_detection_channel"
            val channelName = "Motion detection channel"

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = Color.RED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager!!.createNotificationChannel(channel)

            channelId
        } else {
            ""
        }
    }
}
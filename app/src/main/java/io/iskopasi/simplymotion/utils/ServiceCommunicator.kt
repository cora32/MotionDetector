package io.iskopasi.simplymotion.utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import io.iskopasi.simplymotion.utils.ServiceCommunicator.Companion.STRING_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


typealias CommunicatorCallback = (String?, Any?, ServiceCommunicator) -> Unit

class ServiceCommunicator(val tag: String, callback: CommunicatorCallback) {
    companion object {
        const val BIND_ME = "BIND_ME"
        const val STRING_KEY = "STRING_KEY"
    }

    private val buffer = mutableListOf<Pair<String, Any?>>()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var handler: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            val data = msg.data.getString(STRING_KEY)
            val obj = msg.obj

            "--> [$tag] Received: $data".e
            if (data == BIND_ME) {
                "--> [$tag] - binding sender: ${msg.replyTo}".e
                messengerSender = msg.replyTo
                flush()
            } else {
                callback(data, obj, this@ServiceCommunicator)
            }
        }
    }
    private var messengerSender: Messenger? = null
    private var messengerReceiver = Messenger(handler)
    val serviceConnection by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                messengerSender = Messenger(service).apply {
                    // Sending service message to bind a reply messenger on other side
                    sendMsg(BIND_ME, null, replyTo = messengerReceiver)
                }
            }

            override fun onServiceDisconnected(arg0: ComponentName) {}
        }
    }

    private fun flush() = coroutineScope.launch {
        for (msg in buffer) {
            "--->[$tag] flushing: $msg".e
            messengerSender?.sendMsg(msg.first, obj = msg.second, replyTo = messengerReceiver)
        }

        buffer.clear()
    }

    fun sendMsg(msg: String, obj: Any? = null) {
        coroutineScope.launch {
            if (messengerSender == null) {
                "--->[$tag] buffering: $msg $obj".e
                buffer.add(Pair(msg, obj))
            } else {
                flush()
                "--->[$tag] sending: $msg $obj".e
                messengerSender?.sendMsg(msg, obj = obj, replyTo = messengerReceiver)
            }
        }
    }

    fun onBind(): IBinder? {
        return Messenger(handler).binder
    }
}

fun Messenger?.sendMsg(message: String, obj: Any?, replyTo: Messenger? = null) {
    val msg = Message.obtain(null, 0, 0, 0).apply {
        data = Bundle().apply {
            putString(STRING_KEY, message)
        }
        this.replyTo = replyTo
        this.obj = obj
    }

    try {
        this?.send(msg)
    } catch (e: RemoteException) {
        e.printStackTrace()
    }
}
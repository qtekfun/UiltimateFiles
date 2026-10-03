package com.qtekfun.fexplo.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.qtekfun.fexplo.domain.repository.VolumeChangeSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Listens to the system broadcasts that announce storage and USB devices coming and going. */
class SystemVolumeMonitor(private val context: Context) : VolumeChangeSource {

    override val changes: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val usb = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val media = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file") // media broadcasts only match when a scheme is declared
        }
        // System broadcasts are delivered whatever the flag; Android 14 just requires one.
        ContextCompat.registerReceiver(context, receiver, usb, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, media, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

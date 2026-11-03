package com.orbiusb.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor

/** AOA USB session. Active only while the owning Activity is in the foreground. */
class UsbAccessorySession(
    context: Context,
    private val onStateChanged: (connected: Boolean, message: String) -> Unit
) {
    companion object {
        private const val ACTION_PERMISSION = "com.orbiusb.mobile.USB_ACCESSORY_PERMISSION"
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private var registered = false
    private var descriptor: ParcelFileDescriptor? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> inspect()
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    close()
                    onStateChanged(false, "Dispositivo USB desconectado")
                }
                ACTION_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val accessory = accessoryExtra(intent)
                    if (granted && accessory != null) open(accessory)
                    else onStateChanged(false, "Permiso USB no concedido")
                }
            }
        }
    }

    /** Call from Activity.onResume(). */
    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
                addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                addAction(ACTION_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        }
        inspect()
    }

    /** Call from Activity.onPause(). */
    fun stop() {
        if (registered) {
            try { appContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
            registered = false
        }
        close()
        onStateChanged(false, "OrbiUSB en pausa")
    }

    private fun inspect() {
        val accessory = manager.accessoryList?.firstOrNull()
        if (accessory == null) {
            onStateChanged(false, "Esperando conexión USB AOA")
            return
        }
        if (manager.hasPermission(accessory)) {
            open(accessory)
        } else {
            val request = Intent(ACTION_PERMISSION).setPackage(appContext.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(appContext, 0, request, flags)
            manager.requestPermission(accessory, permissionIntent)
            onStateChanged(false, "Solicitando permiso para el accesorio USB")
        }
    }

    private fun open(accessory: UsbAccessory) {
        close()
        descriptor = manager.openAccessory(accessory)
        if (descriptor == null) {
            onStateChanged(false, "Android no pudo abrir el canal AOA")
        } else {
            onStateChanged(true, "USB AOA conectado; canal de datos disponible")
        }
    }

    fun fileDescriptor(): ParcelFileDescriptor? = descriptor

    fun close() {
        try { descriptor?.close() } catch (_: Exception) { }
        descriptor = null
    }

    @Suppress("DEPRECATION")
    private fun accessoryExtra(intent: Intent): UsbAccessory? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        }
}

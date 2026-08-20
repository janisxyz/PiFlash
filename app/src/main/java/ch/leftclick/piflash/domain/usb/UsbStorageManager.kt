package ch.leftclick.piflash.domain.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import ch.leftclick.piflash.domain.model.UsbStorageDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UsbStorageManager(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "ch.leftclick.piflash.USB_PERMISSION"
        private const val USB_CLASS_MASS_STORAGE = UsbConstants.USB_CLASS_MASS_STORAGE
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun observeDevices(): Flow<List<UsbStorageDevice>> = callbackFlow {
        fun emitNow() {
            trySend(listDevices())
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                emitNow()
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        emitNow()
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun listDevices(): List<UsbStorageDevice> {
        return usbManager.deviceList.values
            .filter { isMassStorage(it) }
            .map { device ->
                UsbStorageDevice(
                    device = device,
                    name = device.productName?.takeIf { it.isNotBlank() }
                        ?: device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    hasPermission = usbManager.hasPermission(device)
                )
            }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
            putExtra(UsbManager.EXTRA_DEVICE, device)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            intent,
            flags
        )
        usbManager.requestPermission(device, pi)
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)
        ?: throw IllegalStateException(
            "Could not open USB device. Grant permission first." +
                if (UsbOem.quirkyUsbStack) "\n\n${UsbOem.otgHint}" else ""
        )

    private fun isMassStorage(device: UsbDevice): Boolean {
        if (device.deviceClass == USB_CLASS_MASS_STORAGE) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == USB_CLASS_MASS_STORAGE) return true
        }
        return false
    }
}

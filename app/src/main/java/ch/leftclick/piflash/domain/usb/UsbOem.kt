package ch.leftclick.piflash.domain.usb

import android.os.Build

object UsbOem {
    val manufacturer: String
        get() = Build.MANUFACTURER.orEmpty()

    val quirkyUsbStack: Boolean
        get() {
            val m = manufacturer.lowercase()
            val b = Build.BRAND.orEmpty().lowercase()
            val display = Build.DISPLAY.orEmpty().lowercase()
            return m.contains("xiaomi") || b.contains("xiaomi") ||
                m.contains("redmi") || b.contains("redmi") ||
                m.contains("poco") || b.contains("poco") ||
                display.contains("hyperos") ||
                Build.VERSION.SDK_INT >= 36
        }

    val otgHint: String =
        "HyperOS/Android 16: enable Settings → Additional settings → OTG before plugging the reader. " +
            "If OTG is greyed out, unplug everything first, turn OTG on, then plug the reader."
}

package ch.leftclick.piflash.domain.usb

import android.os.Build

object UsbOem {
    val manufacturer: String
        get() = Build.MANUFACTURER.orEmpty()

    /** Xiaomi / HyperOS / MIUI USB host stack. Stock Android 12–16 stays on the AOSP path. */
    val quirkyUsbStack: Boolean
        get() {
            val hay = listOf(
                manufacturer,
                Build.BRAND.orEmpty(),
                Build.DISPLAY.orEmpty(),
                Build.FINGERPRINT.orEmpty()
            ).joinToString(" ").lowercase()
            return hay.contains("xiaomi") ||
                hay.contains("redmi") ||
                hay.contains("poco") ||
                hay.contains("hyperos") ||
                hay.contains("miui") ||
                hay.contains("blackshark")
        }

    val otgHint: String =
        "HyperOS: enable Settings → Additional settings → OTG before plugging the reader. " +
            "If OTG is greyed out, unplug everything first, turn OTG on, then plug the reader. " +
            "Keep PiFlash on screen while flashing."
}

package ch.leftclick.piflash.domain.model

import android.hardware.usb.UsbDevice
import android.net.Uri
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SelectedImage(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val compression: ImageCompression
)

data class UsbStorageDevice(
    val device: UsbDevice,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
    val capacityBytes: Long? = null
)

data class PiConfiguration(
    val hostname: String = "raspberrypi",
    val username: String = "pi",
    val password: String = "",
    val enableSsh: Boolean = true,
    val sshAuthMode: SshAuthMode = SshAuthMode.PASSWORD,
    val sshPublicKey: String = "",
    val enableWifi: Boolean = true,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiHidden: Boolean = false,
    val wifiSecurity: WifiSecurity = WifiSecurity.WPA2,
    val country: String = "CH",
    val timezone: String = "Europe/Zurich",
    val locale: String = "en_GB.UTF-8",
    val keyboardLayout: String = "ch",
    /** Run apt-get update && apt-get -y upgrade after network is up on first boot. */
    val aptUpdateUpgrade: Boolean = false,
    /** Install Coolify (requires 64-bit OS + internet). Runs after network is up. */
    val installCoolify: Boolean = false
)

/** A named headless-setup snapshot. Built-in templates have [builtIn] = true and are not persisted. */
data class ConfigPreset(
    val id: String,
    val name: String,
    val config: PiConfiguration,
    val savedAt: Long = 0L,
    val builtIn: Boolean = false
)

object ConfigTemplates {
    val all: List<ConfigPreset> = listOf(
        ConfigPreset(
            id = "template-home",
            name = "Home lab",
            builtIn = true,
            config = PiConfiguration(
                hostname = "raspberrypi",
                username = "pi",
                enableSsh = true,
                enableWifi = true,
                country = "CH",
                timezone = "Europe/Zurich"
            )
        ),
        ConfigPreset(
            id = "template-coolify",
            name = "Coolify host",
            builtIn = true,
            config = PiConfiguration(
                hostname = "coolify",
                username = "coolify",
                enableSsh = true,
                enableWifi = true,
                aptUpdateUpgrade = true,
                installCoolify = true,
                country = "CH",
                timezone = "Europe/Zurich"
            )
        ),
        ConfigPreset(
            id = "template-lan",
            name = "Headless LAN",
            builtIn = true,
            config = PiConfiguration(
                hostname = "raspi",
                username = "pi",
                enableSsh = true,
                enableWifi = false,
                country = "CH",
                timezone = "Europe/Zurich"
            )
        )
    )
}

enum class ImageCompression { NONE, XZ, GZIP, UNKNOWN }

enum class WifiSecurity { OPEN, WPA2, WPA3 }

enum class SshAuthMode { PASSWORD, KEY, BOTH }

enum class FlashPhase {
    IDLE,
    PREPARING,
    WRITING,
    VERIFYING,
    CONFIGURING,
    SYNCING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class FlashError(
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = false
)

data class FlashSummary(
    val bytesWritten: Long,
    val durationMs: Long,
    val deviceName: String,
    val imageName: String,
    val hostname: String
)

data class FlashProgress(
    val phase: FlashPhase,
    val bytesWritten: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Double = 0.0,
    val message: String = "",
    val error: FlashError? = null,
    val summary: FlashSummary? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatSpeed(bytesPerSecond: Double): String {
    if (bytesPerSecond <= 0) return "—"
    return "${formatBytes(bytesPerSecond.toLong())}/s"
}

fun formatEta(remainingBytes: Long, bytesPerSecond: Double): String {
    if (remainingBytes <= 0 || bytesPerSecond <= 0) return "—"
    val seconds = (remainingBytes / bytesPerSecond).toLong().coerceAtLeast(0)
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%dh %02dm", hours, minutes)
    else String.format(Locale.US, "%dm %02ds", minutes, secs)
}

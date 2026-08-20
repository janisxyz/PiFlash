package ch.leftclick.piflash.domain.flash

import android.hardware.usb.UsbDevice
import ch.leftclick.piflash.domain.image.ImageDecompressor
import ch.leftclick.piflash.domain.model.FlashError
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.FlashProgress
import ch.leftclick.piflash.domain.model.FlashSummary
import ch.leftclick.piflash.domain.model.ImageCompression
import ch.leftclick.piflash.domain.model.PiConfiguration
import ch.leftclick.piflash.domain.model.SelectedImage
import ch.leftclick.piflash.domain.pios.FatBootWriter
import ch.leftclick.piflash.domain.pios.PiOsConfigurator
import ch.leftclick.piflash.domain.usb.BlockDeviceWriter
import ch.leftclick.piflash.domain.usb.UsbStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FlashSession(
    private val usb: UsbStorageManager,
    private val decompressor: ImageDecompressor,
    private val flasher: SdCardFlasher = SdCardFlasher(decompressor),
    private val configurator: PiOsConfigurator = PiOsConfigurator()
) {
    fun run(
        image: SelectedImage,
        device: UsbDevice,
        config: PiConfiguration
    ): Flow<FlashProgress> = flow {
        val started = System.currentTimeMillis()
        try {
            emit(FlashProgress(FlashPhase.PREPARING, message = "Opening USB device…"))
            val connection = usb.openDevice(device)
            BlockDeviceWriter.from(device, connection).use { writer ->
                writer.initialize { msg ->
                    emit(FlashProgress(FlashPhase.PREPARING, message = msg))
                }
                val estimated = when {
                    image.uncompressedBytes > 0 -> image.uncompressedBytes
                    image.compression == ImageCompression.NONE -> image.sizeBytes
                    else -> 0L
                }
                emit(
                    FlashProgress(
                        FlashPhase.WRITING,
                        totalBytes = estimated,
                        message = if (estimated > 0) "Starting image write…" else "Starting image write (decompressing…)"
                    )
                )
                flasher.flash(image, writer, estimated).collect { emit(it) }
                emit(FlashProgress(FlashPhase.CONFIGURING, message = "Writing first-boot config"))
                val files = configurator.buildBootFiles(config)
                FatBootWriter(writer).writeFiles(files)
                writer.synchronizeCache()
                val duration = System.currentTimeMillis() - started
                val summary = FlashSummary(
                    bytesWritten = writer.capacityBytes,
                    durationMs = duration,
                    deviceName = device.productName ?: device.deviceName,
                    imageName = image.displayName,
                    hostname = config.hostname
                )
                emit(FlashProgress(FlashPhase.SUCCESS, message = "Done", summary = summary))
            }
        } catch (c: CancellationException) {
            emit(FlashProgress(FlashPhase.CANCELLED, message = "Cancelled"))
            throw c
        } catch (t: Throwable) {
            emit(
                FlashProgress(
                    phase = FlashPhase.FAILED,
                    error = FlashError(t.message ?: t.javaClass.simpleName, t),
                    message = t.message ?: "Flash failed"
                )
            )
        }
    }
}

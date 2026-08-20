package ch.leftclick.piflash.domain.flash

import ch.leftclick.piflash.domain.image.ImageDecompressor
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.FlashProgress
import ch.leftclick.piflash.domain.model.SelectedImage
import ch.leftclick.piflash.domain.model.formatBytes
import ch.leftclick.piflash.domain.usb.BlockDeviceWriter
import ch.leftclick.piflash.domain.usb.UsbOem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream

class SdCardFlasher(
    private val decompressor: ImageDecompressor
) {
    fun flash(
        image: SelectedImage,
        writer: BlockDeviceWriter,
        estimatedSize: Long
    ): Flow<FlashProgress> = flow {
        emit(
            FlashProgress(
                FlashPhase.PREPARING,
                totalBytes = estimatedSize,
                message = "Opening image"
            )
        )
        val block = writer.blockSize
        val blocksPerBuf = if (UsbOem.quirkyUsbStack) 64 else 256
        val buf = ByteArray(block * blocksPerBuf)
        var written = 0L
        val start = System.nanoTime()
        var lastEmitNs = 0L
        var lastBytes = 0L
        var lastSpeed = 0.0
        var lba = 0L

        fun totalNow(): Long =
            if (estimatedSize > 0) maxOf(estimatedSize, written) else 0L

        suspend fun emitWriting(force: Boolean) {
            val now = System.nanoTime()
            if (!force && lastEmitNs != 0L && now - lastEmitNs < 1_000_000_000L) return
            val dt = if (lastEmitNs == 0L) (now - start) / 1e9 else (now - lastEmitNs) / 1e9
            if (dt > 0) lastSpeed = (written - lastBytes) / dt
            lastEmitNs = now
            lastBytes = written
            val total = totalNow()
            emit(
                FlashProgress(
                    phase = FlashPhase.WRITING,
                    bytesWritten = written,
                    totalBytes = total,
                    bytesPerSecond = lastSpeed,
                    message = if (total > 0) {
                        "Writing ${formatBytes(written)} / ${formatBytes(total)} @ LBA $lba"
                    } else {
                        "Writing ${formatBytes(written)} (decompressing) @ LBA $lba"
                    }
                )
            )
        }

        decompressor.openStream(image).use { stream ->
            emitWriting(force = true)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = readFully(stream, buf)
                if (n <= 0) break
                val aligned = if (n % block == 0) n else {
                    val padded = ((n / block) + 1) * block
                    if (padded > buf.size) throw IllegalStateException("Pad overflow")
                    java.util.Arrays.fill(buf, n, padded, 0)
                    padded
                }
                if (writer.capacityBytes > 0 && written + aligned > writer.capacityBytes) {
                    throw IllegalStateException("Image is larger than the SD card")
                }
                writer.writeBlocks(lba, buf, aligned)
                written += aligned
                lba += aligned / block
                emitWriting(force = false)
            }
        }
        emitWriting(force = true)
        emit(
            FlashProgress(
                FlashPhase.SYNCING,
                bytesWritten = written,
                totalBytes = totalNow().coerceAtLeast(written),
                bytesPerSecond = lastSpeed,
                message = "Flushing to card…"
            )
        )
        writer.synchronizeCache()
        val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
        val avg = if (elapsed > 0) written / elapsed else 0.0
        emit(
            FlashProgress(
                phase = FlashPhase.CONFIGURING,
                bytesWritten = written,
                totalBytes = written,
                bytesPerSecond = avg,
                message = "Image written — applying headless config"
            )
        )
    }

    private fun readFully(stream: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = stream.read(buf, off, buf.size - off)
            if (n < 0) return off
            off += n
        }
        return off
    }
}

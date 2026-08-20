package ch.leftclick.piflash.domain.flash

import ch.leftclick.piflash.domain.image.ImageDecompressor
import ch.leftclick.piflash.domain.model.FlashPhase
import ch.leftclick.piflash.domain.model.FlashProgress
import ch.leftclick.piflash.domain.model.SelectedImage
import ch.leftclick.piflash.domain.usb.BlockDeviceWriter
import kotlinx.coroutines.CancellationException
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
        emit(FlashProgress(FlashPhase.PREPARING, totalBytes = estimatedSize, message = "Opening image"))
        val block = writer.blockSize
        val buf = ByteArray(block * 128) // 64 KiB
        var written = 0L
        val start = System.nanoTime()
        var lastTick = start
        var lastBytes = 0L

        decompressor.openStream(image).use { stream ->
            emit(FlashProgress(FlashPhase.WRITING, totalBytes = estimatedSize, message = "Writing image"))
            var lba = 0L
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
                val now = System.nanoTime()
                val dt = (now - lastTick) / 1_000_000_000.0
                val speed = if (dt > 0.25) {
                    val s = (written - lastBytes) / dt
                    lastTick = now
                    lastBytes = written
                    s
                } else 0.0
                emit(
                    FlashProgress(
                        phase = FlashPhase.WRITING,
                        bytesWritten = written,
                        totalBytes = estimatedSize,
                        bytesPerSecond = speed,
                        message = "Writing"
                    )
                )
            }
        }
        emit(FlashProgress(FlashPhase.SYNCING, bytesWritten = written, totalBytes = estimatedSize, message = "Flushing"))
        writer.synchronizeCache()
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val avg = if (elapsed > 0) written / (elapsed / 1000.0) else 0.0
        emit(
            FlashProgress(
                phase = FlashPhase.CONFIGURING,
                bytesWritten = written,
                totalBytes = estimatedSize.coerceAtLeast(written),
                bytesPerSecond = avg,
                message = "Image written"
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

package ch.leftclick.piflash.domain.image

import android.content.Context
import android.os.ParcelFileDescriptor
import ch.leftclick.piflash.domain.model.ImageCompression
import ch.leftclick.piflash.domain.model.SelectedImage
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

class ImageDecompressor(private val context: Context) {

    fun openStream(image: SelectedImage): InputStream {
        val raw = context.contentResolver.openInputStream(image.uri)
            ?: throw IllegalStateException("Cannot open image stream")
        val buffered = BufferedInputStream(raw, 1024 * 256)
        return when (image.compression) {
            ImageCompression.XZ -> XZInputStream(buffered)
            ImageCompression.GZIP -> GZIPInputStream(buffered)
            ImageCompression.NONE, ImageCompression.UNKNOWN -> buffered
        }
    }

    fun estimateUncompressedSize(image: SelectedImage): Long {
        return when (image.compression) {
            ImageCompression.NONE -> image.sizeBytes
            ImageCompression.GZIP -> peekGzipIsize(image)
            ImageCompression.XZ -> peekXzUncompressed(image)
            ImageCompression.UNKNOWN -> -1L
        }
    }

    private fun peekTail(image: SelectedImage, maxBytes: Int): ByteArray? {
        val pfd = context.contentResolver.openFileDescriptor(image.uri, "r") ?: return null
        pfd.use {
            val ch = FileInputStream(it.fileDescriptor).channel
            val size = ch.size().takeIf { s -> s > 0 } ?: image.sizeBytes
            if (size < 16) return null
            val n = minOf(size, maxBytes.toLong()).toInt()
            val buf = ByteBuffer.allocate(n)
            ch.position(size - n)
            while (buf.hasRemaining()) {
                if (ch.read(buf) < 0) break
            }
            buf.flip()
            val out = ByteArray(buf.remaining())
            buf.get(out)
            return out
        }
    }

    private fun peekGzipIsize(image: SelectedImage): Long {
        val tail = peekTail(image, 8) ?: return -1L
        if (tail.size < 4) return -1L
        val isize = ByteBuffer.wrap(tail, tail.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        return if (isize > 0) isize else -1L
    }

    private fun peekXzUncompressed(image: SelectedImage): Long {
        val tail = peekTail(image, 256 * 1024) ?: return -1L
        if (tail.size < 32) return -1L
        val end = tail.size
        if (tail[end - 2] != 0x59.toByte() || tail[end - 1] != 0x5A.toByte()) return -1L
        val backwardSize = u32le(tail, end - 8)
        val indexSize = (backwardSize + 1L) * 4L
        if (indexSize <= 0 || indexSize > end - 12L) return -1L
        val indexStart = end - 12 - indexSize.toInt()
        if (indexStart < 0 || tail[indexStart] != 0.toByte()) return -1L
        var p = indexStart + 1
        val nrec = readVli(tail, p) ?: return -1L
        p = nrec.second
        if (nrec.first <= 0 || nrec.first > 64) return -1L
        var uncompressed = 0L
        repeat(nrec.first.toInt()) {
            val unpadded = readVli(tail, p) ?: return -1L
            val uncomp = readVli(tail, unpadded.second) ?: return -1L
            uncompressed += uncomp.first
            p = uncomp.second
        }
        return if (uncompressed > 0) uncompressed else -1L
    }

    private fun u32le(buf: ByteArray, off: Int): Long {
        if (off < 0 || off + 4 > buf.size) return -1L
        return ByteBuffer.wrap(buf, off, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun readVli(buf: ByteArray, off: Int): Pair<Long, Int>? {
        var value = 0L
        var i = 0
        while (i < 9) {
            val at = off + i
            if (at >= buf.size) return null
            val b = buf[at].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl (i * 7))
            i++
            if (b and 0x80 == 0) return value to (off + i)
        }
        return null
    }
}

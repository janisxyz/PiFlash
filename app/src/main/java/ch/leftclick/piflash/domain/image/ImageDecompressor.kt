package ch.leftclick.piflash.domain.image

import android.content.Context
import ch.leftclick.piflash.domain.model.ImageCompression
import ch.leftclick.piflash.domain.model.SelectedImage
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.InputStream
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
            else -> -1L
        }
    }
}

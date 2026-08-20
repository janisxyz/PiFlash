package ch.leftclick.piflash.domain.image

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ch.leftclick.piflash.domain.model.ImageCompression
import ch.leftclick.piflash.domain.model.SelectedImage

class ImageAnalyzer(private val context: Context) {

    private val decompressor = ImageDecompressor(context)

    fun analyze(uri: Uri): SelectedImage {
        val name = queryName(uri) ?: uri.lastPathSegment ?: "image.img"
        val size = querySize(uri)
        val lower = name.lowercase()
        val compression = when {
            lower.endsWith(".img.xz") || lower.endsWith(".xz") -> ImageCompression.XZ
            lower.endsWith(".img.gz") || lower.endsWith(".gz") -> ImageCompression.GZIP
            lower.endsWith(".img") || lower.endsWith(".iso") -> ImageCompression.NONE
            else -> ImageCompression.UNKNOWN
        }
        if (compression == ImageCompression.UNKNOWN) {
            throw IllegalArgumentException("Unsupported image: $name. Use .img, .img.xz or .img.gz")
        }
        val img = SelectedImage(uri, name, size, compression)
        val uncompressed = runCatching { decompressor.estimateUncompressedSize(img) }.getOrDefault(-1L)
        return img.copy(uncompressedBytes = uncompressed)
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return null
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0) return c.getLong(i)
            }
        }
        return -1L
    }
}

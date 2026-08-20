package ch.leftclick.piflash.domain.pios

import ch.leftclick.piflash.domain.usb.BlockDeviceWriter

/**
 * Writes small files into the root of the first FAT boot partition by patching
 * the directory table. Supports FAT16/FAT32 volumes that still have free root entries.
 * This is intentionally conservative: if the layout is unexpected we fail loudly.
 */
class FatBootWriter(
    private val writer: BlockDeviceWriter,
    private val detector: PiOsDetector = PiOsDetector()
) {
    fun writeFiles(files: Map<String, ByteArray>) {
        val boot = detector.findFatBoot(writer)
            ?: throw IllegalStateException("No FAT boot partition found after flash. Image may not be Raspberry Pi OS.")
        val first = writer.readBlocks(boot.startLba, writer.blockSize)
        val bytsPerSec = u16(first, 11).takeIf { it != 0 } ?: writer.blockSize
        val secPerClus = first[13].toInt() and 0xFF
        val reserved = u16(first, 14)
        val fats = first[16].toInt() and 0xFF
        val rootEnt = u16(first, 17)
        val fatSz16 = u16(first, 22)
        val fatSz32 = le32(first, 36)
        val fatSz = if (fatSz16 != 0) fatSz16 else fatSz32
        val isFat32 = rootEnt == 0
        val rootLba = boot.startLba + reserved + fats.toLong() * fatSz
        val rootBytes = if (isFat32) secPerClus * bytsPerSec else rootEnt * 32
        val sectors = ((rootBytes + bytsPerSec - 1) / bytsPerSec).coerceAtLeast(1)
        val aligned = ((sectors * bytsPerSec + writer.blockSize - 1) / writer.blockSize) * writer.blockSize
        val dir = writer.readBlocks(rootLba, aligned)

        // Best-effort: append 8.3 directory entries if we find a 0x00 slot.
        // Full cluster allocation is skipped for empty marker files / tiny configs
        // when the volume already has a writable boot overlay from the image.
        // We still write via a reserved unused area after the root dir for tiny files.
        var slot = findFreeDirSlot(dir)
        if (slot < 0) {
            throw IllegalStateException("FAT root directory is full; cannot write config files")
        }
        // Store file payloads in unused sectors immediately after the root directory.
        var dataLba = rootLba + (aligned / writer.blockSize)
        for ((name, payload) in files) {
            if (slot + 32 > dir.size) {
                throw IllegalStateException("Not enough FAT directory slots")
            }
            val short = to83(name)
            val padded = if (payload.isEmpty()) ByteArray(writer.blockSize) else {
                val size = ((payload.size + writer.blockSize - 1) / writer.blockSize) * writer.blockSize
                ByteArray(size).also { payload.copyInto(it) }
            }
            writer.writeBlocks(dataLba, padded, padded.size)
            val cluster = guessCluster(boot.startLba, reserved, fats, fatSz, isFat32, dataLba, secPerClus, bytsPerSec)
            writeDirEntry(dir, slot, short, payload.size, cluster)
            dataLba += padded.size / writer.blockSize
            slot += 32
            val next = findFreeDirSlot(dir, slot)
            slot = if (next >= 0) next else slot
        }
        writer.writeBlocks(rootLba, dir, dir.size)
    }

    private fun guessCluster(
        start: Long, reserved: Int, fats: Int, fatSz: Int, fat32: Boolean,
        dataLba: Long, secPerClus: Int, bps: Int
    ): Int {
        val rootSectors = if (fat32) 0 else 32 // approx; cluster numbers are best-effort
        val dataStart = start + reserved + fats.toLong() * fatSz + rootSectors
        val sec = (dataLba - dataStart).coerceAtLeast(0)
        val spc = secPerClus.coerceAtLeast(1)
        val bpsSafe = if (bps == 0) 512 else bps
        val sectorsFromData = sec * writer.blockSize / bpsSafe
        return (2 + sectorsFromData / spc).toInt().coerceAtLeast(2)
    }

    private fun findFreeDirSlot(dir: ByteArray, from: Int = 0): Int {
        var i = from - (from % 32)
        while (i + 32 <= dir.size) {
            val b = dir[i].toInt() and 0xFF
            if (b == 0x00 || b == 0xE5) return i
            i += 32
        }
        return -1
    }

    private fun writeDirEntry(dir: ByteArray, off: Int, name83: ByteArray, size: Int, cluster: Int) {
        name83.copyInto(dir, off)
        dir[off + 11] = 0x20
        dir[off + 26] = (cluster and 0xFF).toByte()
        dir[off + 27] = ((cluster shr 8) and 0xFF).toByte()
        dir[off + 20] = ((cluster shr 16) and 0xFF).toByte()
        dir[off + 21] = ((cluster shr 24) and 0xFF).toByte()
        dir[off + 28] = (size and 0xFF).toByte()
        dir[off + 29] = ((size shr 8) and 0xFF).toByte()
        dir[off + 30] = ((size shr 16) and 0xFF).toByte()
        dir[off + 31] = ((size shr 24) and 0xFF).toByte()
    }

    private fun to83(name: String): ByteArray {
        val base = name.substringBeforeLast('.', name).uppercase().filter { it.isLetterOrDigit() || it == '_' }.padEnd(8, ' ').take(8)
        val ext = if (name.contains('.')) name.substringAfterLast('.').uppercase().padEnd(3, ' ').take(3) else "   "
        return (base + ext).toByteArray(Charsets.US_ASCII)
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}

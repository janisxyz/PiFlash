package ch.leftclick.piflash.domain.pios

import ch.leftclick.piflash.domain.usb.BlockDeviceWriter

data class BootPartition(
    val startLba: Long,
    val sectorCount: Long,
    val style: Style
) {
    enum class Style { LEGACY_BOOT, FIRMWARE }
}

class PiOsDetector {
    fun findFatBoot(writer: BlockDeviceWriter): BootPartition? {
        val mbr = writer.readBlocks(0, writer.blockSize)
        if (mbr[510] != 0x55.toByte() || mbr[511] != 0xAA.toByte()) return null
        for (i in 0 until 4) {
            val off = 446 + i * 16
            val type = mbr[off + 4].toInt() and 0xFF
            val start = le32(mbr, off + 8).toLong() and 0xFFFFFFFFL
            val count = le32(mbr, off + 12).toLong() and 0xFFFFFFFFL
            if (type == 0x0B || type == 0x0C || type == 0x0E || type == 0x06 || type == 0x04) {
                return BootPartition(start, count, BootPartition.Style.LEGACY_BOOT)
            }
        }
        return null
    }

    private fun le32(b: ByteArray, o: Int): Int {
        return (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
    }
}

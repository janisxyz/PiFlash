package ch.leftclick.piflash.domain.usb

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScsiException(
    message: String,
    val senseKey: Int = -1,
    val asc: Int = -1,
    val ascq: Int = -1,
    val retryable: Boolean = false
) : IllegalStateException(message)

data class Csw(
    val signature: Int,
    val tag: Int,
    val residue: Int,
    val status: Int
)

data class Sense(
    val responseCode: Int,
    val senseKey: Int,
    val asc: Int,
    val ascq: Int,
    val raw: ByteArray
) {
    val message: String get() = Scsi.senseMessage(senseKey, asc, ascq)
    val retryable: Boolean get() = Scsi.isRetryable(senseKey, asc, ascq)
    val notReady: Boolean get() = senseKey == Scsi.SENSE_NOT_READY
    val unitAttention: Boolean get() = senseKey == Scsi.SENSE_UNIT_ATTENTION
    val mediumNotPresent: Boolean get() = senseKey == Scsi.SENSE_NOT_READY && asc == 0x3A
}

object Scsi {
    const val CBW_SIGNATURE = 0x43425355
    const val CSW_SIGNATURE = 0x53425355
    const val CBW_SIZE = 31
    const val CSW_SIZE = 13

    const val STATUS_PASSED = 0
    const val STATUS_FAILED = 1
    const val STATUS_PHASE_ERROR = 2

    const val SENSE_NO_SENSE = 0x00
    const val SENSE_RECOVERED = 0x01
    const val SENSE_NOT_READY = 0x02
    const val SENSE_MEDIUM_ERROR = 0x03
    const val SENSE_HARDWARE = 0x04
    const val SENSE_ILLEGAL_REQUEST = 0x05
    const val SENSE_UNIT_ATTENTION = 0x06
    const val SENSE_DATA_PROTECT = 0x07
    const val SENSE_ABORTED = 0x0B

    const val USB_CLASS_MASS_STORAGE = 0x08
    const val SUBCLASS_SCSI = 0x06
    const val PROTOCOL_BOT = 0x50
    const val PROTOCOL_UAS = 0x62

    const val REQ_MASS_STORAGE_RESET = 0xFF
    const val REQ_GET_MAX_LUN = 0xFE

    fun buildCbw(
        tag: Int,
        dataLength: Int,
        directionOut: Boolean,
        lun: Int,
        cdb: ByteArray,
        cdbLength: Int = inferCdbLength(cdb)
    ): ByteArray {
        val buf = ByteBuffer.allocate(CBW_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(CBW_SIGNATURE)
        buf.putInt(tag)
        buf.putInt(dataLength)
        buf.put(if (directionOut) 0x00.toByte() else 0x80.toByte())
        buf.put((lun and 0x0F).toByte())
        buf.put((cdbLength and 0x1F).toByte())
        val padded = ByteArray(16)
        System.arraycopy(cdb, 0, padded, 0, minOf(cdb.size, 16))
        buf.put(padded)
        return buf.array()
    }

    fun inferCdbLength(cdb: ByteArray): Int {
        if (cdb.isEmpty()) return 6
        return when (cdb[0].toInt() and 0xFF) {
            0x00, 0x03, 0x12, 0x1A, 0x1B -> 6
            0x25, 0x28, 0x2A, 0x35 -> 10
            0x9E, 0x88, 0x8A, 0x91 -> 16
            else -> when ((cdb[0].toInt() and 0xE0) shr 5) {
                0 -> 6
                1, 2 -> 10
                4 -> 16
                else -> 10
            }
        }
    }

    fun inquiry(): ByteArray = byteArrayOf(0x12, 0, 0, 0, 36, 0)
    fun testUnitReady(): ByteArray = byteArrayOf(0x00, 0, 0, 0, 0, 0)
    fun requestSense(): ByteArray = byteArrayOf(0x03, 0, 0, 0, 18, 0)
    fun startStopUnit(start: Boolean): ByteArray =
        byteArrayOf(0x1B, 0, 0, 0, if (start) 0x01 else 0x00, 0)

    fun readCapacity10(): ByteArray = byteArrayOf(0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    fun readCapacity16(): ByteArray {
        val cdb = ByteArray(16)
        cdb[0] = 0x9E.toByte()
        cdb[1] = 0x10
        cdb[13] = 32
        return cdb
    }

    fun modeSense6(allocation: Int = 64): ByteArray =
        byteArrayOf(0x1A, 0, 0x3F, 0, allocation.toByte(), 0)

    fun read10(lba: Long, blocks: Int): ByteArray {
        val cdb = ByteArray(10)
        cdb[0] = 0x28
        putBe32(cdb, 2, lba)
        cdb[7] = ((blocks ushr 8) and 0xFF).toByte()
        cdb[8] = (blocks and 0xFF).toByte()
        return cdb
    }

    fun write10(lba: Long, blocks: Int): ByteArray {
        val cdb = ByteArray(10)
        cdb[0] = 0x2A
        putBe32(cdb, 2, lba)
        cdb[7] = ((blocks ushr 8) and 0xFF).toByte()
        cdb[8] = (blocks and 0xFF).toByte()
        return cdb
    }

    fun synchronizeCache10(): ByteArray {
        val cdb = ByteArray(10)
        cdb[0] = 0x35
        return cdb
    }

    fun parseCsw(raw: ByteArray): Csw {
        require(raw.size >= CSW_SIZE) { "CSW too short" }
        val buf = ByteBuffer.wrap(raw, 0, CSW_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        return Csw(
            signature = buf.int,
            tag = buf.int,
            residue = buf.int,
            status = raw[12].toInt() and 0xFF
        )
    }

    fun isCsw(raw: ByteArray, offset: Int = 0): Boolean {
        if (raw.size < offset + 4) return false
        val sig =
            (raw[offset].toInt() and 0xFF) or
                ((raw[offset + 1].toInt() and 0xFF) shl 8) or
                ((raw[offset + 2].toInt() and 0xFF) shl 16) or
                ((raw[offset + 3].toInt() and 0xFF) shl 24)
        return sig == CSW_SIGNATURE
    }

    fun parseSense(raw: ByteArray): Sense {
        val key = if (raw.size > 2) raw[2].toInt() and 0x0F else 0
        val asc = if (raw.size > 12) raw[12].toInt() and 0xFF else 0
        val ascq = if (raw.size > 13) raw[13].toInt() and 0xFF else 0
        val code = if (raw.isNotEmpty()) raw[0].toInt() and 0x7F else 0
        return Sense(code, key, asc, ascq, raw)
    }

    fun parseCapacity10(buf: ByteArray): Pair<Long, Int> {
        val lastLba = be32(buf, 0)
        val block = be32(buf, 4).toInt()
        return lastLba to block
    }

    fun parseCapacity16(buf: ByteArray): Pair<Long, Int> {
        val lastLba = be64(buf, 0)
        val block = be32(buf, 8).toInt()
        return lastLba to block
    }

    fun isWriteProtected(modeSense6: ByteArray): Boolean {
        if (modeSense6.size < 3) return false
        return (modeSense6[2].toInt() and 0x80) != 0
    }

    fun isRetryable(senseKey: Int, asc: Int, ascq: Int): Boolean {
        if (senseKey < 0) return true
        if (senseKey == SENSE_NOT_READY && asc == 0x3A) return false
        return when (senseKey) {
            SENSE_NO_SENSE, SENSE_UNIT_ATTENTION, SENSE_NOT_READY, SENSE_ABORTED, SENSE_RECOVERED -> true
            else -> false
        }
    }

    fun senseMessage(senseKey: Int, asc: Int, ascq: Int): String {
        if (senseKey == SENSE_NOT_READY && asc == 0x3A) {
            return "No memory card in the reader. Insert an SD card and retry."
        }
        if (senseKey == SENSE_DATA_PROTECT) {
            return "The SD card is write-protected. Slide the lock off and retry."
        }
        if (senseKey == SENSE_NOT_READY) {
            return "The reader is not ready (sense ${hex(senseKey)}/${hex(asc)}/${hex(ascq)}). Unplug, reinsert the card, and retry."
        }
        if (senseKey == SENSE_UNIT_ATTENTION) {
            return "The reader reported a media change. Retry the flash."
        }
        if (senseKey == SENSE_ILLEGAL_REQUEST) {
            return "The reader rejected a SCSI command (sense ${hex(senseKey)}/${hex(asc)}/${hex(ascq)})."
        }
        if (senseKey == SENSE_MEDIUM_ERROR) {
            return "The SD card reported a media error. Try another card."
        }
        if (senseKey == SENSE_HARDWARE) {
            return "The USB reader reported a hardware error."
        }
        if (senseKey < 0) {
            return "The USB reader was not ready (no SCSI sense). Unplug, reinsert the card, and retry."
        }
        return "SCSI command failed (sense ${hex(senseKey)}/${hex(asc)}/${hex(ascq)})"
    }

    private fun putBe32(cdb: ByteArray, off: Int, value: Long) {
        cdb[off] = ((value ushr 24) and 0xFF).toByte()
        cdb[off + 1] = ((value ushr 16) and 0xFF).toByte()
        cdb[off + 2] = ((value ushr 8) and 0xFF).toByte()
        cdb[off + 3] = (value and 0xFF).toByte()
    }

    private fun be32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or
            ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or
            (b[o + 3].toLong() and 0xFF)

    private fun be64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        }
        return v
    }

    private fun hex(v: Int) = String.format("%02x", v and 0xFF)
}

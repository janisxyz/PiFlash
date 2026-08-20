package ch.leftclick.piflash.domain.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB Mass Storage Bulk-Only Transport + SCSI block writer.
 * Writes raw 512-byte sectors. Caller must keep writes block-aligned.
 */
class BlockDeviceWriter(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint
) : AutoCloseable {

    val blockSize: Int = 512
    var capacityBytes: Long = 0L
        private set

    private var tag: Int = 1
    private val timeoutMs = 15_000

    fun initialize() {
        if (!connection.claimInterface(usbInterface, true)) {
            throw IllegalStateException("Failed to claim USB mass-storage interface")
        }
        capacityBytes = readCapacity()
    }

    fun writeBlocks(lba: Long, data: ByteArray, length: Int) {
        require(length % blockSize == 0) { "Write length must be a multiple of $blockSize" }
        val blocks = length / blockSize
        val cdb = ByteArray(16)
        cdb[0] = 0x2A.toByte() // WRITE(10)
        cdb[2] = ((lba ushr 24) and 0xFF).toByte()
        cdb[3] = ((lba ushr 16) and 0xFF).toByte()
        cdb[4] = ((lba ushr 8) and 0xFF).toByte()
        cdb[5] = (lba and 0xFF).toByte()
        cdb[7] = ((blocks ushr 8) and 0xFF).toByte()
        cdb[8] = (blocks and 0xFF).toByte()
        sendCbw(length, directionOut = true, cdb = cdb)
        var sent = 0
        while (sent < length) {
            val chunk = minOf(length - sent, bulkOut.maxPacketSize * 64)
            val n = connection.bulkTransfer(bulkOut, data, sent, chunk, timeoutMs)
            if (n <= 0) throw IllegalStateException("USB write failed at offset $sent (n=$n)")
            sent += n
        }
        readCsw()
    }

    fun readBlocks(lba: Long, length: Int): ByteArray {
        require(length % blockSize == 0)
        val blocks = length / blockSize
        val cdb = ByteArray(16)
        cdb[0] = 0x28.toByte() // READ(10)
        cdb[2] = ((lba ushr 24) and 0xFF).toByte()
        cdb[3] = ((lba ushr 16) and 0xFF).toByte()
        cdb[4] = ((lba ushr 8) and 0xFF).toByte()
        cdb[5] = (lba and 0xFF).toByte()
        cdb[7] = ((blocks ushr 8) and 0xFF).toByte()
        cdb[8] = (blocks and 0xFF).toByte()
        sendCbw(length, directionOut = false, cdb = cdb)
        val out = ByteArray(length)
        var rec = 0
        while (rec < length) {
            val n = connection.bulkTransfer(bulkIn, out, rec, length - rec, timeoutMs)
            if (n <= 0) throw IllegalStateException("USB read failed (n=$n)")
            rec += n
        }
        readCsw()
        return out
    }

    fun synchronizeCache() {
        val cdb = ByteArray(16)
        cdb[0] = 0x35.toByte() // SYNCHRONIZE CACHE(10)
        sendCbw(0, directionOut = true, cdb = cdb)
        readCsw()
    }

    private fun readCapacity(): Long {
        val cdb = ByteArray(16)
        cdb[0] = 0x25.toByte() // READ CAPACITY(10)
        sendCbw(8, directionOut = false, cdb = cdb)
        val buf = ByteArray(8)
        val n = connection.bulkTransfer(bulkIn, buf, 8, timeoutMs)
        if (n < 8) throw IllegalStateException("READ CAPACITY failed")
        readCsw()
        val lastLba = ByteBuffer.wrap(buf, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val size = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        return (lastLba + 1) * size
    }

    private fun sendCbw(dataLength: Int, directionOut: Boolean, cdb: ByteArray) {
        val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
        cbw.putInt(0x43425355) // USBC
        cbw.putInt(tag++)
        cbw.putInt(dataLength)
        cbw.put(if (directionOut) 0x00 else 0x80)
        cbw.put(0) // LUN
        cbw.put(10) // CB length
        cbw.put(cdb.copyOf(16))
        val packet = cbw.array()
        val n = connection.bulkTransfer(bulkOut, packet, 31, timeoutMs)
        if (n != 31) throw IllegalStateException("CBW transfer failed (n=$n)")
    }

    private fun readCsw() {
        val csw = ByteArray(13)
        val n = connection.bulkTransfer(bulkIn, csw, 13, timeoutMs)
        if (n < 13) throw IllegalStateException("CSW transfer failed (n=$n)")
        val status = csw[12].toInt() and 0xFF
        if (status != 0) throw IllegalStateException("SCSI command failed, CSW status=$status")
    }

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    companion object {
        fun from(device: UsbDevice, connection: UsbDeviceConnection): BlockDeviceWriter {
            var foundIface: UsbInterface? = null
            var inn: UsbEndpoint? = null
            var out: UsbEndpoint? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_MASS_STORAGE) continue
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp != null && outEp != null) {
                    foundIface = iface
                    inn = inEp
                    out = outEp
                    break
                }
            }
            val iface = foundIface ?: throw IllegalStateException("No USB mass-storage bulk endpoints")
            return BlockDeviceWriter(connection, iface, inn!!, out!!)
        }
    }
}

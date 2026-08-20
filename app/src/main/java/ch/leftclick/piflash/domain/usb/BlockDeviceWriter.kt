package ch.leftclick.piflash.domain.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbRequest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BlockDeviceWriter(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint,
    private val lun: Int = 0
) : AutoCloseable {

    var blockSize: Int = 512
        private set
    var capacityBytes: Long = 0L
        private set

    private var activeLun: Int = lun
    private var tag: Int = 1
    private val ioTimeoutMs = 5_000
    private val initTimeoutMs = 2_000
    private val outRequest = UsbRequest()
    private val inRequest = UsbRequest()
    private val xferLock = Any()
    private var requestsReady = false

    suspend fun initialize(onStatus: suspend (String) -> Unit = {}) {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        fun timedOut() = SystemClock.elapsedRealtime() > deadline

        onStatus("Claiming USB interface…")
        if (!connection.claimInterface(usbInterface, true)) {
            throw ScsiException("Failed to claim USB mass-storage interface")
        }
        if (!UsbOem.quirkyUsbStack && Build.VERSION.SDK_INT >= 21) {
            runCatching { connection.setInterface(usbInterface) }
        }
        if (UsbOem.quirkyUsbStack) delay(200)
        val maxLun = runCatching { getMaxLun() }.getOrDefault(0)
        var last: ScsiException? = null
        var ready = false
        for (tryLun in 0..maxLun) {
            if (timedOut()) break
            try {
                onStatus("Talking to reader (LUN $tryLun)…")
                runCatching { inquiry(tryLun) }.onFailure {
                    Log.w(TAG, "INQUIRY LUN $tryLun failed: ${it.message}")
                }
                waitUntilReady(tryLun, onStatus, deadline)
                activeLun = tryLun
                ready = true
                break
            } catch (e: ScsiException) {
                last = e
                if (e.asc == 0x3A && maxLun == 0) throw e
                onStatus("LUN $tryLun not ready — resetting…")
                resetRecovery()
            }
        }
        if (!ready) {
            throw last ?: ScsiException(
                "The USB reader did not become ready in time. Unplug it, reinsert the SD card, and retry." +
                    if (UsbOem.quirkyUsbStack) "\n\n${UsbOem.otgHint}" else "",
                retryable = true
            )
        }
        onStatus("Reading card size…")
        val (lastLba, size) = readCapacity(activeLun)
        blockSize = size.coerceAtLeast(512)
        capacityBytes = (lastLba + 1L) * blockSize
        if (isWriteProtected(activeLun)) {
            throw ScsiException("The SD card is write-protected. Slide the lock off and retry.")
        }
        onStatus("Card ready · ${capacityBytes / (1024 * 1024)} MB")
    }

    fun writeBlocks(lba: Long, data: ByteArray, length: Int) {
        require(length % blockSize == 0) { "Write length must be a multiple of $blockSize" }
        require(length <= data.size)
        var offset = 0
        var at = lba
        var chunkBlocks = preferredWriteBlocks(length / blockSize)
        while (offset < length) {
            val blocks = minOf(chunkBlocks, (length - offset) / blockSize)
            val bytes = blocks * blockSize
            try {
                val slice = if (offset == 0 && bytes == length) data else data.copyOfRange(offset, offset + bytes)
                scsiWrite(at, slice, bytes)
                offset += bytes
                at += blocks
            } catch (e: ScsiException) {
                if (e.retryable && chunkBlocks > 1) {
                    chunkBlocks = (chunkBlocks / 4).coerceAtLeast(1)
                    continue
                }
                throw e
            }
        }
    }

    fun readBlocks(lba: Long, length: Int): ByteArray {
        require(length % blockSize == 0)
        val out = ByteArray(length)
        execute(Scsi.read10(lba, length / blockSize), length, false, inBuf = out)
        return out
    }

    fun synchronizeCache() {
        try {
            execute(Scsi.synchronizeCache10(), 0, true, ignoreUnsupported = true)
        } catch (e: ScsiException) {
            if (e.senseKey == Scsi.SENSE_ILLEGAL_REQUEST) return
            Log.w(TAG, "SYNCHRONIZE CACHE failed: ${e.message}")
        }
    }

    private fun scsiWrite(lba: Long, data: ByteArray, length: Int) {
        execute(Scsi.write10(lba, length / blockSize), length, true, outBuf = data)
    }

    private fun preferredWriteBlocks(total: Int): Int {
        val maxByPacket = ((bulkOut.maxPacketSize * 32) / blockSize).coerceAtLeast(1)
        val cap = if (UsbOem.quirkyUsbStack) 32 else 256
        return minOf(total, maxByPacket, cap)
    }

    private fun inquiry(useLun: Int) {
        execute(Scsi.inquiry(), 36, false, inBuf = ByteArray(36), lun = useLun, retries = 2, timeoutMs = initTimeoutMs)
    }

    private suspend fun waitUntilReady(
        useLun: Int,
        onStatus: suspend (String) -> Unit,
        deadline: Long
    ) {
        var last: ScsiException? = null
        repeat(12) { attempt ->
            if (SystemClock.elapsedRealtime() > deadline) {
                throw last ?: ScsiException("Timed out waiting for the SD card", retryable = true)
            }
            onStatus("Waiting for SD card… (${attempt + 1}/12)")
            try {
                execute(
                    Scsi.testUnitReady(),
                    0,
                    true,
                    lun = useLun,
                    retries = 0,
                    timeoutMs = initTimeoutMs
                )
                return
            } catch (e: ScsiException) {
                last = e
                if (e.asc == 0x3A) {
                    throw ScsiException(Scsi.senseMessage(e.senseKey, e.asc, e.ascq), e.senseKey, e.asc, e.ascq)
                }
                if (attempt == 4) {
                    runCatching {
                        execute(
                            Scsi.startStopUnit(true),
                            0,
                            true,
                            lun = useLun,
                            ignoreUnsupported = true,
                            timeoutMs = initTimeoutMs
                        )
                    }
                }
                delay(120L * (attempt + 1).coerceAtMost(4))
            }
        }
        val e = last
        throw ScsiException(
            e?.message ?: "Reader never became ready",
            e?.senseKey ?: -1,
            e?.asc ?: -1,
            e?.ascq ?: -1,
            retryable = true
        )
    }

    private fun readCapacity(useLun: Int): Pair<Long, Int> {
        val buf10 = ByteArray(8)
        try {
            execute(Scsi.readCapacity10(), 8, false, inBuf = buf10, lun = useLun, retries = 3, timeoutMs = initTimeoutMs)
            val (last, size) = Scsi.parseCapacity10(buf10)
            if (last != 0xFFFFFFFFL && size > 0) return last to size
        } catch (e: ScsiException) {
            Log.w(TAG, "READ CAPACITY(10) failed: ${e.message}")
        }
        val buf16 = ByteArray(32)
        execute(Scsi.readCapacity16(), 32, false, inBuf = buf16, lun = useLun, retries = 2, timeoutMs = initTimeoutMs)
        val (last, size) = Scsi.parseCapacity16(buf16)
        if (size <= 0) throw ScsiException("READ CAPACITY returned block size 0")
        return last to size
    }

    private fun isWriteProtected(useLun: Int): Boolean {
        val buf = ByteArray(64)
        return try {
            execute(Scsi.modeSense6(), buf.size, false, inBuf = buf, lun = useLun, retries = 1, timeoutMs = initTimeoutMs)
            Scsi.isWriteProtected(buf)
        } catch (_: Exception) {
            false
        }
    }

    private fun execute(
        cdb: ByteArray,
        dataLength: Int,
        directionOut: Boolean,
        outBuf: ByteArray? = null,
        inBuf: ByteArray? = null,
        lun: Int = this.activeLun,
        retries: Int = 2,
        ignoreUnsupported: Boolean = false,
        timeoutMs: Int = ioTimeoutMs
    ) {
        var lastError: ScsiException? = null
        val attempts = retries + 1
        repeat(attempts) { attempt ->
            try {
                val expectedTag = sendCbw(dataLength, directionOut, cdb, lun, timeoutMs)
                var earlyCsw: Csw? = null
                if (dataLength > 0) {
                    if (directionOut) bulkOutAll(outBuf ?: ByteArray(dataLength), dataLength, timeoutMs)
                    else earlyCsw = bulkInAll(inBuf ?: ByteArray(dataLength), dataLength, timeoutMs)
                }
                val csw = earlyCsw ?: readCsw(expectedTag, timeoutMs)
                when (csw.status) {
                    Scsi.STATUS_PASSED -> return
                    Scsi.STATUS_FAILED, Scsi.STATUS_PHASE_ERROR -> {
                        if (csw.status == Scsi.STATUS_PHASE_ERROR) resetRecovery()
                        val sense = requestSense(lun)
                        if (ignoreUnsupported && sense.senseKey == Scsi.SENSE_ILLEGAL_REQUEST) return
                        val retryable = sense.retryable || csw.status == Scsi.STATUS_PHASE_ERROR
                        val ex = ScsiException(sense.message, sense.senseKey, sense.asc, sense.ascq, retryable)
                        if (retryable && attempt < attempts - 1) {
                            Thread.sleep(30L * (attempt + 1))
                            lastError = ex
                        } else throw ex
                    }
                    else -> {
                        val sense = requestSense(lun)
                        throw ScsiException(
                            sense.message.ifBlank { "SCSI command failed (status=${csw.status})" },
                            sense.senseKey,
                            sense.asc,
                            sense.ascq,
                            retryable = true
                        )
                    }
                }
            } catch (e: ScsiException) {
                lastError = e
                if (!e.retryable || attempt >= attempts - 1) throw e
                resetRecovery()
                Thread.sleep(30L * (attempt + 1))
            }
        }
        throw lastError ?: ScsiException("SCSI command failed", retryable = true)
    }

    private fun sendCbw(dataLength: Int, directionOut: Boolean, cdb: ByteArray, lun: Int, timeoutMs: Int): Int {
        val thisTag = tag++
        val packet = Scsi.buildCbw(thisTag, dataLength, directionOut, lun, cdb)
        val n = bulk(bulkOut, packet, Scsi.CBW_SIZE, timeoutMs)
        if (n != Scsi.CBW_SIZE) {
            clearHalt(bulkOut)
            throw ScsiException("CBW transfer failed (n=$n)", retryable = true)
        }
        return thisTag
    }

    private fun readCsw(expectedTag: Int, timeoutMs: Int, ignoreStatus: Boolean = false): Csw {
        val raw = ByteArray(Scsi.CSW_SIZE)
        var got = 0
        var tries = 0
        val tmp = ByteArray(Scsi.CSW_SIZE)
        while (got < Scsi.CSW_SIZE && tries < 6) {
            val n = bulk(bulkIn, tmp, Scsi.CSW_SIZE - got, timeoutMs)
            if (n <= 0) {
                clearHalt(bulkIn)
                if (tries == 2) resetRecovery()
                Thread.sleep(30L * (tries + 1))
                tries++
                continue
            }
            System.arraycopy(tmp, 0, raw, got, minOf(n, Scsi.CSW_SIZE - got))
            got += n
        }
        if (got < Scsi.CSW_SIZE) throw ScsiException(
            "USB reader dropped the reply (CSW n=$got). Keep PiFlash on screen.",
            retryable = true
        )
        val csw = Scsi.parseCsw(raw)
        if (csw.signature != Scsi.CSW_SIGNATURE) {
            clearHalt(bulkIn)
            throw ScsiException("Invalid CSW signature 0x${Integer.toHexString(csw.signature)}", retryable = true)
        }
        if (csw.tag != expectedTag) Log.w(TAG, "CSW tag mismatch expected=$expectedTag got=${csw.tag}")
        return csw
    }

    private fun requestSense(lun: Int): Sense {
        val thisTag = tag++
        val packet = Scsi.buildCbw(thisTag, 18, false, lun, Scsi.requestSense())
        val sent = bulk(bulkOut, packet, Scsi.CBW_SIZE, initTimeoutMs)
        if (sent != Scsi.CSW_SIZE && sent != Scsi.CBW_SIZE) {
            clearHalt(bulkOut)
            return Sense(0, -1, 0, 0, ByteArray(0))
        }
        val data = ByteArray(18)
        val bounce = ByteArray(18)
        val n = bulk(bulkIn, bounce, 18, initTimeoutMs)
        if (n > 0) {
            if (Scsi.isCsw(bounce)) {
                return Sense(0, -1, 0, 0, ByteArray(0))
            }
            System.arraycopy(bounce, 0, data, 0, minOf(n, 18))
        }
        runCatching { readCsw(thisTag, initTimeoutMs, ignoreStatus = true) }
        if (n < 3) return Sense(0, -1, 0, 0, data)
        return Scsi.parseSense(data)
    }

    private fun bulkOutAll(data: ByteArray, length: Int, timeoutMs: Int) {
        var sent = 0
        val bounce = ByteArray((bulkOut.maxPacketSize * 64).coerceAtLeast(bulkOut.maxPacketSize))
        var stalls = 0
        while (sent < length) {
            val nwant = minOf(length - sent, bounce.size)
            System.arraycopy(data, sent, bounce, 0, nwant)
            val n = bulk(bulkOut, bounce, nwant, timeoutMs)
            if (n <= 0) {
                clearHalt(bulkOut)
                if (++stalls > 3) throw ScsiException("USB write failed at offset $sent (n=$n)", retryable = true)
                continue
            }
            sent += n
        }
    }

    private fun bulkInAll(data: ByteArray, length: Int, timeoutMs: Int): Csw? {
        var rec = 0
        val bounce = ByteArray((bulkIn.maxPacketSize * 64).coerceAtLeast(maxOf(bulkIn.maxPacketSize, Scsi.CSW_SIZE)))
        var stalls = 0
        while (rec < length) {
            val nwant = minOf(length - rec, bounce.size)
            val n = bulk(bulkIn, bounce, nwant, timeoutMs)
            if (n <= 0) {
                clearHalt(bulkIn)
                if (++stalls > 3) throw ScsiException("USB read failed (n=$n)", retryable = true)
                continue
            }
            if (rec == 0 && n >= Scsi.CSW_SIZE && Scsi.isCsw(bounce)) {
                return Scsi.parseCsw(bounce.copyOf(Scsi.CSW_SIZE))
            }
            System.arraycopy(bounce, 0, data, rec, minOf(n, length - rec))
            rec += n
        }
        return null
    }

    private fun massStorageReset() {
        val type = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or 0x01
        val r = connection.controlTransfer(type, Scsi.REQ_MASS_STORAGE_RESET, 0, usbInterface.id, ByteArray(0), 0, initTimeoutMs)
        if (r < 0) Log.w(TAG, "Bulk-Only reset returned $r")
        clearHalt(bulkIn)
        clearHalt(bulkOut)
        requestsReady = false
        Thread.sleep(20)
    }

    private fun getMaxLun(): Int {
        val type = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or 0x01
        val buf = ByteArray(1)
        val r = connection.controlTransfer(type, Scsi.REQ_GET_MAX_LUN, 0, usbInterface.id, buf, 1, initTimeoutMs)
        return if (r >= 1) buf[0].toInt() and 0x0F else 0
    }

    private fun clearHalt(ep: UsbEndpoint) {
        runCatching {
            connection.controlTransfer(UsbConstants.USB_DIR_OUT or 0x02, 0x01, 0, ep.address, ByteArray(0), 0, initTimeoutMs)
        }
    }

    private fun resetRecovery() = massStorageReset()

    private fun prepareRequests() {
        if (requestsReady) return
        if (!outRequest.initialize(connection, bulkOut) || !inRequest.initialize(connection, bulkIn)) {
            throw ScsiException("UsbRequest.initialize failed")
        }
        requestsReady = true
    }

    private fun bulk(ep: UsbEndpoint, data: ByteArray, length: Int, timeoutMs: Int): Int {
        val box = AtomicInteger(Int.MIN_VALUE)
        val fail = AtomicReference<Throwable>()
        val t = Thread({
            try {
                val n = if (UsbOem.quirkyUsbStack && length > 512) {
                    requestBulk(ep, data, length, timeoutMs)
                } else {
                    connection.bulkTransfer(ep, data, length, timeoutMs)
                }
                box.set(n)
            } catch (e: Throwable) {
                fail.set(e)
            }
        }, "piflash-usb")
        t.start()
        t.join((timeoutMs + 400).toLong())
        if (t.isAlive) {
            runCatching { inRequest.cancel() }
            runCatching { outRequest.cancel() }
            Log.e(TAG, "USB hung ep=0x${Integer.toHexString(ep.address)} len=$length after ${timeoutMs}ms")
            throw ScsiException(
                "USB transfer hung (HyperOS ignored timeout). Force-stop PiFlash, unplug the reader, toggle OTG, retry.",
                retryable = true
            )
        }
        fail.get()?.let { throw it }
        return box.get()
    }

    private fun requestBulk(ep: UsbEndpoint, data: ByteArray, length: Int, timeoutMs: Int): Int {
        prepareRequests()
        val out = ep.direction == UsbConstants.USB_DIR_OUT
        val req = if (out) outRequest else inRequest
        val buf = ByteBuffer.allocateDirect(length)
        if (out) {
            buf.put(data, 0, length)
            buf.flip()
        } else {
            buf.clear()
            buf.limit(length)
        }
        synchronized(xferLock) {
            if (!req.queue(buf)) {
                throw ScsiException("USB queue failed", retryable = true)
            }
            val done = connection.requestWait(timeoutMs.toLong())
            if (done == null) {
                req.cancel()
                runCatching { connection.requestWait(50) }
                throw ScsiException("USB timed out after ${timeoutMs}ms", retryable = true)
            }
            val n = buf.position()
            if (!out && n > 0) {
                buf.position(0)
                val take = minOf(n, data.size)
                buf.get(data, 0, take)
                return take
            }
            return if (out) length else n
        }
    }

    override fun close() {
        runCatching { inRequest.close() }
        runCatching { outRequest.close() }
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    companion object {
        private const val TAG = "PiFlash.BOT"

        fun from(device: UsbDevice, connection: UsbDeviceConnection): BlockDeviceWriter {
            if (!UsbOem.quirkyUsbStack) {
                runCatching {
                    if (device.configurationCount > 0) connection.setConfiguration(device.getConfiguration(0))
                }
            }
            data class Candidate(val iface: UsbInterface, val inn: UsbEndpoint, val out: UsbEndpoint, val score: Int)
            val candidates = ArrayList<Candidate>()
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_MASS_STORAGE) continue
                if (iface.interfaceProtocol == Scsi.PROTOCOL_UAS) continue
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp == null || outEp == null) continue
                var score = 1
                if (iface.interfaceSubclass == Scsi.SUBCLASS_SCSI) score += 2
                if (iface.interfaceProtocol == Scsi.PROTOCOL_BOT) score += 3
                candidates += Candidate(iface, inEp, outEp, score)
            }
            val best = candidates.maxByOrNull { it.score }
                ?: throw ScsiException("No USB mass-storage Bulk-Only interface (UAS-only readers are not supported)")
            return BlockDeviceWriter(connection, best.iface, best.inn, best.out)
        }
    }
}

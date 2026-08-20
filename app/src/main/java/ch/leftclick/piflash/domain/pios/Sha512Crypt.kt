package ch.leftclick.piflash.domain.pios

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Ulrich Drepper's SHA-512 crypt (`$6$`), the scheme Raspberry Pi OS accepts
 * in `userconf.txt`. Matches `crypt(3)` / `openssl passwd -6`.
 */
object Sha512Crypt {
    private const val MAGIC = "$6$"
    private const val DEFAULT_ROUNDS = 5000
    private const val SALT_MAX = 16
    private val B64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()

    fun hash(password: String, salt: String = randomSalt(), rounds: Int = DEFAULT_ROUNDS): String {
        val roundsClamped = rounds.coerceIn(1000, 999_999_999)
        val saltClean = salt.take(SALT_MAX).replace("$", "")
        val pw = password.toByteArray(Charsets.UTF_8)
        val sa = saltClean.toByteArray(Charsets.US_ASCII)

        val md = MessageDigest.getInstance("SHA-512")

        md.update(pw)
        md.update(sa)
        md.update(pw)
        var altResult = md.digest()

        md.reset()
        md.update(pw)
        md.update(sa)

        var i = pw.size
        while (i > 64) {
            md.update(altResult)
            i -= 64
        }
        md.update(altResult, 0, i)

        i = pw.size
        while (i > 0) {
            if ((i and 1) != 0) md.update(altResult) else md.update(pw)
            i = i ushr 1
        }
        altResult = md.digest()

        val alt = MessageDigest.getInstance("SHA-512")
        repeat(pw.size) { alt.update(pw) }
        val pBytes = repeatToLength(alt.digest(), pw.size)

        alt.reset()
        val cnt = 16 + (altResult[0].toInt() and 0xFF)
        repeat(cnt) { alt.update(sa) }
        val sBytes = repeatToLength(alt.digest(), sa.size)

        var digest = altResult
        for (round in 0 until roundsClamped) {
            md.reset()
            if (round and 1 != 0) md.update(pBytes) else md.update(digest)
            if (round % 3 != 0) md.update(sBytes)
            if (round % 7 != 0) md.update(pBytes)
            if (round and 1 != 0) md.update(digest) else md.update(pBytes)
            digest = md.digest()
        }

        val encoded = encode(digest)
        return if (roundsClamped == DEFAULT_ROUNDS) {
            "$MAGIC$saltClean$$encoded"
        } else {
            "$MAGIC" + "rounds=$roundsClamped$$saltClean$$encoded"
        }
    }

    fun randomSalt(length: Int = 16): String {
        val rnd = SecureRandom()
        val n = length.coerceIn(8, SALT_MAX)
        return CharArray(n) { B64[rnd.nextInt(B64.size)] }.concatToString()
    }

    private fun repeatToLength(src: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = minOf(src.size, length - off)
            System.arraycopy(src, 0, out, off, n)
            off += n
        }
        return out
    }

    private fun encode(d: ByteArray): String {
        val sb = StringBuilder(86)
        fun b64(b2: Int, b1: Int, b0: Int, n: Int) {
            var w = ((b2 and 0xFF) shl 16) or ((b1 and 0xFF) shl 8) or (b0 and 0xFF)
            repeat(n) {
                sb.append(B64[w and 0x3F])
                w = w ushr 6
            }
        }
        fun b(i: Int) = d[i].toInt()
        b64(b(0), b(21), b(42), 4)
        b64(b(22), b(43), b(1), 4)
        b64(b(44), b(2), b(23), 4)
        b64(b(3), b(24), b(45), 4)
        b64(b(25), b(46), b(4), 4)
        b64(b(47), b(5), b(26), 4)
        b64(b(6), b(27), b(48), 4)
        b64(b(28), b(49), b(7), 4)
        b64(b(50), b(8), b(29), 4)
        b64(b(9), b(30), b(51), 4)
        b64(b(31), b(52), b(10), 4)
        b64(b(53), b(11), b(32), 4)
        b64(b(12), b(33), b(54), 4)
        b64(b(34), b(55), b(13), 4)
        b64(b(56), b(14), b(35), 4)
        b64(b(15), b(36), b(57), 4)
        b64(b(37), b(58), b(16), 4)
        b64(b(59), b(17), b(38), 4)
        b64(b(18), b(39), b(60), 4)
        b64(b(40), b(61), b(19), 4)
        b64(b(62), b(20), b(41), 4)
        b64(0, 0, b(63), 2)
        return sb.toString()
    }
}

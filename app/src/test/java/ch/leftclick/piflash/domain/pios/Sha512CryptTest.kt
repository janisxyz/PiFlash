package ch.leftclick.piflash.domain.pios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha512CryptTest {
    @Test
    fun opensslDefaultVector() {
        val hash = Sha512Crypt.hash("Hello world!", "saltstring")
        assertEquals(
            "\$6\$saltstring\$svn8UoSVapNtMuq1ukKS4tPQd8iKwSMHWjl/O817G3uBnIFNjnQJuesI68u4OTLiBFdcbYEdFCoEOfaS35inz1",
            hash
        )
    }

    @Test
    fun hashLooksLikeSha512Crypt() {
        val hash = Sha512Crypt.hash("piflash")
        assertTrue(hash.startsWith("\$6\$"))
        assertEquals(3, hash.count { it == '$' })
    }
}

package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineSplitterTest {

    private fun bytes(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun `satu baris lengkap dipotong di LF dan CR dipangkas`() {
        val s = LineSplitter()
        assertEquals(listOf("1B01"), s.feed(bytes("1B01\r\n")))
    }

    @Test
    fun `baris terpecah antar feed digabung`() {
        val s = LineSplitter()
        assertEquals(emptyList<String>(), s.feed(bytes("160")))
        assertEquals(listOf("1602;0;0"), s.feed(bytes("2;0;0\r\n")))
    }

    @Test
    fun `beberapa baris dalam satu feed`() {
        val s = LineSplitter()
        val lines = s.feed(bytes("1A00\r\nA603;1;2\r\n"))
        assertEquals(listOf("1A00", "A603;1;2"), lines)
    }

    @Test
    fun `baris mentah 2 byte atau kurang dibuang`() {
        val s = LineSplitter()
        // "a\r" =2 byte -> dibuang; "\r" =1 byte -> dibuang
        assertEquals(emptyList<String>(), s.feed(bytes("a\r\n\r\n")))
        // "ab\r" =3 byte -> diterima menjadi "ab"
        assertEquals(listOf("ab"), s.feed(bytes("ab\r\n")))
    }

    @Test
    fun `filter charset membuang karakter di luar whiteliste non-bulk`() {
        val s = LineSplitter(filterValidChars = true)
        // '#','\r' tidak valid -> "B1X"
        assertEquals(listOf("B1X"), s.feed(bytes("#B1#X\r\n")))
    }

    @Test
    fun `filter non-aktif mempertahankan karakter bulk`() {
        val s = LineSplitter(filterValidChars = false)
        assertEquals(listOf("#B1#"), s.feed(bytes("#B1#\r\n")))
    }

    @Test
    fun `karakter whitelist sesuai checkIfValidChar`() {
        val valid = "0aZ;., -+="
        valid.forEach { assertTrue("'$it' harus valid", LineSplitter.isValidChar(it)) }
        listOf('#', '$', '%', '(', ')', '\r', '\n', '/').forEach {
            assertFalseish(it)
        }
    }

    private fun assertFalseish(c: Char) =
        assertTrue("'$c' harus invalid", !LineSplitter.isValidChar(c))

    @Test
    fun `sisa byte menunggu feed berikutnya`() {
        val s = LineSplitter()
        s.feed(bytes("9500;5A"))
        assertEquals(listOf("9500;5A3F"), s.feed(bytes("3F\r\n")))
    }
}

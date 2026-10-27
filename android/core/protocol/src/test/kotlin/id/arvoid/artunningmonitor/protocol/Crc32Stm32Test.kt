package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vektor tes dihasilkan oleh implementasi referensi independen (Node.js) yang
 * mem-port persis algoritma IL ComputeSTM32Checksum(Byte[]) — lihat
 * tools/crc-vectors pada disk temp; konstanta di bawah hasil run tersebut.
 */
class Crc32Stm32Test {

    @Test
    fun `data kosong mengembalikan initial`() {
        assertEquals(0xFFFFFFFF.toInt(), Crc32Stm32.compute(ByteArray(0)))
    }

    @Test
    fun `vektor crc independent`() {
        assertEquals(0xFEFC54F9.toInt(), Crc32Stm32.compute("12345678".toByteArray(Charsets.US_ASCII)))
        assertEquals(0x4B29DF61.toInt(), Crc32Stm32.compute("1234567890123456".toByteArray(Charsets.US_ASCII)))
        assertEquals(0xFE4E032E.toInt(), Crc32Stm32.compute("12345678901234567890".toByteArray(Charsets.US_ASCII)))
        // "JUKEN5P" (7 byte) -> di-padding ke8 byte dgn nol
        assertEquals(0xD058C9A0.toInt(), Crc32Stm32.compute("JUKEN5P".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `format heksa8 huruf besar utk pembanding balasan 9500`() {
        assertEquals("FFFFFFFF", Crc32Stm32.toHex8(-1))
        assertEquals("FEFC54F9", Crc32Stm32.toHex8(0xFEFC54F9.toInt()))
    }
}

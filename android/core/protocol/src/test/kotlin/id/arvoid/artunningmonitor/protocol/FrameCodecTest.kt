package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCodecTest {

    @Test
    fun `frame dengan param memakai format HEX lalu param CRLF`() {
        assertEquals("1602;0;5\r\n", FrameCodec.buildLine(0x1602, "0;5"))
    }

    @Test
    fun `frame tanpa param hanya HEX CRLF`() {
        assertEquals("1B00\r\n", FrameCodec.buildLine(Opcodes.Ack.ACK_PING))
        assertEquals("160A\r\n", FrameCodec.buildLine(Opcodes.Read.ENTER_LIVE))
    }

    @Test
    fun `hex huruf besar tanpa padding nol`() {
        assertEquals("1500\r\n", FrameCodec.buildLine(0x1500))
        assertEquals("150D\r\n", FrameCodec.buildLine(5389)) // literal "150d" app -> huruf besar
        assertEquals("1A00\r\n", FrameCodec.buildLine(6656))
    }

    @Test
    fun `raw line memakai CRLF`() {
        assertEquals("#;8080;x;y;\r\n", FrameCodec.buildRawLine("#;8080;x;y;"))
    }

    @Test
    fun `ascii bytes benar`() {
        val bytes = FrameCodec.ascii("1B00\r\n")
        assertEquals(6, bytes.size)
        assertEquals('1'.code.toByte(), bytes[0])
        assertEquals('\n'.code.toByte(), bytes[5])
    }
}

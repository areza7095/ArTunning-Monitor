package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDispatchTest {

    @Test
    fun `parse frame telemetri A603`() {
        val r = ReplyDispatch.parse("A603;12;12.6;0;3450")
        assertEquals("A603", r.token)
        assertEquals(0xA603, r.code)
        assertTrue(r.isTelemetry)
        assertFalse(r.isEchoAck)
        assertEquals(5, r.parts.size)
        assertEquals("12", r.payload(0)) // payload(0) = parts[1]
        assertEquals("12", r.parts[1])
    }

    @Test
    fun `parse echo ack1B01`() {
        val r = ReplyDispatch.parse("1B01")
        assertEquals(Opcodes.Ack.FEEDBACK_ECHO_ACK, r.code)
        assertTrue(r.isEchoAck)
    }

    @Test
    fun `token di-uppercase dan dipotong 4 karakter terakhir`() {
        val r = ReplyDispatch.parse("ab9500;5a3f")
        assertEquals("9500", r.token)
        assertEquals(0x9500, r.code)
        // jalur korelasi app memakai token penuh
        assertEquals(0xAB9500, r.correlationCode)
    }

    @Test
    fun `parse heks case-insensitive`() {
        assertEquals(0x1A00, ReplyDispatch.parseHex("1a00"))
        assertEquals(0x1602, ReplyDispatch.parseHex("1602"))
        assertNull(ReplyDispatch.parseHex("ZZ"))
    }

    @Test
    fun `tabel korelasi request balasan dari codegen`() {
        assertEquals(0x9601, COMMAND_TO_REPLY[0x1601])
        assertEquals(0x9602, COMMAND_TO_REPLY[0x1602])
        assertEquals(0x9616, COMMAND_TO_REPLY[0x1617])
        assertEquals(0x9500, COMMAND_TO_REPLY[0x1500])
        assertEquals(13, COMMAND_TO_REPLY.size)
    }

    @Test
    fun `konstanta opcode hasil codegen sesuai spesifikasi`() {
        assertEquals(0x1602, Opcodes.Read.READ_FUEL_COR1)
        assertEquals(0x160A, Opcodes.Read.ENTER_LIVE)
        assertEquals(0x2602, Opcodes.Write.SAVE_FUEL_COR1)
        assertEquals(0x1B00, Opcodes.Ack.ACK_PING)
        assertEquals(0xA603, Opcodes.Reply.FEEDBACK_READ_ENGINE_RUN)
        assertEquals("#B1#", BulkMarkers.BEGIN_CAL)
        assertEquals("#B2#", BulkMarkers.BEGIN_MAP)
        assertEquals("#B3#", BulkMarkers.BEGIN_LOG)
        assertEquals("*eE*", BulkMarkers.END_TRIMMED)
        assertEquals("A603", TELEMETRY_HEADER)
    }
}

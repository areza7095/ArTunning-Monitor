package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionTest {

    private val telemetry = "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"

    @Test
    fun `handshake mengirim ping dan masuk Handshaking`() {
        val s = LiveSession()
        assertEquals(listOf("1B00\r\n"), s.onConnected(0))
        assertEquals(SessionState.Handshaking, s.state)
        assertTrue(s.connected)
        assertFalse(s.linkLost)
    }

    @Test
    fun `keepalive ping tiap 3 detik`() {
        val s = LiveSession()
        s.onConnected(0)
        s.autoLive = false // isolasi: uji keepalive murni tanpa auto-live
        assertEquals(emptyList<String>(), s.tick(2999))
        assertEquals(listOf("1B00\r\n"), s.tick(3000))
        assertEquals(emptyList<String>(), s.tick(4000))
        assertEquals(listOf("1B00\r\n"), s.tick(6001))
    }

    @Test
    fun `balasan 1B01 reset watchdog dan picu auto live`() {
        val s = LiveSession()
        s.onConnected(0)
        s.autoLive = false // isolasi: tanpa auto, fase watchdog tetap di Handshaking
        s.tick(17001) // tanpa echo reply -> >17000 -> link lost
        assertTrue(s.linkLost)

        // AUTO-LIVE jalur1: handshake diterima -> langsung1617 + state LiveRequested
        s.autoLive = true
        assertEquals(listOf("1617\r\n"), s.onRxLine("1B01", 17100))
        assertFalse(s.linkLost)
        assertEquals(SessionState.LiveRequested, s.state)
    }

    @Test
    fun `auto live memicu dari balasan 1B01 lalu streaming`() {
        val s = LiveSession()
        s.onConnected(0)
        assertEquals(listOf("1617\r\n"), s.onRxLine("1B01", 100))
        assertEquals(SessionState.LiveRequested, s.state)

        // retry160A mulai500 ms setelah enter (lastLiveTxMs =100)
        assertEquals(emptyList<String>(), s.tick(599))
        assertEquals(listOf("160A\r\n"), s.tick(600))

        s.onRxLine(telemetry, 700)
        assertEquals(SessionState.Streaming, s.state)
        assertEquals(emptyList<String>(), s.tick(800)) // tidak ada160A lagi
    }

    @Test
    fun `auto live fallback 1 5 detik tanpa balasan 1B01`() {
        val s = LiveSession()
        s.onConnected(0)
        assertEquals(emptyList<String>(), s.tick(1499)) // belum1500 ms
        assertEquals(listOf("1617\r\n"), s.tick(1500))  // fallback -> masuk live
        assertEquals(SessionState.LiveRequested, s.state)
        // tick berikutnya (>=500 ms) mengirim160A
        assertEquals(listOf("160A\r\n"), s.tick(2000))
    }

    @Test
    fun `stop live menonaktifkan auto live sampai connect ulang`() {
        val s = LiveSession()
        s.onConnected(0)
        s.onRxLine("1B01", 100)               // auto -> LiveRequested
        s.onRxLine(telemetry, 600)            // -> Streaming

        assertEquals(listOf("160B\r\n"), s.stopLive(1000))
        assertEquals(SessionState.Handshaking, s.state)
        assertFalse(s.autoLive)

        //1B01 berikutnya TIDAK memicu auto (keputusan manual)
        assertEquals(emptyList<String>(), s.onRxLine("1B01", 2000))
        assertEquals(SessionState.Handshaking, s.state)
        // fallback juga mati
        assertEquals(emptyList<String>(), s.tick(100_000).filter { it.startsWith("1617") })

        // connect ulang -> auto kembali aktif
        s.onConnected(200_000)
        assertTrue(s.autoLive)
        assertEquals(listOf("1617\r\n"), s.onRxLine("1B01", 200_100))
    }

    @Test
    fun `watchdog tidak jalan saat Streaming`() {
        val s = LiveSession()
        s.onConnected(0)
        s.onRxLine("1B01", 100)
        s.requestLive(1000)
        s.onRxLine(telemetry, 1600)
        assertEquals(SessionState.Streaming, s.state)

        s.tick(100_000) // jauh melebihi17 dtk tapi streaming -> tidak link-lost
        assertFalse(s.linkLost)
    }

    @Test
    fun `masuk live manual1617 lalu160A tiap 500ms maks25x`() {
        val s = LiveSession()
        s.onConnected(0)
        assertEquals(listOf("1617\r\n"), s.requestLive(1000))
        assertEquals(SessionState.LiveRequested, s.state)

        assertEquals(emptyList<String>(), s.tick(1400))      // belum500 ms
        assertEquals(listOf("160A\r\n"), s.tick(1500))       // tick pertama
        assertEquals(emptyList<String>(), s.tick(1900))
        assertEquals(listOf("160A\r\n"), s.tick(2000))       // tick kedua

        // 4600: keepalive (>=3000 sejak ping terakhir0) + live tick
        assertEquals(listOf("1B00\r\n", "160A\r\n"), s.tick(4600))

        // frame A603 -> Streaming, retry160A berhenti
        s.onRxLine(telemetry, 4700)
        assertEquals(SessionState.Streaming, s.state)
        assertEquals(emptyList<String>(), s.tick(5000))
        assertEquals(listOf("1B00\r\n"), s.tick(7600))
    }

    @Test
    fun `retry live berhenti setelah batas percobaan`() {
        val s = LiveSession()
        s.onConnected(0)
        s.requestLive(0)
        var liveSent = 0
        var now = 0L
        repeat(40) {
            now += 500
            if (s.tick(now).any { it.startsWith("160A") }) liveSent++
        }
        assertEquals(25, liveSent)
    }

    @Test
    fun `keluar live mengirim 160B`() {
        val s = LiveSession()
        s.onConnected(0)
        s.requestLive(0)
        s.onRxLine(telemetry, 600)
        assertEquals(SessionState.Streaming, s.state)

        assertEquals(listOf("160B\r\n"), s.stopLive(1000))
        assertEquals(SessionState.Handshaking, s.state)
    }

    @Test
    fun `telemetri diabaikan bila tidak sedang request live`() {
        val s = LiveSession()
        s.onConnected(0)
        s.autoLive = false // isolasi: tanpa auto,1B01 juga tidak memicu apa pun
        assertEquals(emptyList<String>(), s.onRxLine("1B01", 100))
        s.onRxLine(telemetry, 200)
        assertEquals(SessionState.Handshaking, s.state)
    }

    @Test
    fun `setelah disconnect tidak ada tx lagi`() {
        val s = LiveSession()
        s.onConnected(0)
        s.onDisconnected()
        assertEquals(SessionState.Idle, s.state)
        assertEquals(emptyList<String>(), s.tick(1_000_000))
        assertEquals(emptyList<String>(), s.requestLive(1_000_000))
    }
}

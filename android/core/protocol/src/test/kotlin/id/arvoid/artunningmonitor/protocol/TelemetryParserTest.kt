package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryParserTest {

    // Contoh transkrip dari docs/en/juken5-protocol-spec.md bagian 11.
    private val sampleLine = "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"

    @Test
    fun `parse contoh transkrip spesifikasi`() {
        val t = TelemetryParser.parse(sampleLine)
        assertNotNull(t)
        t!!
        assertEquals(12f, t.tpsPercent, 0.0001f)
        assertEquals(12.6f, t.batteryVolt, 0.0001f)
        assertEquals(0, t.coreId)
        assertEquals(3450f, t.rpm, 0.0001f)

        // skala sesuai Form1.SetText
        assertEquals(85.2f, t.eotCelsius, 0.0001f)   // [5]/10
        assertEquals(12.5f, t.afrRaw, 0.0001f)        // mentah
        assertEquals(101.3f, t.mapValue, 0.0001f)     // [7]/10
        assertEquals(34.5f, t.iatCelsius, 0.0001f)    // [8]/10
        assertEquals(1.52f, t.pumpAmps, 0.0001f)      // [9]/1000
        assertEquals(0.89f, t.injAmps, 0.0001f)       // [10]/1000
        assertEquals(3, t.afrBandIndex)               // (int)12.5 - 9 = 3 (hijau)
    }

    @Test
    fun `bukan frame A603 mengembalikan null`() {
        assertNull(TelemetryParser.parse("1A00"))
        assertNull(TelemetryParser.parse("9500;5A3F;9BC1"))
    }

    @Test
    fun `frame A603 tidak lengkap mengembalikan null`() {
        assertNull(TelemetryParser.parse("A603;12;12.6"))
    }

    @Test
    fun `binning kolom RPM`() {
        assertEquals(0, TelemetryParser.rpmColumn(500f))    // clamp ke1000
        assertEquals(0, TelemetryParser.rpmColumn(1000f))
        assertEquals(10, TelemetryParser.rpmColumn(3450f))  // q=3500 -> (3500-1000)/250
        assertEquals(60, TelemetryParser.rpmColumn(16000f))
        assertEquals(60, TelemetryParser.rpmColumn(20000f)) // clamp atas
        assertEquals(0, TelemetryParser.rpmColumnIgn(1000f))
        assertEquals(5, TelemetryParser.rpmColumnIgn(3450f))
    }

    @Test
    fun `binning baris TPS`() {
        assertEquals(0, TelemetryParser.tpsRow(0f))
        assertEquals(1, TelemetryParser.tpsRow(1f))
        assertEquals(1, TelemetryParser.tpsRow(2f))
        assertEquals(0, TelemetryParser.tpsRow(2.5f)) // di luar[1,2] & [3,94] & <95 -> else (0)
        assertEquals(3, TelemetryParser.tpsRow(10f))
        assertEquals(19, TelemetryParser.tpsRow(94f))
        assertEquals(20, TelemetryParser.tpsRow(95f))
        assertEquals(20, TelemetryParser.tpsRow(100f))
    }

    @Test
    fun `pembulatan banker sesuai MathRound NET`() {
        assertEquals(14.0, TelemetryParser.roundBanker(13.8), 1e-9)
        assertEquals(4.0, TelemetryParser.roundBanker(4.5), 1e-9)   // ke genap
        assertEquals(6.0, TelemetryParser.roundBanker(5.5), 1e-9)   // ke genap
        assertEquals(2.0, TelemetryParser.roundBanker(2.5), 1e-9)   // ke genap
        assertEquals(4.0, TelemetryParser.roundBanker(3.5), 1e-9)   // f=3 (ganjil) ->4
    }
}

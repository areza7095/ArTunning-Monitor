package id.arvoid.artunningmonitor.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {

    private val sample = TelemetryParser.parse(
        "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"
    )!!

    @Test
    fun `serialize deserialize roundtrip mempertahankan urutan`() {
        val set = listOf(MetricKey.RPM, MetricKey.MAP, MetricKey.INJ, MetricKey.AFR)
        assertEquals("rpm,map,inj,afr", MetricSet.serialize(set))
        assertEquals(set, MetricSet.deserialize(MetricSet.serialize(set)))
    }

    @Test
    fun `deserialize kosong atau null memakai default`() {
        assertEquals(MetricKey.DEFAULT, MetricSet.deserialize(null))
        assertEquals(MetricKey.DEFAULT, MetricSet.deserialize(""))
        assertEquals(MetricKey.DEFAULT, MetricSet.deserialize("   "))
    }

    @Test
    fun `deserialize membuang id tak dikenal dan duplikat`() {
        val out = MetricSet.deserialize("rpm,bogus,tps,rpm,iat")
        assertEquals(listOf(MetricKey.RPM, MetricKey.TPS, MetricKey.IAT), out)
        // kalau semua tak dikenal -> default
        assertEquals(MetricKey.DEFAULT, MetricSet.deserialize("bogus,xyz"))
    }

    @Test
    fun `semua metric key punya formatter dan satuan`() {
        for (key in MetricKey.ALL) {
            assertTrue("value $key kosong", MetricFormatter.value(sample, key).isNotEmpty())
        }
        assertEquals("", MetricFormatter.unit(MetricKey.AFR))
        assertEquals("kPa", MetricFormatter.unit(MetricKey.MAP))
    }

    @Test
    fun `format nilai sesuai spesifikasi gauge`() {
        assertEquals("3450", MetricFormatter.value(sample, MetricKey.RPM))
        assertEquals("12", MetricFormatter.value(sample, MetricKey.TPS))
        assertEquals("12.50", MetricFormatter.value(sample, MetricKey.AFR))
        assertEquals("85.2", MetricFormatter.value(sample, MetricKey.EOT))
        assertEquals("101.3", MetricFormatter.value(sample, MetricKey.MAP))
        assertEquals("34.5", MetricFormatter.value(sample, MetricKey.IAT))
        assertEquals("12.60", MetricFormatter.value(sample, MetricKey.BATT))
        assertEquals("1.520", MetricFormatter.value(sample, MetricKey.PUMP))
        assertEquals("0.890", MetricFormatter.value(sample, MetricKey.INJ))
    }

    @Test
    fun `summary hanya memuat metrik terpilih dan berformat lokal bebas`() {
        val s = MetricFormatter.summary(sample, listOf(MetricKey.RPM, MetricKey.AFR, MetricKey.MAP))
        assertEquals("3450 rpm · AFR 12.50 · 101.3 kPa", s)
        // tidak bocor metrik di luar pilihan
        assertTrue(!s.contains("EOT"))
        assertTrue(!s.contains("85.2"))
    }
}

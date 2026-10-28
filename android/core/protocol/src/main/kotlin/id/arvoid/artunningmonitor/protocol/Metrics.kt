package id.arvoid.artunningmonitor.protocol

import java.util.Locale

/**
 * Kunci metrik telemetri — dipakai untuk:
 *  - konfigurasi widget (pilih metrik mana yang tampil)
 *  - formatter seragam (app, widget, media session Android Auto, notifikasi)
 * Nilai/format identik dengan gauge UI (spec bagian 9).
 */
enum class MetricKey(val id: String, val label: String, val unit: String) {
    RPM("rpm", "RPM", "rpm"),
    TPS("tps", "TPS", "%"),
    AFR("afr", "AFR", ""),
    EOT("eot", "EOT", "°C"),
    MAP("map", "MAP", "kPa"),
    IAT("iat", "IAT", "°C"),
    BATT("batt", "BATT", "V"),
    PUMP("pump", "PUMP", "A"),
    INJ("inj", "INJ", "A");

    companion object {
        val ALL: List<MetricKey> = entries.toList()

        /** Urutan pilihan widget: RPM/TPS/AFR/EOT dulu (paling lazim). */
        val DEFAULT: List<MetricKey> = listOf(RPM, TPS, AFR, EOT)

        fun fromId(id: String): MetricKey? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Set metrik terpilih per widget/konfigurasi pengguna + serialisasi.
 * Format simpan: "rpm,tps,afr" (id dipisah koma) — ringkas & tahan perubahan urutan.
 */
object MetricSet {

    fun serialize(metrics: List<MetricKey>): String =
        metrics.joinToString(",") { it.id }

    /** id tak dikenal diabaikan; hasil kosong -> [MetricKey.DEFAULT]. */
    fun deserialize(raw: String?): List<MetricKey> {
        if (raw.isNullOrBlank()) return MetricKey.DEFAULT
        val list = raw.split(',')
            .mapNotNull { MetricKey.fromId(it.trim()) }
            .distinct()
        return list.ifEmpty { MetricKey.DEFAULT }
    }
}

/**
 * Formatter angka telemetri — satu-satunya sumber format (Locale.ROOT, pemisah '.').
 * Nilai sesuai gauge Android: RPM/TPS0 dtk, AFR2, EOT/MAP/IAT1, BATT2, PUMP/INJ3.
 */
object MetricFormatter {

    private val LOC = Locale.ROOT

    fun value(sample: TelemetrySample, key: MetricKey): String = when (key) {
        MetricKey.RPM -> String.format(LOC, "%.0f", sample.rpm)
        MetricKey.TPS -> String.format(LOC, "%.0f", sample.tpsPercent)
        MetricKey.AFR -> String.format(LOC, "%.2f", sample.afrRaw)
        MetricKey.EOT -> String.format(LOC, "%.1f", sample.eotCelsius)
        MetricKey.MAP -> String.format(LOC, "%.1f", sample.mapValue)
        MetricKey.IAT -> String.format(LOC, "%.1f", sample.iatCelsius)
        MetricKey.BATT -> String.format(LOC, "%.2f", sample.batteryVolt)
        MetricKey.PUMP -> String.format(LOC, "%.3f", sample.pumpAmps)
        MetricKey.INJ -> String.format(LOC, "%.3f", sample.injAmps)
    }

    /** Satuan tampil (AFR tanpa satuan -> string kosong). */
    fun unit(key: MetricKey): String = key.unit

    /** "3450 rpm · TPS12% · AFR 12.50" — untuk notifikasi & metadata Android Auto. */
    fun summary(sample: TelemetrySample, metrics: List<MetricKey>): String =
        metrics.joinToString(" · ") { m ->
            val v = value(sample, m)
            if (m.unit.isEmpty()) "${m.label} $v" else "$v ${m.unit}"
        }
}

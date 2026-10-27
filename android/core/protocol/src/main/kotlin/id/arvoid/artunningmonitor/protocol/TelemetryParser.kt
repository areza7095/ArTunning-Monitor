package id.arvoid.artunningmonitor.protocol

import kotlin.math.floor

/**
 * Satu frame telemetri/status `A603` (spec bagian 9).
 * Nilai masih mentah (raw) sesuai yang dikirim ECU; properti turunan menerapkan skala.
 *
 * Layout: A603;<TPS>;<BAT>;<CORE>;<RPM>;<EOTx10>;<AFR>;<MAPx10>;<IATx10>;<PUMPx1000>;<INJx1000>
 * Bukti: Form1.SetText IL_0854-IL_0DDF.
 */
data class TelemetrySample(
    val tpsPercent: Float,
    val batteryVolt: Float,
    val coreId: Int,
    val rpm: Float,
    val eotRaw: Float,
    val afrRaw: Float,
    val mapRaw: Float,
    val iatRaw: Float,
    val pumpRaw: Float,
    val injRaw: Float,
) {
    val eotCelsius: Float get() = eotRaw / 10f
    val mapValue: Float get() = mapRaw / 10f
    val iatCelsius: Float get() = iatRaw / 10f
    val pumpAmps: Float get() = pumpRaw / 1000f
    val injAmps: Float get() = injRaw / 1000f

    /** Band warna AFR aplikasi: nilai_afr = (int)AFR - 9 (biru<=2, hijau=3, kuning=4, merah>4). */
    val afrBandIndex: Int get() = afrRaw.toInt() - 9
}

object TelemetryParser {

    /**
     * Parse baris menjadi [TelemetrySample]; null bila bukan frame A603 / tidak lengkap.
     * Angka memakai pemisah desimal '.' (InvariantCulture — spec bagian 3.1).
     */
    fun parse(line: String): TelemetrySample? {
        val reply = ReplyDispatch.parse(line)
        if (!reply.isTelemetry) return null
        if (reply.parts.size < MIN_PARTS) return null

        fun f(i: Int): Float? = reply.parts[i].toFloatOrNull()
        val tps = f(1) ?: return null
        val batt = f(2) ?: return null
        val core = reply.parts[3].toIntOrNull() ?: return null
        val rpm = f(4) ?: return null
        val eot = f(5) ?: return null
        val afr = f(6) ?: return null
        val map = f(7) ?: return null
        val iat = f(8) ?: return null
        val pump = f(9) ?: return null
        val inj = f(10) ?: return null
        return TelemetrySample(tps, batt, core, rpm, eot, afr, map, iat, pump, inj)
    }

    const val MIN_PARTS = 11

    /**
     * Binning kolom RPM aplikasi (Form1.SetText IL_0A22-IL_0A9A):
     * clamp1000..16000, q = roundBanker(rpm*4/1000)*1000/4 (kelipatan250),
     * kolom = (q-1000)/250.
     */
    fun rpmColumn(rpm: Float): Int {
        val clamped = rpm.coerceIn(1000f, 16000f)
        val q = roundBanker(clamped * 4.0 / 1000.0).toInt() * 1000 / 4
        return (q - 1000) / 250
    }

    /** Binning kolom untuk peta pengapian: (q-1000)/500. */
    fun rpmColumnIgn(rpm: Float): Int {
        val clamped = rpm.coerceIn(1000f, 16000f)
        val q = roundBanker(clamped * 4.0 / 1000.0).toInt() * 1000 / 4
        return (q - 1000) / 500
    }

    /**
     * Binning baris TPS (Form1.SetText IL_0A9F-IL_0B47):
     * [1,2] -> 1 ; [3,94] -> floor(V+5)/5 dgn V = floor(tps*2/10)*10/2 ; >=95 -> 20 ; selain itu 0.
     */
    fun tpsRow(tps: Float): Int = when {
        tps in 1f..2f -> 1
        tps in 3f..94f -> {
            val v = (floor(tps * 2.0 / 10.0).toFloat() * 10f) / 2f
            (floor(v + 5f).toInt()) / 5
        }
        tps >= 95f -> 20
        else -> 0
    }

    /** Math.Round(.NET) = pembulatan ke genap terdekat (banker's rounding). */
    internal fun roundBanker(x: Double): Double {
        val f = floor(x)
        val frac = x - f
        return when {
            frac > 0.5 -> f + 1.0
            frac < 0.5 -> f
            f % 2.0 == 0.0 -> f
            else -> f + 1.0
        }
    }
}

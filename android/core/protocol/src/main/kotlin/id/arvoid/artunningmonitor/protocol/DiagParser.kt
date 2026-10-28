package id.arvoid.artunningmonitor.protocol

/**
 * Balasan diagnostik yang di-parse EcUService selain telemetri A603.
 * Semua field masih mentah sesuai frame; turunan menerapkan skala/label.
 */

/** Status satu bit DIAG2: 00=OK, 01=ERROR, 10=NA, 11=RESERVED (spec bagian 5). */
enum class DiagBit(val code: Int, val label: String) {
    OK(0, "OK"),
    ERROR(1, "ERROR"),
    NA(2, "N/A"),
    RESERVED(3, "RESERVED");

    companion object {
        fun fromCode(c: Int): DiagBit = entries.firstOrNull { it.code == c } ?: RESERVED
    }
}

/** Satu field hasil decode A602 (urutan field = urutan frame). */
data class DiagField(val index: Int, val raw: String, val bit: DiagBit)

/** Hasil A601 TPS_CAL — value + voltage display (format N2, unit V). */
data class TpsCalSample(val raw: String, val volts: Float)

/** Hasil 9616 ECU model — decode enum ChargerModelComm (spec bagian 10.7). */
data class EcuModelSample(val rawModel: Int, val modelName: String) {
    companion object {
        fun fromRaw(raw: Int): EcuModelSample = EcuModelSample(
            rawModel = raw,
            modelName = when (raw) {
                0 -> "JUKEN 5"
                1 -> "JUKEN 5 PLUS"
                2 -> "JUKEN 6"
                else -> "MODEL_$raw"
            },
        )
    }
}

/** Hasil B800 MAINTAIN_REPORT — counter runtime (format 3600/60 detik → detik = raw/60). */
data class RuntimeSample(val rawSeconds: Float) {
    val totalSeconds: Float get() = rawSeconds / 60f

    /** "1j 23m 45s" — tampilan ramah; nol bagian tetap ditampilkan agar stabil. */
    fun formatHms(): String {
        val s = totalSeconds.toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return "%dh %02dm %02ds".format(h, m, sec)
    }
}

/**
 * Parser balasan diagnostik. Semua fungsi menerima baris mentah dan return null
 * bila bukan balasan yang dituju / field tidak valid — caller tidak perlu cek token.
 */
object DiagParser {

    /** A602: seluruh field setelah token di-decode per 2-bit. */
    fun parseDiag2(line: String): List<DiagField>? {
        val reply = ReplyDispatch.parse(line)
        if (reply.code != Opcodes.Reply.FEEDBACK_READ_DIAG2) return null
        // parts[0] = token; field = parts[1..]
        val fields = reply.parts.drop(1)
        if (fields.isEmpty()) return null
        return fields.mapIndexed { i, raw ->
            val n = raw.toIntOrNull()
            DiagField(i, raw, if (n != null) DiagBit.fromCode(n) else DiagBit.RESERVED)
        }
    }

    /** A601: voltage display (N2). Ambil field pertama yang bisa di-parse float. */
    fun parseTpsCal(line: String): TpsCalSample? {
        val reply = ReplyDispatch.parse(line)
        if (reply.code != Opcodes.Reply.FEEDBACK_READ_TPS_CAL) return null
        val raw = reply.parts.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        val volts = raw.toFloatOrNull() ?: return null
        return TpsCalSample(raw, volts)
    }

    /** 9616: model ECU — field pertama integer enum. */
    fun parseEcuModel(line: String): EcuModelSample? {
        val reply = ReplyDispatch.parse(line)
        if (reply.code != Opcodes.Reply.FEEDBACK_READ_USER_DEF_2) return null
        val raw = reply.parts.getOrNull(1)?.trim() ?: return null
        val model = raw.toIntOrNull() ?: return null
        return EcuModelSample.fromRaw(model)
    }

    /** B800: counter runtime maintenance. */
    fun parseRuntime(line: String): RuntimeSample? {
        val reply = ReplyDispatch.parse(line)
        if (reply.code != Opcodes.Write.MAINTAIN_REPORT) return null
        val raw = reply.parts.getOrNull(1)?.trim() ?: return null
        val v = raw.toFloatOrNull() ?: return null
        return RuntimeSample(v)
    }
}

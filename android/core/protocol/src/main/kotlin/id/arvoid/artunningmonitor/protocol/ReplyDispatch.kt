package id.arvoid.artunningmonitor.protocol

/**
 * Hasil pembacaan satu baris RX.
 *
 * @property rawFirst  token pertama apa adanya (sebelum dipotong).
 * @property token      token pertama maksimal 4 karakter terakhir (aturan app:
 *                      Substring(len-4) bila panjang > 4), sudah huruf besar.
 * @property code       [token] di-parse heksadesimal (null bila bukan heks).
 * @property correlationCode [rawFirst] di-parse heks — dipakai jalur korelasi
 *                      CommandValuePairs (app memakai NumberStyles.HexNumber).
 * @property parts      seluruh field setelah split ';'.
 */
data class Reply(
    val rawFirst: String,
    val token: String,
    val code: Int?,
    val correlationCode: Int?,
    val parts: List<String>,
) {
    val isTelemetry: Boolean get() = token == TELEMETRY_HEADER
    val isEchoAck: Boolean get() = code == Opcodes.Ack.FEEDBACK_ECHO_ACK
    val isAck: Boolean get() = code == Opcodes.Ack.ACK

    fun part(index: Int): String? = parts.getOrNull(index)
    fun payload(index: Int): String? = parts.getOrNull(index + 1)
}

/**
 * Dispatch token balasan ECU (spec bagian 5).
 *
 * Normalisasi app (Form1.SetText IL_0482-IL_04F5):
 *   Replace CRLF/CR -> "" ; ToUpper ; Split(';') ; token = 4 karakter terakhir
 *   dari token pertama bila panjangnya lebih dari4.
 */
object ReplyDispatch {

    fun parse(line: String): Reply {
        val clean = line.replace("\r", "").replace("\n", "").uppercase()
        val parts = clean.split(';')
        val rawFirst = parts.getOrElse(0) { "" }
        val token = if (rawFirst.length > 4) rawFirst.substring(rawFirst.length - 4) else rawFirst
        return Reply(
            rawFirst = rawFirst,
            token = token,
            code = parseHex(token),
            correlationCode = parseHex(rawFirst),
            parts = parts,
        )
    }

    /** Setara int.TryParse(s, NumberStyles.HexNumber, InvariantCulture, ...) (IL_0029-IL_003A). */
    fun parseHex(s: String): Int? = s.trim().toIntOrNull(16)
}

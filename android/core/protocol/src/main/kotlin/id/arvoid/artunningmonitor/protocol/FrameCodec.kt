package id.arvoid.artunningmonitor.protocol

/**
 * Codec frame baris protokol ECU Juken 5 Plus.
 *
 * Sumber: docs/en/juken5-protocol-spec.md bagian 3.1 —
 * TX = "<HEX>" atau "<HEX>;<param>" diakhiri CRLF;
 * HEX = opcode.ToString("X") (huruf besar, tanpa padding nol).
 * Bukti IL: Juken_5.Protocol.CommManagement.AddCommMessage.
 */
object FrameCodec {
    const val LINE_END = "\r\n"

    /** Susun satu frame TX. [param] boleh multi-field dengan pemisah ';' (sudah termasuk). */
    fun buildLine(command: Int, param: String = ""): String {
        val hex = command.toString(16).uppercase()
        return if (param.isEmpty()) hex + LINE_END else "$hex;$param" + LINE_END
    }

    /** Frame literal mentah (mis. pola provisioning "#;8080;...") tanpa interpretasi opcode. */
    fun buildRawLine(payload: String): String = payload + LINE_END

    fun ascii(line: String): ByteArray = line.toByteArray(Charsets.US_ASCII)
}

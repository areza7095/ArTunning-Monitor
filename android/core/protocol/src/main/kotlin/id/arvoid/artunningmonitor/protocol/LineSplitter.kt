package id.arvoid.artunningmonitor.protocol

/**
 * Pembagi byte RX menjadi baris teks.
 *
 * Aturan (spec bagian 3.2; bukti IL SerialCommHandler.ExtractDataToArray +
 * Form1.checkIfValidChar):
 *  - pemisah baris = LF (0x0A); sisa byte menunggu feed berikutnya;
 *  - baris diterima hanya bila panjang mentah > 2 byte;
 *  - [filterValidChars=true] (mode non-bulk) hanya karakter
 *    [0-9A-Za-z] plus ';' '.' ',' spasi '-' '+' '=';
 *  - CR di ekor baris dipangkas.
 *
 * Mode bulk (filterValidChars=false) mempertahankan semua karakter —
 * untuk MVP live monitor mode inilah yang jarang dipakai (bulk = fase berikutnya).
 */
class LineSplitter(private val filterValidChars: Boolean = true) {

    private val buf = ArrayList<Byte>(128)

    /** Terima byte mentah, kembalikan baris-baris yang sudah lengkap. */
    fun feed(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        for (b in data) {
            if (b.toInt() and 0xFF == CODE_LF) {
                if (buf.size > MIN_RAW_BYTES) out += buildLine()
                buf.clear()
            } else {
                buf.add(b)
            }
        }
        return out
    }

    fun reset() {
        buf.clear()
    }

    private fun buildLine(): String {
        val sb = StringBuilder(buf.size)
        for (i in buf.indices) {
            val ch = (buf[i].toInt() and 0xFF).toChar()
            if (i == buf.size - 1 && ch == '\r') continue // pangkas CR ekor
            if (!filterValidChars || isValidChar(ch)) sb.append(ch)
        }
        return sb.toString()
    }

    companion object {
        const val CODE_LF = 0x0A
        const val MIN_RAW_BYTES = 2

        /** Portabulan Form1.checkIfValidChar (char ASCII yang diterima mode non-bulk). */
        fun isValidChar(c: Char): Boolean = when {
            c in '0'..'9' -> true
            c in 'A'..'Z' -> true
            c in 'a'..'z' -> true
            else -> c == ';' || c == '.' || c == ',' || c == ' ' || c == '-' || c == '+' || c == '='
        }
    }
}

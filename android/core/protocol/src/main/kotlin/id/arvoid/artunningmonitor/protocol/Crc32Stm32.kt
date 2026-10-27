package id.arvoid.artunningmonitor.protocol

import java.util.Locale

/**
 * CRC32 ala STM32 (hardware CRC): non-reflected, MSB-first,
 * init 0xFFFFFFFF, polinomial 0x04C11DB7, tanpa final XOR.
 *
 * Port persis dari JukenDataManagement.ComputeSTM32Checksum(Byte[]):
 * input dibaca sebagai kata uint32 little-endian (Buffer.BlockCopy),
 * per kata: crc ^= word lalu32 langkah: crc = (crc<<1) ^ poly bila MSB terbit.
 * Bukti IL: isGetMapValid memakai init=ldc.i4.m1, poly=ldc.i4 79764919 (0x04C11DB7).
 *
 * Catatan padding: app memakai Math.Round(len/4)*4 (perilaku .NET banker's);
 * semua data ECU kelipatan4 (cal=1012, map=10248, log=28) sehingga identik.
 * Implementasi ini memakai ceil agar input apa pun tidak pernah terpotong.
 */
object Crc32Stm32 {

    const val INITIAL = -1 // 0xFFFFFFFF
    const val POLYNOMIAL = 0x04C11DB7

    fun compute(data: ByteArray, initial: Int = INITIAL): Int {
        var crc = initial
        val words = (data.size + 3) / 4
        for (w in 0 until words) {
            val base = w * 4
            var word = 0
            for (k in 0 until 4) {
                val idx = base + k
                val b = if (idx < data.size) data[idx].toInt() and 0xFF else 0
                word = word or (b shl (8 * k))
            }
            crc = crc xor word
            repeat(32) {
                crc = if (crc and Int.MIN_VALUE != 0) (crc shl 1) xor POLYNOMIAL else (crc shl 1)
            }
        }
        return crc
    }

    /** Teks heksa8 huruf besar — format yang dipakai balasan "9500;<calCRC>;<mapCRC>". */
    fun toHex8(crc: Int): String = String.format(Locale.ROOT, "%08X", crc)
}

import Foundation

/// CRC32 ala STM32 (hardware CRC): non-reflected, MSB-first,
/// init 0xFFFFFFFF, polinomial 0x04C11DB7, tanpa final XOR.
///
/// Port persis JukenDataManagement.ComputeSTM32Checksum(Byte[]):
/// input dibaca sebagai kata uint32 little-endian, per kata:
/// crc ^= word lalu32 langkah: crc = (crc<<1) ^ poly bila MSB terbit.
/// Semua aritmetika di UInt32 (bitPattern di akhir) — tanpa overflow trap.
///
/// Catatan padding: app memakai Math.Round(len/4)*4; semua data ECU kelipatan4
/// (cal=1012, map=10248, log=28) sehingga identik; implementasi ini memakai ceil.
public enum Crc32Stm32 {
    public static let initial: Int32 = -1 // 0xFFFFFFFF
    public static let polynomial: UInt32 = 0x04C11DB7

    public static func compute(_ data: [UInt8], initial: Int32 = Crc32Stm32.initial) -> Int32 {
        var crc = UInt32(bitPattern: initial)
        let words = (data.count + 3) / 4
        for w in 0..<words {
            var word: UInt32 = 0
            for k in 0..<4 {
                let idx = w * 4 + k
                let b: UInt32 = idx < data.count ? UInt32(data[idx]) : 0
                word |= b << (8 * k)
            }
            crc ^= word
            for _ in 0..<32 {
                if crc & 0x80000000 != 0 {
                    crc = (crc << 1) ^ polynomial
                } else {
                    crc = crc << 1
                }
            }
        }
        return Int32(bitPattern: crc)
    }

    /// Teks heksa8 huruf besar — format balasan "9500;<calCRC>;<mapCRC>".
    public static func toHex8(_ crc: Int32) -> String {
        let s = String(UInt32(bitPattern: crc), radix: 16, uppercase: true)
        return String(repeating: "0", count: max(0, 8 - s.count)) + s
    }
}

import Foundation

/// Pembagi byte RX menjadi baris teks.
///
/// Aturan (spec bagian 3.2; port LineSplitter.kt):
///  - pemisah baris = LF (0x0A); sisa byte menunggu feed berikutnya;
///  - baris diterima hanya bila panjang mentah > 2 byte;
///  - `filterValidChars == true` (mode non-bulk) hanya karakter [0-9A-Za-z]
///    plus ';' '.' ',' spasi '-' '+' '=';
///  - CR di ekor baris dipangkas.
public final class LineSplitter {
    public static let codeLF: UInt8 = 0x0A
    public static let minRawBytes = 2

    private let filterValidChars: Bool
    private var buf: [UInt8] = []

    public init(filterValidChars: Bool = true) {
        self.filterValidChars = filterValidChars
    }

    /// Terima byte mentah, kembalikan baris-baris yang sudah lengkap.
    public func feed(_ data: [UInt8]) -> [String] {
        var out: [String] = []
        for b in data {
            if b == Self.codeLF {
                if buf.count > Self.minRawBytes { out.append(buildLine()) }
                buf.removeAll(keepingCapacity: true)
            } else {
                buf.append(b)
            }
        }
        return out
    }

    public func reset() {
        buf.removeAll(keepingCapacity: false)
    }

    private func buildLine() -> String {
        var s = ""
        s.reserveCapacity(buf.count)
        for (i, b) in buf.enumerated() {
            if i == buf.count - 1 && b == UInt8(ascii: "\r") { continue } // pangkas CR ekor
            let ch = Character(UnicodeScalar(b))
            if !filterValidChars || Self.isValidChar(ch) { s.append(ch) }
        }
        return s
    }

    /// Portabulan Form1.checkIfValidChar (char ASCII yang diterima mode non-bulk).
    public static func isValidChar(_ c: Character) -> Bool {
        guard let u = c.asciiValue else { return false } // non-ASCII selalu ditolak
        if (48...57).contains(u) { return true } // 0-9
        if (65...90).contains(u) { return true } // A-Z
        if (97...122).contains(u) { return true } // a-z
        return ";., -+=".contains(c)
    }
}

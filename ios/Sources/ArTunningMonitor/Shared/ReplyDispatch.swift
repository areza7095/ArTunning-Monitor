import Foundation

/// Hasil pembacaan satu baris RX (selaras dgn ReplyDispatch.kt).
///
/// - `rawFirst`: token pertama apa adanya (sebelum dipotong).
/// - `token`: 4 karakter terakhir bila panjang > 4, sudah huruf besar.
/// - `code`: token di-parse heksadesimal (nil bila bukan heks).
/// - `correlationCode`: rawFirst di-parse heks (jalur CommandValuePairs).
public struct Reply {
    public let rawFirst: String
    public let token: String
    public let code: Int?
    public let correlationCode: Int?
    public let parts: [String]

    public var isTelemetry: Bool { token == TELEMETRY_HEADER }
    public var isEchoAck: Bool { code == Opcodes.Ack.FEEDBACK_ECHO_ACK }
    public var isAck: Bool { code == Opcodes.Ack.ACK }

    public func part(_ index: Int) -> String? {
        parts.indices.contains(index) ? parts[index] : nil
    }

    /// Field ke-`index` setelah header (index 0 = field pertama payload).
    public func payload(_ index: Int) -> String? {
        part(index + 1)
    }
}

/// Dispatch token balasan ECU (spec bagian 5).
/// Normalisasi app: Replace CRLF/CR -> "" ; ToUpper ; Split(';') ; token = 4 karakter terakhir.
public enum ReplyDispatch {
    public static func parse(_ line: String) -> Reply {
        let clean = line
            .replacingOccurrences(of: "\r", with: "")
            .replacingOccurrences(of: "\n", with: "")
            .uppercased()
        let parts = clean.components(separatedBy: ";")
        let rawFirst = parts.first ?? ""
        let token = rawFirst.count > 4 ? String(rawFirst.suffix(4)) : rawFirst
        return Reply(
            rawFirst: rawFirst,
            token: token,
            code: parseHex(token),
            correlationCode: parseHex(rawFirst),
            parts: parts
        )
    }

    /// Setara int.TryParse(s, NumberStyles.HexNumber, ...) — heks, boleh berspasi.
    public static func parseHex(_ s: String) -> Int? {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return Int(t, radix: 16)
    }
}

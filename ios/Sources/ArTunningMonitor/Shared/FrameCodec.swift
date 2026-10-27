import Foundation

/// Codec frame baris protokol ECU Juken 5 Plus.
///
/// Sumber: docs/juken5-protocol-spec.md bagian 3.1 —
/// TX = "<HEX>" atau "<HEX>;<param>" diakhiri CRLF;
/// HEX = opcode.ToString("X") (huruf besar, tanpa padding nol).
/// Selaras dgn core:protocol Kotlin (FrameCodec.kt).
public enum FrameCodec {
    public static let lineEnd = "\r\n"

    /// Susun satu frame TX. `param` boleh multi-field dengan pemisah ';' (sudah termasuk).
    public static func buildLine(command: Int, param: String = "") -> String {
        let hex = String(command, radix: 16, uppercase: true) // tanpa padding, spt "X"
        return param.isEmpty ? hex + lineEnd : "\(hex);\(param)" + lineEnd
    }

    /// Frame literal mentah (mis. pola provisioning "#;8080;...") tanpa interpretasi opcode.
    public static func buildRawLine(_ payload: String) -> String {
        payload + lineEnd
    }

    /// Byte ASCII untuk ditulis ke transport (konten frame dijamin ASCII).
    public static func ascii(_ line: String) -> [UInt8] {
        Array(line.utf8)
    }
}

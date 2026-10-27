#if os(iOS)
import SwiftUI

// Token design system NomadKit — sumber: docs/design/nomadkit.md (DESIGN.md, MIT).
// Nilai light = token resmi; nilai dark = TURUNAN (inferensi, sama dgn Android).

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    // ---- token resmi (light) ----
    static let sand = Color(hex: 0xD4A373)           // Primary Sand — aksi utama
    static let ocean = Color(hex: 0x0891B2)          // Secondary Ocean
    static let forest = Color(hex: 0x166534)         // Tertiary Forest — success
    static let nomadBackground = Color(hex: 0xFFFDF7)
    static let warningAmber = Color(hex: 0xCA8A04)
    static let errorRed = Color(hex: 0xDC2626)
    static let onSand = Color(hex: 0x2A1D0F)         // teks di atas Sand (kontras)
    static let onLight = Color(hex: 0x2B2419)
    static let onLightMuted = Color(hex: 0x6B6154)
    static let outlineWarm = Color(hex: 0xE4DCCF)

    // ---- dark: turunan ----
    static let darkBackground = Color(hex: 0x1C1917)
    static let darkSurface = Color(hex: 0x292524)
    static let onDark = Color(hex: 0xEDE6DB)
    static let onDarkMuted = Color(hex: 0xA8A29E)
    static let outlineDark = Color(hex: 0x44403C)
    static let oceanLight = Color(hex: 0x22B8D6)
    static let forestLight = Color(hex: 0x2E9E63)
    static let warningAmberLight = Color(hex: 0xFBBF24)
    static let errorRedLight = Color(hex: 0xF87171)
}

/// Akses warna menyesuaikan color scheme aktif (light eksak / dark turunan).
enum AppPalette {
    static func background(_ dark: Bool) -> Color { dark ? .darkBackground : .nomadBackground }
    static func surface(_ dark: Bool) -> Color { dark ? .darkSurface : .white }
    static func onSurface(_ dark: Bool) -> Color { dark ? .onDark : .onLight }
    static func onSurfaceMuted(_ dark: Bool) -> Color { dark ? .onDarkMuted : .onLightMuted }
    static func outline(_ dark: Bool) -> Color { dark ? .outlineDark : .outlineWarm }
    static func warning(_ dark: Bool) -> Color { dark ? .warningAmberLight : .warningAmber }
    static func error(_ dark: Bool) -> Color { dark ? .errorRedLight : .errorRed }
    static func success(_ dark: Bool) -> Color { dark ? .forestLight : .forest }
    static func info(_ dark: Bool) -> Color { dark ? .oceanLight : .ocean }
}
#endif


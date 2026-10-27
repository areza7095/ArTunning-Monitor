package id.arvoid.artunningmonitor.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Token design system NomadKit — sumber: docs/design/nomadkit.md (DESIGN.md, MIT).
 * Jangan ubah nilai light di sini tanpa mengubah dokumen sumbernya (monorepo:
 * docs/ = source of truth).
 */

// ---- Token resmi (light) ----
val Sand = Color(0xFFD4A373)          // Primary Sand  — aksi utama (dominan)
val Ocean = Color(0xFF0891B2)         // Secondary Ocean — aksi sekunder/link
val Forest = Color(0xFF166534)        // Tertiary Forest — success/highlight
val NomadBackground = Color(0xFFFFFDF7) // Background — latar layar
val SurfaceDefault = Color(0xFFFFFFFF)  // Surface Default — kartu/panel
val Success = Forest
val WarningAmber = Color(0xFFCA8A04)
val ErrorRed = Color(0xFFDC2626)
val InfoOcean = Ocean

// Warga turunan agar teks di atas Sand tetap kontras (aturan aksesibilitas:
// "jangan andalkan warna saja" — kontras teks juga wajib).
val OnSand = Color(0xFF2A1D0F)        // teks di atas tombol Sand
val OnLight = Color(0xFF2B2419)       // teks utama tema terang
val OnLightMuted = Color(0xFF6B6154)  // teks sekunder tema terang
val OutlineWarm = Color(0xFFE4DCCF)

// ---- Dark: TURUNAN (inferensi, bukan bagian DESIGN.md) ----
val DarkBackground = Color(0xFF1C1917)   // background digelapkan (sand-tone charcoal)
val DarkSurface = Color(0xFF292524)      // surface digelapkan
val OnDark = Color(0xFFEDE6DB)           // teks utama tema gelap
val OnDarkMuted = Color(0xFFA8A29E)
val OutlineDark = Color(0xFF44403C)
val OceanLight = Color(0xFF22B8D6)       // Ocean disesuaikan agar kontras di bg gelap
val ForestLight = Color(0xFF2E9E63)      // Forest disesuaikan (success di bg gelap)
val WarningAmberLight = Color(0xFFFBBF24)
val ErrorRedLight = Color(0xFFF87171)

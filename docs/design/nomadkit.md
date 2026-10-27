# NomadKit Design System

> Sumber: https://designmd.ai/chef/nomadkit — DESIGN.md v1, License MIT
> Diunduh via endpoint resmi `https://designmd.ai/api/v1/kits/chef/nomadkit/download` (2026-10-27).
> Token design system bersama untuk seluruh target di monorepo ini (Android, dan nanti ESP/web).

## Overview

NomadKit is a free-spirited, light-packed, and essential design system for digital nomad tools and backpacker community platforms. It embraces minimalism and portability, using warm sand tones balanced by ocean and forest accents.

## Colors

- **Primary Sand** (#D4A373): Primary actions, key accents
- **Secondary Ocean** (#0891B2): Links, secondary actions
- **Tertiary Forest** (#166534): Success states, highlights
- **Background** (#FFFDF7): Page background
- **Surface Default** (#FFFFFF): Card backgrounds
- **Success** (#166534)
- **Warning** (#CA8A04)
- **Error** (#DC2626)
- **Info** (#0891B2)

## Do's and Don'ts
- Do use Sand as the dominant primary action color
- Don't rely on color alone to convey meaning
- Do design for offline-first with skeleton states

---

## Pemetaan ke implementasi Android (ArTunning Monitor)

| Token | Hex | Pemakaian |
|---|---|---|
| Primary Sand | `#D4A373` | Aksi utama: tombol **Connect**, tombol kirim frame (console) |
| Secondary Ocean | `#0891B2` | Aksi sekunder: Scan, Disconnect, tab indikator |
| Tertiary Forest | `#166534` | Status sukses koneksi (selalu + ikon/teks) |
| Background | `#FFFDF7` | Latar layar |
| Surface Default | `#FFFFFF` | Kartu/panel (gauge, konsol) |
| Success | `#166534` | RX OK / streaming aktif |
| Warning | `#CA8A04` | Reconnecting / retry |
| Error | `#DC2626` | Gagal koneksi, link lost |
| Info | `#0891B2` | Log TX di konsol |

- **Light theme**: token persis seperti di atas (satu-satunya token resmi dalam file ini).
- **Dark theme**: *turunan* (inferensi kita, bukan bagian dari DESIGN.md) — surface/ background
  digelapkan, token makna dipertahankan. Tandai sebagai derived di kode.
- Level type **Headline / Body / Label** → Material3 Typography (`headlineMedium`,
  `bodyMedium`, `labelMedium`); font family default (DESIGN.md tidak menyebut font).
- Don't "rely on color alone": setiap status selalu membawa teks + ikon.
- "Offline-first with skeleton states": skeleton saat menunggu handshake/stream pertama.

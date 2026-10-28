# Dokumentasi

[English](README.md) · **Bahasa Indonesia**

Dokumentasi **ArTunning Monitor**. English adalah default; pilih folder/versi Bahasa Indonesia
saat kamu membutuhkannya.

| Dokumen | English | Bahasa Indonesia |
|---|---|---|
| Spesifikasi protokol (ECU Juken 5 Plus) | [`en/juken5-protocol-spec.md`](en/juken5-protocol-spec.md) | [`id/juken5-protocol-spec.md`](id/juken5-protocol-spec.md) |
| Build & install (Windows → Android / iPhone) | [`en/install.md`](en/install.md) | [`id/install.md`](id/install.md) |

## File bersama / netral-bahasa

| File | Fungsi |
|---|---|
| [`opcodes.json`](opcodes.json) | Opcode mesin-readable, layout struct, timing, transport — **source of truth untuk codegen** (`Opcodes.kt`, `Opcodes.swift`) |
| [`design/nomadkit.md`](design/nomadkit.md) | Token design system NomadKit (DESIGN.md, MIT) |
| `media/` | Screenshot (Connect / Live / Console) |

**Konvensi:** `docs/` = source of truth — kode di-*generate*/di-port dari dokumen ini,
bukan sebaliknya. Pasangan translasi ada di `en/` dan `id/`; jaga keduanya tetap sinkron
saat kamu mengedit salah satu bahasa.

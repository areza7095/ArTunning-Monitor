# Docs

**English** · [Bahasa Indonesia](README.id.md)

Documentation for **ArTunning Monitor**. English is the default; pick the Bahasa Indonesia
folder/version when you need it.

| Document | English | Bahasa Indonesia |
|---|---|---|
| Protocol specification (ECU Juken 5 Plus) | [`en/juken5-protocol-spec.md`](en/juken5-protocol-spec.md) | [`id/juken5-protocol-spec.md`](id/juken5-protocol-spec.md) |
| Build & install (Windows → Android / iPhone) | [`en/install.md`](en/install.md) | [`id/install.md`](id/install.md) |

## Shared / language-neutral files

| File | Purpose |
|---|---|
| [`opcodes.json`](opcodes.json) | Machine-readable opcodes, struct layouts, timing, transport — **source of truth for codegen** (`Opcodes.kt`, `Opcodes.swift`) |
| [`design/nomadkit.md`](design/nomadkit.md) | NomadKit design system tokens (DESIGN.md, MIT) |
| `media/` | Screenshots (Connect / Live / Console) |

**Conventions:** `docs/` is the source of truth — code is generated/ported from these
documents, not the other way around. Translation pairs live in `en/` and `id/`; keep both
in sync when you edit either language.

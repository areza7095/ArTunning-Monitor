#if os(iOS)
import SwiftUI

// Komponen bersama iOS. Penerapan aturan NomadKit:
//  - setiap status SELALU punya teks + ikon (jangan andalkan warna saja)
//  - skeleton state untuk masa tunggu (offline-first)

/// Baris status: ikon + teks utama + sub-teks.
struct StatusCard: View {
    @EnvironmentObject private var vm: AppViewModel
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool { scheme == .dark }

    private var icon: String {
        switch vm.status {
        case .disconnected: "wifi.slash"
        case .connecting: "arrow.triangle.2.circlepath"
        case .handshaking: "arrow.triangle.2.circlepath"
        case .liveRequested: "hourglass"
        case .streaming: "checkmark.circle.fill"
        case .reconnecting: "arrow.clockwise"
        case .error: "exclamationmark.triangle.fill"
        }
    }

    private var title: String {
        switch vm.status {
        case .disconnected: "Terputus"
        case .connecting: "Menghubungkan…"
        case .handshaking: "Handshake (kirim 1B00)…"
        case .liveRequested: "Meminta mode live (160A)…"
        case .streaming: "Live streaming"
        case .reconnecting: "Reconnecting…"
        case .error: "Error"
        }
    }

    private var tint: Color {
        switch vm.status {
        case .disconnected: AppPalette.onSurfaceMuted(dark)
        case .connecting, .handshaking: AppPalette.info(dark)
        case .liveRequested, .reconnecting: AppPalette.warning(dark)
        case .streaming: AppPalette.success(dark)
        case .error: AppPalette.error(dark)
        }
    }

    private var sub: String {
        if let e = vm.errorMessage { return e }
        return "\(vm.host):\(vm.portText)"
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(tint)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text("Status")
                    .font(.caption)
                    .foregroundStyle(AppPalette.onSurfaceMuted(dark))
                Text(title)
                    .font(.headline)
                    .foregroundStyle(AppPalette.onSurface(dark))
                Text(sub)
                    .font(.subheadline)
                    .foregroundStyle(AppPalette.onSurfaceMuted(dark))
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppPalette.surface(dark))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(tint.opacity(0.45), lineWidth: 1)
        )
        .accessibilityLabel("Status koneksi")
    }
}

/// Placeholder berdenyut — aturan "design for offline-first with skeleton states".
struct SkeletonBox: View {
    @State private var on = false

    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.gray.opacity(on ? 0.55 : 0.2))
            .frame(height: 76)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                    on = true
                }
            }
            .accessibilityHidden(true)
    }
}

/// Kartu metrik: label + nilai + satuan (nilai selalu diberi LABEL teks — bukan warna saja).
struct MetricCard: View {
    let title: String
    let value: String
    let unit: String
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool { scheme == .dark }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppPalette.onSurfaceMuted(dark))
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value)
                    .font(.title)
                    .fontWeight(.semibold)
                    .foregroundStyle(AppPalette.onSurface(dark))
                Text(unit)
                    .font(.subheadline)
                    .foregroundStyle(AppPalette.onSurfaceMuted(dark))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(AppPalette.surface(dark))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppPalette.outline(dark), lineWidth: 1)
        )
    }
}
#endif


#if os(iOS)
import Foundation
import SwiftUI

private struct Metric: Identifiable {
    let id = UUID()
    let title: String
    let value: String
    let unit: String
}

/// Tab Live — grid gauge identik dgn Android (skala dari spec bagian9).
struct LiveScreen: View {
    @EnvironmentObject private var vm: AppViewModel
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool { scheme == .dark }
    private var liveActive: Bool {
        vm.status == .liveRequested || vm.status == .streaming
    }

    private var metrics: [Metric] {
        guard let t = vm.telemetry else { return [] }
        return [
            Metric(title: "RPM", value: String(format: "%.0f", t.rpm), unit: "rpm"),
            Metric(title: "TPS", value: String(format: "%.0f", t.tpsPercent), unit: "%"),
            Metric(title: "AFR", value: String(format: "%.2f", t.afrRaw), unit: "λ"),
            Metric(title: "EOT", value: String(format: "%.1f", t.eotCelsius), unit: "°C"),
            Metric(title: "MAP", value: String(format: "%.1f", t.mapValue), unit: "kPa"),
            Metric(title: "IAT", value: String(format: "%.1f", t.iatCelsius), unit: "°C"),
            Metric(title: "BATT", value: String(format: "%.2f", t.batteryVolt), unit: "V"),
            Metric(title: "INJ", value: String(format: "%.3f", t.injAmps), unit: "A"),
        ]
    }

    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                StatusCard()

                // aksi utama (Sand) / stop (outline)
                if liveActive {
                    Button {
                        vm.stopLive()
                    } label: {
                        HStack {
                            Image(systemName: "stop.fill")
                            Text("Stop Live")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                } else {
                    Button {
                        vm.startLive()
                    } label: {
                        HStack {
                            Image(systemName: "play.fill")
                            Text("Start Live")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.sand)
                    .foregroundStyle(Color.onSand)
                    .disabled(vm.status != .handshaking)
                }

                if let t = vm.telemetry {
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(metrics) { m in
                            MetricCard(title: m.title, value: m.value, unit: m.unit)
                        }
                    }

                    Text("ECU: \(vm.host)  •  core \(t.coreId)")
                        .font(.caption)
                        .foregroundStyle(AppPalette.onSurfaceMuted(dark))
                } else if liveActive {
                    Text("Menunggu data dari ECU…")
                        .font(.subheadline)
                        .foregroundStyle(AppPalette.onSurfaceMuted(dark))
                    SkeletonBox()
                    SkeletonBox()
                    SkeletonBox()
                } else {
                    Text("Live belum aktif — tekan Start Live")
                        .font(.subheadline)
                        .foregroundStyle(AppPalette.onSurfaceMuted(dark))
                }
            }
            .padding(16)
        }
        .background(AppPalette.background(dark))
    }
}
#endif


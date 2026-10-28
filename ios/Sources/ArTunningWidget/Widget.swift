#if os(iOS)
import SwiftUI
import WidgetKit
import UIKit
import ArTunningMonitor

/// Widget home screen telemetry — baca TelemetryStore (App Group) + WidgetKit.
/// App menulis snapshot + reloadTimelines; widget tidak membuka koneksi sendiri.
@main
struct ArTunningWidgetBundle: WidgetBundle {
    var body: some Widget {
        TelemetryWidget()
    }
}

struct TelemetryWidget: Widget {
    let kind = "TelemetryWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TelemetryProvider()) { entry in
            TelemetryWidgetView(entry: entry)
                .containerBackgroundCompat()
        }
        .configurationDisplayName("Telemetri ECU")
        .description("RPM, TPS, AFR, dll — mengikuti koneksi aplikasi.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// ------------------------------------------------------------------ entry

struct TelemetryEntry: TimelineEntry {
    let date: Date
    let snapshot: TelemetrySnapshot?
    let metrics: [MetricKey]
    let ageSeconds: Int64?
}

struct TelemetryProvider: TimelineProvider {
    func placeholder(in context: Context) -> TelemetryEntry {
        let sample = TelemetrySample(
            tpsPercent: 12, batteryVolt: 12.6, coreId: 0, rpm: 3450,
            eotRaw: 852, afrRaw: 12.5, mapRaw: 1013, iatRaw: 345,
            pumpRaw: 1520, injRaw: 890
        )
        return TelemetryEntry(
            date: .now,
            snapshot: TelemetrySnapshot(
                status: .streaming,
                telemetry: sample,
                telemetryAtMs: Int64(Date().timeIntervalSince1970 * 1000)
            ),
            metrics: MetricKey.default,
            ageSeconds: 0
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (TelemetryEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TelemetryEntry>) -> Void) {
        // App yang push via reloadTimelines; timeline refresh safety ~5 dtk.
        let entry = loadEntry()
        let refresh = Calendar.current.date(byAdding: .second, value: 5, to: .now) ?? .now
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func loadEntry() -> TelemetryEntry {
        let store = TelemetryStore()
        return TelemetryEntry(
            date: .now,
            snapshot: store.loadSnapshot(),
            metrics: store.loadMetrics(),
            ageSeconds: store.ageSeconds()
        )
    }
}

// ------------------------------------------------------------------ view

private struct W {
    static let sand = Color(red: 0xD4 / 255, green: 0xA3 / 255, blue: 0x73 / 255)
    static let onSand = Color(red: 0x2A / 255, green: 0x1D / 255, blue: 0x0F / 255)
    static let forest = Color(red: 0x16 / 255, green: 0x65 / 255, blue: 0x34 / 255)
    static let muted = Color(red: 0x6B / 255, green: 0x61 / 255, blue: 0x54 / 255)
}

struct TelemetryWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TelemetryEntry

    private var statusText: String {
        switch entry.snapshot?.status {
        case .streaming: "LIVE"
        case .connecting, .handshaking, .liveRequested, .reconnecting: "…"
        case .permissionNeeded: "IZIN"
        case .error: "ERR"
        case .disconnected, nil: "OFF"
        }
    }

    private var isLive: Bool {
        entry.snapshot?.status == .streaming && entry.snapshot?.telemetry != nil
    }

    private var maxMetrics: Int {
        family == .systemSmall ? 2 : 4
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("ArTunning")
                    .font(.caption.bold())
                    .foregroundStyle(W.onSand)
                Spacer()
                Text(statusText)
                    .font(.caption.bold())
                    .foregroundStyle(isLive ? W.forest : W.onSand)
            }
            .padding(6)
            .background(W.sand)

            if let snap = entry.snapshot, let t = snap.telemetry {
                metricGrid(t)
            } else {
                Text("belum ada data - buka app")
                    .font(.caption2)
                    .foregroundStyle(W.muted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            if let age = entry.ageSeconds {
                Text("update \(age)s lalu")
                    .font(.system(size: 9))
                    .foregroundStyle(W.muted)
            }
        }
    }

    @ViewBuilder
    private func metricGrid(_ t: TelemetrySample) -> some View {
        let cols = Array(entry.metrics.prefix(maxMetrics)).chunked(2)
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(cols.enumerated()), id: \.offset) { _, row in
                HStack(alignment: .top) {
                    ForEach(row, id: \.self) { key in
                        VStack(alignment: .leading, spacing: 1) {
                            Text(MetricFormatter.value(t, key))
                                .font(.callout.bold().monospacedDigit())
                            Text(key.label)
                                .font(.system(size: 9))
                                .foregroundStyle(W.muted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(.horizontal, 8)
        .padding(.bottom, 6)
    }
}

// containerBackground iOS 17+; fallback iOS 15/16
private extension View {
    @ViewBuilder
    func containerBackgroundCompat() -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(.fill, for: .widget)
        } else {
            background(Color(.systemBackground))
        }
    }
}

private extension Array {
    func chunked(_ n: Int) -> [[Element]] {
        guard n > 0 else { return [self] }
        var out: [[Element]] = []
        var i = 0
        while i < count {
            out.append(Array(self[i..<min(i + n, count)]))
            i += n
        }
        return out
    }
}
#endif // os(iOS)

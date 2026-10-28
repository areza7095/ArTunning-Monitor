import Foundation

/// Kunci metrik telemetri — mirror MetricKey Android (core/protocol Metrics.kt).
/// Dipakai konfigurasi widget iOS + formatter seragam.
public enum MetricKey: String, CaseIterable, Codable, Sendable {
    case rpm, tps, afr, eot, map, iat, batt, pump, inj

    public var label: String {
        switch self {
        case .rpm: "RPM"
        case .tps: "TPS"
        case .afr: "AFR"
        case .eot: "EOT"
        case .map: "MAP"
        case .iat: "IAT"
        case .batt: "BATT"
        case .pump: "PUMP"
        case .inj: "INJ"
        }
    }

    public var unit: String {
        switch self {
        case .rpm: "rpm"
        case .tps: "%"
        case .afr: ""
        case .eot: "°C"
        case .map: "kPa"
        case .iat: "°C"
        case .batt: "V"
        case .pump: "A"
        case .inj: "A"
        }
    }

    public static let `default`: [MetricKey] = [.rpm, .tps, .afr, .eot]
}

/// Set metrik terpilih + serialisasi (format sama dgn Android: "rpm,tps,afr").
public enum MetricSet {
    public static func serialize(_ metrics: [MetricKey]) -> String {
        metrics.map(\.rawValue).joined(separator: ",")
    }

    public static func deserialize(_ raw: String?) -> [MetricKey] {
        guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else {
            return MetricKey.default
        }
        var seen = Set<MetricKey>()
        var out: [MetricKey] = []
        for part in raw.split(separator: ",") {
            if let k = MetricKey(rawValue: part.trimmingCharacters(in: .whitespaces)),
               seen.insert(k).inserted {
                out.append(k)
            }
        }
        return out.isEmpty ? MetricKey.default : out
    }
}

/// Formatter angka — Locale.ROOT, pemisah '.', presisi sama dgn gauge Android.
public enum MetricFormatter {
    public static func value(_ sample: TelemetrySample, _ key: MetricKey) -> String {
        switch key {
        case .rpm: String(format: "%.0f", sample.rpm)
        case .tps: String(format: "%.0f", sample.tpsPercent)
        case .afr: String(format: "%.2f", sample.afrRaw)
        case .eot: String(format: "%.1f", sample.eotCelsius)
        case .map: String(format: "%.1f", sample.mapValue)
        case .iat: String(format: "%.1f", sample.iatCelsius)
        case .batt: String(format: "%.2f", sample.batteryVolt)
        case .pump: String(format: "%.3f", sample.pumpAmps)
        case .inj: String(format: "%.3f", sample.injAmps)
        }
    }

    public static func summary(_ sample: TelemetrySample, metrics: [MetricKey]) -> String {
        metrics.map { m -> String in
            let v = value(sample, m)
            return m.unit.isEmpty ? "\(m.label) \(v)" : "\(v) \(m.unit)"
        }.joined(separator: " · ")
    }
}

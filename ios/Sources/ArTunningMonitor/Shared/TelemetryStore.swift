import Foundation

/// Status koneksi untuk snapshot widget (enum ringkas, Codable).
public enum WidgetConnStatus: String, Codable, Sendable, Equatable {
    case disconnected
    case connecting
    case handshaking
    case liveRequested
    case streaming
    case reconnecting
    case permissionNeeded
    case error
}

/// Snapshot telemetri untuk dibagikan app -> widget via App Group UserDefaults.
public struct TelemetrySnapshot: Codable, Equatable, Sendable {
    public let status: WidgetConnStatus
    public let telemetry: TelemetrySample?
    public let telemetryAtMs: Int64
    public let deviceName: String?
    public let error: String?

    public init(
        status: WidgetConnStatus,
        telemetry: TelemetrySample? = nil,
        telemetryAtMs: Int64 = 0,
        deviceName: String? = nil,
        error: String? = nil
    ) {
        self.status = status
        self.telemetry = telemetry
        self.telemetryAtMs = telemetryAtMs
        self.deviceName = deviceName
        self.error = error
    }
}

/// Penyimpanan snapshot + pilihan metrik di App Group UserDefaults.
///
/// App menulis (throttle ~1 dtk) + `WidgetCenter.reloadTimelines`; widget membaca.
/// App Group ID = [TelemetryStore.appGroupID] — entitlement wajib di app & widget.
/// Fallback bila App Group unavailable: suiteName nil -> UserDefaults.standard
/// (widget tidak bisa membaca; hanya app yang pakai — test host tetap jalan).
public final class TelemetryStore: @unchecked Sendable {

    public static let appGroupID = "group.id.arvoid.artunningmonitor"
    private static let snapshotKey = "telemetry.snapshot"
    private static let metricsKey = "telemetry.metrics"

    public let defaults: UserDefaults

    /// - Parameter suiteName: App Group suite; nil = standard (test/non-entitlement).
    public init(suiteName: String? = TelemetryStore.appGroupID) {
        if let suiteName, let suite = UserDefaults(suiteName: suiteName) {
            defaults = suite
        } else {
            defaults = .standard
        }
    }

    // ---------------------------------------------------------------- tulis

    public func saveSnapshot(_ snap: TelemetrySnapshot) {
        guard let data = try? JSONEncoder().encode(snap) else { return }
        defaults.set(data, forKey: Self.snapshotKey)
    }

    public func saveMetrics(_ metrics: [MetricKey]) {
        defaults.set(MetricSet.serialize(metrics), forKey: Self.metricsKey)
    }

    // ---------------------------------------------------------------- baca

    public func loadSnapshot() -> TelemetrySnapshot? {
        guard let data = defaults.data(forKey: Self.snapshotKey) else { return nil }
        return try? JSONDecoder().decode(TelemetrySnapshot.self, from: data)
    }

    public func loadMetrics() -> [MetricKey] {
        MetricSet.deserialize(defaults.string(forKey: Self.metricsKey))
    }

    // ---------------------------------------------------------------- util

    /// Hapus snapshot + metrics (dipakai test / reset).
    public func clear() {
        defaults.removeObject(forKey: Self.snapshotKey)
        defaults.removeObject(forKey: Self.metricsKey)
    }

    /// Staleness dalam detik sejak telemetryAtMs; nil bila belum ada data.
    public func ageSeconds(nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Int64? {
        guard let snap = loadSnapshot(), snap.telemetryAtMs > 0 else { return nil }
        return max(0, (nowMs - snap.telemetryAtMs) / 1000)
    }
}

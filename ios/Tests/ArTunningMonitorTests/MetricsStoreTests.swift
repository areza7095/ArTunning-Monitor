import XCTest
@testable import ArTunningMonitor

/// Test MetricKey/MetricSet/MetricFormatter + TelemetryStore (App Group UserDefaults).
/// Mirror MetricsTest Android + store encode/decode.
final class MetricsStoreTests: XCTestCase {

    private let sample = TelemetryParser.parse(
        "A603;12;12.6;0;3450;852;12.5;1013;345;1520;890"
    )!

    // MARK: - MetricSet

    func testSerializeDeserializeRoundtripPertahankanUrutan() {
        let set: [MetricKey] = [.rpm, .map, .inj, .afr]
        XCTAssertEqual(MetricSet.serialize(set), "rpm,map,inj,afr")
        XCTAssertEqual(MetricSet.deserialize(MetricSet.serialize(set)), set)
    }

    func testDeserializeKosongAtauNilPakaiDefault() {
        XCTAssertEqual(MetricSet.deserialize(nil), MetricKey.default)
        XCTAssertEqual(MetricSet.deserialize(""), MetricKey.default)
        XCTAssertEqual(MetricSet.deserialize("   "), MetricKey.default)
    }

    func testDeserializeMembuangIdTakDikenalDanDuplikat() {
        let out = MetricSet.deserialize("rpm,bogus,tps,rpm,iat")
        XCTAssertEqual(out, [.rpm, .tps, .iat])
        XCTAssertEqual(MetricSet.deserialize("bogus,xyz"), MetricKey.default)
    }

    // MARK: - MetricFormatter

    func testFormatNilaiSesuaiSpesifikasiGauge() {
        XCTAssertEqual(MetricFormatter.value(sample, .rpm), "3450")
        XCTAssertEqual(MetricFormatter.value(sample, .tps), "12")
        XCTAssertEqual(MetricFormatter.value(sample, .afr), "12.50")
        XCTAssertEqual(MetricFormatter.value(sample, .eot), "85.2")
        XCTAssertEqual(MetricFormatter.value(sample, .map), "101.3")
        XCTAssertEqual(MetricFormatter.value(sample, .iat), "34.5")
        XCTAssertEqual(MetricFormatter.value(sample, .batt), "12.60")
        XCTAssertEqual(MetricFormatter.value(sample, .pump), "1.520")
        XCTAssertEqual(MetricFormatter.value(sample, .inj), "0.890")
    }

    func testSummaryHanyaMemuatMetrikTerpilih() {
        let s = MetricFormatter.summary(sample, metrics: [.rpm, .afr, .map])
        XCTAssertEqual(s, "3450 rpm · AFR 12.50 · 101.3 kPa")
        XCTAssertFalse(s.contains("EOT"))
        XCTAssertFalse(s.contains("85.2"))
    }

    // MARK: - TelemetryStore (suiteName unik; fallback standard dibersihkan dulu)

    /// Store terisolasi: suite unik bila tersedia; clear dulu bila fallback standard.
    private func makeStore() -> TelemetryStore {
        let store = TelemetryStore(suiteName: "test.metricsstore.\(UUID().uuidString)")
        store.clear()
        return store
    }

    func testSnapshotRoundtrip() {
        let store = makeStore()
        let snap = TelemetrySnapshot(
            status: .streaming,
            telemetry: sample,
            telemetryAtMs: 1_700_000_000_000,
            deviceName: "192.168.2.6",
            error: nil
        )
        store.saveSnapshot(snap)
        XCTAssertEqual(store.loadSnapshot(), snap)
    }

    func testSnapshotTanpaTelemetry() {
        let store = makeStore()
        let snap = TelemetrySnapshot(status: .disconnected)
        store.saveSnapshot(snap)
        let loaded = store.loadSnapshot()
        XCTAssertEqual(loaded?.status, .disconnected)
        XCTAssertNil(loaded?.telemetry)
        XCTAssertEqual(loaded?.telemetryAtMs, 0)
    }

    func testMetricsPersist() {
        let store = makeStore()
        store.saveMetrics([.rpm, .eot, .batt])
        XCTAssertEqual(store.loadMetrics(), [.rpm, .eot, .batt])
    }

    func testAgeSeconds() {
        let store = makeStore()
        let now: Int64 = 1_700_000_000_000
        store.saveSnapshot(TelemetrySnapshot(
            status: .streaming,
            telemetry: sample,
            telemetryAtMs: now - 5_000
        ))
        XCTAssertEqual(store.ageSeconds(nowMs: now), 5)
    }

    func testAgeSecondsNilTanpaData() {
        let store = makeStore()
        XCTAssertNil(store.ageSeconds())
    }
}

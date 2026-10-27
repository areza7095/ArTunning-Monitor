import Foundation

/// Satu frame telemetri/status `A603` (spec bagian 9) — port TelemetryParser.kt.
///
/// Layout: A603;<TPS>;<BAT>;<CORE>;<RPM>;<EOTx10>;<AFR>;<MAPx10>;<IATx10>;<PUMPx1000>;<INJx1000>
/// Nilai masih mentah (raw); properti turunan menerapkan skala.
public struct TelemetrySample: Equatable {
    public let tpsPercent: Double
    public let batteryVolt: Double
    public let coreId: Int
    public let rpm: Double
    public let eotRaw: Double
    public let afrRaw: Double
    public let mapRaw: Double
    public let iatRaw: Double
    public let pumpRaw: Double
    public let injRaw: Double

    public var eotCelsius: Double { eotRaw / 10 }
    public var mapValue: Double { mapRaw / 10 }
    public var iatCelsius: Double { iatRaw / 10 }
    public var pumpAmps: Double { pumpRaw / 1000 }
    public var injAmps: Double { injRaw / 1000 }

    /// Band warna AFR aplikasi: (int)AFR - 9 (biru<=2, hijau=3, kuning=4, merah>4).
    /// Int() memotong ke bawah, sama dgn conv.i4 pada IL.
    public var afrBandIndex: Int { Int(afrRaw) - 9 }
}

public enum TelemetryParser {
    public static let minParts = 11

    /// Parse baris; nil bila bukan frame A603 / tidak lengkap.
    /// Angka memakai pemisah desimal '.' (InvariantCulture — Double() default invariant).
    public static func parse(_ line: String) -> TelemetrySample? {
        let r = ReplyDispatch.parse(line)
        guard r.isTelemetry, r.parts.count >= minParts else { return nil }

        func f(_ i: Int) -> Double? { Double(r.parts[i]) }
        guard
            let tps = f(1),
            let batt = f(2),
            let core = Int(r.parts[3]),
            let rpm = f(4),
            let eot = f(5),
            let afr = f(6),
            let map = f(7),
            let iat = f(8),
            let pump = f(9),
            let inj = f(10)
        else { return nil }

        return TelemetrySample(
            tpsPercent: tps,
            batteryVolt: batt,
            coreId: core,
            rpm: rpm,
            eotRaw: eot,
            afrRaw: afr,
            mapRaw: map,
            iatRaw: iat,
            pumpRaw: pump,
            injRaw: inj
        )
    }

    /// Binning kolom RPM (Form1.SetText IL_0A22-IL_0A9A):
    /// clamp1000..16000, q = roundBanker(rpm*4/1000)*1000/4 (kelipatan250),
    /// kolom = (q-1000)/250.
    public static func rpmColumn(_ rpm: Double) -> Int {
        let clamped = min(max(rpm, 1000), 16000)
        let q = Int(roundBanker(clamped * 4.0 / 1000.0)) * 1000 / 4
        return (q - 1000) / 250
    }

    /// Binning kolom peta pengapian: (q-1000)/500.
    public static func rpmColumnIgn(_ rpm: Double) -> Int {
        let clamped = min(max(rpm, 1000), 16000)
        let q = Int(roundBanker(clamped * 4.0 / 1000.0)) * 1000 / 4
        return (q - 1000) / 500
    }

    /// Binning baris TPS (Form1.SetText IL_0A9F-IL_0B47):
    /// [1,2] -> 1 ; [3,94] -> floor(V+5)/5 dgn V = floor(tps*2/10)*10/2 ;
    /// >=95 -> 20 ; selain itu 0.
    public static func tpsRow(_ tps: Double) -> Int {
        if tps >= 1, tps <= 2 { return 1 }
        if tps >= 3, tps <= 94 {
            let v = (floor(tps * 2.0 / 10.0) * 10.0) / 2.0
            return Int(floor(v + 5.0)) / 5
        }
        if tps >= 95 { return 20 }
        return 0
    }

    /// Math.Round(.NET) = pembulatan ke genap terdekat (banker's rounding).
    static func roundBanker(_ x: Double) -> Double {
        let f = floor(x)
        let frac = x - f
        if frac > 0.5 { return f + 1 }
        if frac < 0.5 { return f }
        return f.truncatingRemainder(dividingBy: 2.0) == 0 ? f : f + 1
    }
}

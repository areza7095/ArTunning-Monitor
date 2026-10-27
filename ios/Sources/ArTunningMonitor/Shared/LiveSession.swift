import Foundation

public enum SessionState {
    case idle
    case handshaking
    case liveRequested
    case streaming
}

/// State machine sesi koneksi/live ECU (spec bagian6) — port persis LiveSession.kt.
///
/// Perilaku dari bukti IL app desktop:
///  - handshake: kirim "1B00" (keepalive3000 ms; Form1.lanjut_echo/ulang_echo_Tick)
///  - balasan "1B01" = FEEDBACK_ECHO_ACK -> reset watchdog (SetText IL_06A3->IL_0E48)
///  - watchdog: tanpa "1B01" selama17000 ms -> linkLost (echo1700 ms x10);
///    di-suspend saat streaming (echo_Tick IL_000F: isLiveState -> ret)
///  - masuk live: "1617" lalu "160A" tiap500 ms maks25x
///  - frame "A603" pertama saat liveRequested -> streaming
///  - keluar live: "160B"
///
/// Penyederhanaan (sama dgn versi Kotlin): timer "160A" dijadwalkan langsung
/// 500 ms setelah "1617" (urutan identik jalur reconnect desktop);
/// pembanding core (mapping_live) tidak dipakai — frame A603 pertama diterima.
///
/// AUTO-LIVE (UX): setelah koneksi terbuka, sesi masuk live OTOMATIS —
/// pemicu hybrid = balasan "1B01" pertama ATAU fallback `autoLiveFallbackMs`
/// sejak connect. stopLive() mematikan autoLive sampai connect berikutnya;
/// requestLive() (tombol Start) selalu bisa kapan pun.
public final class LiveSession {
    private let keepaliveIntervalMs: Int64
    private let watchdogTimeoutMs: Int64
    private let liveRetryIntervalMs: Int64
    private let liveRetryMax: Int
    private let autoLiveFallbackMs: Int64

    public private(set) var state: SessionState = .idle
    public private(set) var connected = false

    /// Latch "link lost" — direset saat balasan echo (1B01) / koneksi baru.
    public private(set) var linkLost = false
    public private(set) var liveTicksSent = 0

    /// Otomatis masuk live setelah handshake/fallback; dimatikan oleh stopLive().
    public var autoLive = true

    private var connectedAtMs: Int64 = 0
    private var lastRxMs: Int64 = 0
    private var lastEchoReplyMs: Int64 = 0
    private var lastPingMs: Int64 = 0
    private var lastLiveTxMs: Int64 = 0

    public init(
        keepaliveIntervalMs: Int64 = 3000,
        watchdogTimeoutMs: Int64 = 17000,
        liveRetryIntervalMs: Int64 = 500,
        liveRetryMax: Int = 25,
        autoLiveFallbackMs: Int64 = 1500
    ) {
        self.keepaliveIntervalMs = keepaliveIntervalMs
        self.watchdogTimeoutMs = watchdogTimeoutMs
        self.liveRetryIntervalMs = liveRetryIntervalMs
        self.liveRetryMax = liveRetryMax
        self.autoLiveFallbackMs = autoLiveFallbackMs
    }

    /// Koneksi transport terbuka: hasilkan frame handshake pertama.
    public func onConnected(nowMs: Int64) -> [String] {
        connected = true
        state = .handshaking
        lastRxMs = nowMs
        lastEchoReplyMs = nowMs
        lastPingMs = nowMs
        lastLiveTxMs = nowMs
        liveTicksSent = 0
        linkLost = false
        connectedAtMs = nowMs
        autoLive = true // sesi baru -> auto-live kembali aktif
        return [ping()]
    }

    public func onDisconnected() {
        connected = false
        state = .idle
        liveTicksSent = 0
        linkLost = false
    }

    /// Setiap baris RX: perbarui watchdog, proses balasan relevan, hasilkan TX tambahan.
    public func onRxLine(_ line: String, nowMs: Int64) -> [String] {
        guard connected else { return [] }
        lastRxMs = nowMs
        let reply = ReplyDispatch.parse(line)
        if reply.isEchoAck {
            lastEchoReplyMs = nowMs
            linkLost = false
            // AUTO-LIVE jalur1: handshake oke -> langsung masuk live.
            if autoLive, state == .handshaking {
                return enterLive(nowMs: nowMs)
            }
            return []
        }
        if reply.isTelemetry, state == .liveRequested {
            state = .streaming
            liveTicksSent = 0
        }
        return []
    }

    /// Permintaan masuk mode live: kirim PC_READ_USER_DEF_2 (1617).
    public func requestLive(nowMs: Int64) -> [String] {
        guard connected, state != .liveRequested else { return [] }
        return enterLive(nowMs: nowMs)
    }

    /// Keluar mode live: kirim EXIT_LIVE (160B) + matikan auto-live untuk sesi ini.
    public func stopLive(nowMs: Int64) -> [String] {
        guard connected, state != .idle else { return [] }
        autoLive = false // manual sampai connect/reconnect berikutnya
        state = .handshaking
        liveTicksSent = 0
        lastLiveTxMs = nowMs
        return [FrameCodec.buildLine(command: Opcodes.Read.EXIT_LIVE)]
    }

    private func enterLive(nowMs: Int64) -> [String] {
        state = .liveRequested
        liveTicksSent = 0
        lastLiveTxMs = nowMs
        return [FrameCodec.buildLine(command: Opcodes.Read.PC_READ_USER_DEF_2)]
    }

    /// Dipanggil periodik (~250 ms):
    ///  - keepalive "1B00" tiap keepaliveIntervalMs
    ///  - retry "160A" tiap liveRetryIntervalMs selama liveRequested (maks liveRetryMax)
    ///  - watchdog -> linkLost (di-suspend selama streaming)
    public func tick(nowMs: Int64) -> [String] {
        guard connected, state != .idle else { return [] }
        var tx: [String] = []

        if state != .streaming, nowMs - lastEchoReplyMs > watchdogTimeoutMs {
            linkLost = true
        }

        if nowMs - lastPingMs >= keepaliveIntervalMs {
            lastPingMs = nowMs
            tx.append(ping())
        }

        // AUTO-LIVE jalur2: fallback bila "1B01" belum pernah diterima sejak connect.
        if autoLive, state == .handshaking, nowMs - connectedAtMs >= autoLiveFallbackMs {
            tx.append(contentsOf: enterLive(nowMs: nowMs)) // lastLiveTxMs = nowMs ->160A mulai tick berikutnya
        }

        if state == .liveRequested,
           liveTicksSent < liveRetryMax,
           nowMs - lastLiveTxMs >= liveRetryIntervalMs
        {
            lastLiveTxMs = nowMs
            liveTicksSent += 1
            tx.append(FrameCodec.buildLine(command: Opcodes.Read.ENTER_LIVE))
        }

        return tx
    }

    private func ping() -> String {
        FrameCodec.buildLine(command: Opcodes.Ack.ACK_PING)
    }
}

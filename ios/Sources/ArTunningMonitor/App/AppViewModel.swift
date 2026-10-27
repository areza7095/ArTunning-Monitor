#if os(iOS)
import Foundation
import NetworkExtension
import SwiftUI

enum IosConnStatus {
    case disconnected, connecting, handshaking, liveRequested, streaming, reconnecting, error
}

enum IosLogDir { case tx, rx, info }

struct IosLogLine: Identifiable {
    let id = UUID()
    let dir: IosLogDir
    let text: String
    let timeMs: Int64
}

/// Orkestrator iOS: NetClient (byte) + LineSplitter/LiveSession/TelemetryParser (protokol).
/// Timing sama dgn Android (loop250 ms -> LiveSession.tick) — spec bagian6.
@MainActor
final class AppViewModel: ObservableObject {

    @Published var status: IosConnStatus = .disconnected
    @Published var host: String = "192.168.2.6"
    @Published var portText: String = "80"
    @Published var ssid: String = ""
    @Published var wifiPassword: String = ""
    @Published var telemetry: TelemetrySample?
    @Published var errorMessage: String?
    @Published var log: [IosLogLine] = []
    @Published var joinedWifi: Bool = false

    private let net = NetClient()
    private let splitter = LineSplitter()
    private let session = LiveSession()

    private var tickTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private let reconnectDelaysSec: [UInt64] = [2, 5, 10, 30] // setara ReconnectPolicy Android

    init() {
        net.onBytes = { [weak self] bytes in
            DispatchQueue.main.async { self?.handleBytes(bytes) }
        }
        net.onClosed = { [weak self] reason in
            DispatchQueue.main.async { self?.handleClosed(reason) }
        }
    }

    deinit {
        tickTask?.cancel()
        reconnectTask?.cancel()
    }

    // ------------------------------------------------------------------ koneksi

    func connect() {
        tickTask?.cancel()
        reconnectTask?.cancel()
        reconnectAttempt = 0
        guard let p = UInt16(portText), p > 0 else {
            status = .error
            errorMessage = "Port tidak valid"
            return
        }
        status = .connecting
        errorMessage = nil
        telemetry = nil
        appendLog(.info, "Menghubungkan ke \(host):\(p)…")
        Task { @MainActor [weak self] in
            guard let self else { return }
            if await self.tryConnectOnce() {
                self.appendLog(.info, "TCP tersambung")
                self.startTick()
                self.syncStatus()
            } else {
                self.status = .error
                self.errorMessage = "Gagal koneksi — pastikan iPhone join WiFi ECU & izin Local Network"
                self.appendLog(.info, "connect gagal")
            }
        }
    }

    func disconnect() {
        tickTask?.cancel()
        reconnectTask?.cancel()
        reconnectAttempt = 0
        net.disconnect() // notify=false: tanpa event onClosed
        session.onDisconnected()
        splitter.reset()
        status = .disconnected
        errorMessage = nil
        appendLog(.info, "Terputus manual")
    }

    /// Satu percobaan koneksi TCP; bila sukses -> handshake `1B00` dikirim.
    private func tryConnectOnce() async -> Bool {
        guard let p = UInt16(portText) else { return false }
        let h = host
        let ok: Bool = await withCheckedContinuation { continuation in
            net.connect(host: h, port: p) { success in
                continuation.resume(returning: success)
            }
        }
        guard ok else { return false }
        let tx = session.onConnected(nowMs: nowMs())
        sendFrames(tx)
        return true
    }

    // ------------------------------------------------------- loop sesi & timing

    private func startTick() {
        tickTask?.cancel()
        tickTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard let self, !Task.isCancelled else { return }
                let tx = self.session.tick(nowMs: self.nowMs())
                if !tx.isEmpty { self.sendFrames(tx) }
                if self.session.linkLost {
                    self.handleLinkLost()
                    return
                }
            }
        }
    }

    private func sendFrames(_ frames: [String]) {
        for f in frames {
            net.send(Data(FrameCodec.ascii(f)))
            appendLog(.tx, f.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    // ----------------------------------------------------------------- RX/TX

    private func handleBytes(_ data: [UInt8]) {
        let lines = splitter.feed(data)
        guard !lines.isEmpty else { return }
        let now = nowMs()
        var txs: [String] = []
        for line in lines {
            appendLog(.rx, line)
            txs += session.onRxLine(line, nowMs: now)
            if ReplyDispatch.parse(line).isTelemetry, let t = TelemetryParser.parse(line) {
                telemetry = t
            }
        }
        if session.connected { syncStatus() }
        if !txs.isEmpty { sendFrames(txs) }
    }

    private func handleClosed(_ reason: String) {
        tickTask?.cancel()
        appendLog(.info, "Koneksi terputus: \(reason)")
        scheduleReconnect(reason)
    }

    private func handleLinkLost() {
        appendLog(.info, "Link lost — tidak ada balasan echo >17 dtk")
        net.disconnect() // notify=false
        scheduleReconnect("link lost")
    }

    private func scheduleReconnect(_ reason: String) {
        if let t = reconnectTask, !t.isCancelled { return }
        status = .reconnecting
        errorMessage = "Reconnect (\(reason))"
        reconnectTask = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                guard self.reconnectAttempt < self.reconnectDelaysSec.count else {
                    self.status = .error
                    self.errorMessage = "Reconnect otomatis habis (\(reason)). Tekan Connect."
                    self.appendLog(.info, "Reconnect otomatis habis")
                    return
                }
                let d = self.reconnectDelaysSec[self.reconnectAttempt]
                self.reconnectAttempt += 1
                self.appendLog(.info, "Reconnect dalam \(d)s…")
                try? await Task.sleep(nanoseconds: d * 1_000_000_000)
                if await self.tryConnectOnce() {
                    self.reconnectAttempt = 0
                    self.startTick()
                    self.syncStatus()
                    return
                }
            }
        }
    }

    // ------------------------------------------------------------------- aksi

    func startLive() {
        guard session.connected else { return }
        sendFrames(session.requestLive(nowMs: nowMs()))
        syncStatus()
        appendLog(.info, "Meminta mode live…")
    }

    func stopLive() {
        guard session.connected else { return }
        sendFrames(session.stopLive(nowMs: nowMs()))
        syncStatus()
    }

    /// Kirim frame mentah dari tab Console (auto tambah CRLF bila belum ada).
    func sendRaw(_ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        sendFrames([t.hasSuffix("\r\n") ? t : t + "\r\n"])
    }

    func clearLog() {
        log.removeAll()
    }

    /// Auto-join AP WiFi ECU (opsional — NEHotspotConfiguration, iOS11+).
    func joinECUWifi() {
        let name = ssid.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else {
            appendLog(.info, "SSID kosong")
            return
        }
        let pass = wifiPassword
        // SDK iOS27: label init = ssid: ; passphrase non-optional ->
        // jaringan terbuka memakai init(ssid:), berpassword init(ssid:passphrase:isWEP:).
        let cfg: NEHotspotConfiguration
        if pass.isEmpty {
            cfg = NEHotspotConfiguration(ssid: name)
        } else {
            cfg = NEHotspotConfiguration(ssid: name, passphrase: pass, isWEP: false)
        }
        // SDK iOS27: joinMode tidak ada lagi -> pakai joinOnce (false = join & simpan profil).
        cfg.joinOnce = false
        NEHotspotConfigurationManager.shared.apply(cfg) { [weak self] error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let error {
                    self.appendLog(.info, "WiFi join gagal: \(error.localizedDescription)")
                } else {
                    self.joinedWifi = true
                    self.appendLog(.info, "Terhubung ke AP \(name)")
                }
            }
        }
    }

    // ------------------------------------------------------------------- intern

    private func syncStatus() {
        switch session.state {
        case .idle: status = .disconnected
        case .handshaking: status = .handshaking
        case .liveRequested: status = .liveRequested
        case .streaming: status = .streaming
        }
    }

    private func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    private func appendLog(_ dir: IosLogDir, _ text: String) {
        log.append(IosLogLine(dir: dir, text: text, timeMs: nowMs()))
        if log.count > 500 {
            log.removeFirst(log.count - 500)
        }
    }
}
#endif


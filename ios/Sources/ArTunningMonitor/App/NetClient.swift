#if os(iOS)
import Foundation
import Network

/// Transport TCP ke ECU — setara WifiCommHandler (spec bagian 8.2):
/// TcpClient(ip, port) + probe 1 byte0x00 + async receive buffer4095.
///
/// Semua state diakses hanya di `queue` internal (aman dari race);
/// callback `onBytes`/`onClosed` dipanggil dari `queue` — pemanggil wajib marshal.
final class NetClient {

    var onBytes: (([UInt8]) -> Void)?
    var onClosed: ((String) -> Void)?

    private let queue = DispatchQueue(label: "artunning.net")
    private var connection: NWConnection?
    private var ready = false
    /// Completion connect yang menunggu (dipanggil tepat sekali, di `queue`).
    private var pendingReport: ((Bool) -> Void)?

    private static let connectTimeoutMs = 10_000 // commTimeout10 dtk (spec bagian8.2)
    private static let maxReceive = 4095 // BufferSize ECU = 4095 (spec)

    /// Buka koneksi TCP. `completion` dipanggil dari queue internal (sekali).
    func connect(host: String, port: UInt16, completion: @escaping (Bool) -> Void) {
        queue.async {
            self.disconnectLocked(notify: false)
            self.pendingReport = completion
            guard let nwPort = NWEndpoint.Port(rawValue: port) else {
                self.report(false)
                return
            }
            let conn = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
            self.connection = conn
            conn.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready:
                    self.ready = true
                    self.sendProbeAndStartReceive()
                    self.report(true)
                case .failed(let error):
                    self.teardownLocked(reason: "TCP: \(error.localizedDescription)")
                case .cancelled:
                    if !self.ready { self.report(false) }
                default:
                    break
                }
            }
            conn.start(queue: self.queue)

            // timeout koneksi ala commTimeout10000 ms
            self.queue.asyncAfter(deadline: .now() + .milliseconds(Self.connectTimeoutMs)) {
                if !self.ready && self.pendingReport != nil {
                    self.teardownLocked(reason: "timeout koneksi (10 dtk)")
                }
            }
        }
    }

    /// Tutup sengaja; TIDAK memicu `onClosed` (pemanggil mengatur state sendiri).
    func disconnect(notify: Bool = false) {
        queue.async { self.disconnectLocked(notify: notify) }
    }

    /// Tulis byte; bila gagal/tersambung putus, lewat `onClosed`.
    func send(_ data: Data) {
        queue.async {
            guard self.ready, let conn = self.connection else { return }
            conn.send(content: data, completion: .contentProcessed { [weak self] error in
                if let error {
                    self?.teardownLocked(reason: "send: \(error.localizedDescription)")
                }
            })
        }
    }

    // MARK: - internal (harus dipanggil di queue)

    private func sendProbeAndStartReceive() {
        // probe persis WifiCommHandler.Open(): Stream.WriteByte(0x00)
        if let conn = connection {
            conn.send(content: Data([0x00]), completion: .contentProcessed { [weak self] error in
                if let error {
                    self?.teardownLocked(reason: "probe: \(error.localizedDescription)")
                }
            })
        }
        receiveNext()
    }

    private func receiveNext() {
        guard ready, let conn = connection else { return }
        conn.receive(minimumIncompleteLength: 1, maximumLength: Self.maxReceive) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.onBytes?(Array(data))
            }
            if let error {
                self.teardownLocked(reason: "stream: \(error.localizedDescription)")
                return
            }
            if isComplete {
                self.teardownLocked(reason: "stream ended")
                return
            }
            self.receiveNext()
        }
    }

    private func teardownLocked(reason: String) {
        let wasReady = ready
        ready = false
        let conn = connection
        connection = nil
        conn?.cancel()
        if wasReady {
            onClosed?(reason)
        } else {
            report(false)
        }
    }

    private func disconnectLocked(notify: Bool) {
        ready = false
        pendingReport = nil // batalkan connect yang sedang menunggu
        connection?.cancel()
        connection = nil
        if notify {
            onClosed?("closed")
        }
    }

    private func report(_ ok: Bool) {
        guard let c = pendingReport else { return }
        pendingReport = nil
        c(ok)
    }
}
#endif


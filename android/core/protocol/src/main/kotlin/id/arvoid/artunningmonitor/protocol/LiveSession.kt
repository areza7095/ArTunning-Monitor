package id.arvoid.artunningmonitor.protocol

/**
 * State machine sesi koneksi/ live ECU (spec bagian 6), murni tanpa I/O —
 * pemanggil (ViewModel) yang menjalankan timer dan menulis byte ke transport.
 *
 * Perilaku diambil dari bukti IL:
 *  - handshake: kirim "1B00" (Form1.lanjut_echo / ulang_echo_Tick; interval keepalive3000 ms)
 *  - balasan "1B01" = FEEDBACK_ECHO_ACK -> ConnectionState=1 & time_echo direset
 *    (Form1.SetText IL_06A3->IL_0DEA/IL_0E48)
 *  - watchdog: echo timer1700 ms x10 -> "Not Connected" (Form1.echo_Tick); di-suspend
 *    saat live (IL_000F: bila isLiveState -> ret tanpa hitung)
 *  - masuk live: kirim "1617" lalu "160A" tiap500 ms maks25x
 *    (SerialHandler_NewConnection IL_0129; timer_live_enter_Tick IL_0028)
 *  - frame "A603" pertama saat LiveRequested -> Streaming (Form1.SetText IL_0692->IL_0854,
 *    cabang masuk_live)
 *  - keluar live: "160B" (timer_live_enter_Tick IL_0078)
 *
 * Catatan penyederhanaan terhadap app:
 *  - App memulai timer "160A" setelah balasan "9616"; MVP langsung menjadwalkan "160A"
 *   500 ms setelah "1617" (urutan identik dengan jalur reconnect:1617 ->160A).
 *  - Pembanding core (mapping_live) diabaikan: frame A603 pertama diterima sebagai
 *    awal streaming (untuk monitor live nilai core tidak kritis).
 *
 * AUTO-LIVE (UX):
 *  - Setelah koneksi terbuka, sesi masuk live OTOMATIS tanpa tombol Start:
 *    pemicu hybrid = balasan "1B01" pertama ATAU fallback [autoLiveFallbackMs]
 *    sejak connect (bila ECU tidak pernah membalas echo).
 *  - stopLive() menonaktifkan autoLive -> sesi ini tetap manual sampai
 *    connect/reconnect berikutnya (onConnected mengaktifkan lagi).
 *  - Start manual (requestLive) SELALU bisa kapan pun, tidak tergantung autoLive.
 */
class LiveSession(
    private val keepaliveIntervalMs: Long = 3000,
    private val watchdogTimeoutMs: Long = 17000,
    private val liveRetryIntervalMs: Long = 500,
    private val liveRetryMax: Int = 25,
    private val autoLiveFallbackMs: Long = 1500,
) {
    var state: SessionState = SessionState.Idle
        private set

    var connected: Boolean = false
        private set

    /** Latch "link lost" — direset saat ada balasan echo (1B01) / koneksi baru. */
    var linkLost: Boolean = false
        private set

    var liveTicksSent: Int = 0
        private set

    /** Otomatis masuk live setelah handshake/fallback; dimatikan oleh stopLive(). */
    var autoLive: Boolean = true

    private var connectedAtMs: Long = 0
    private var lastRxMs: Long = 0
    private var lastEchoReplyMs: Long = 0
    private var lastPingMs: Long = 0
    private var lastLiveTxMs: Long = 0

    /** Koneksi transport terbuka: hasilkan frame handshake pertama. */
    fun onConnected(nowMs: Long): List<String> {
        connected = true
        state = SessionState.Handshaking
        lastRxMs = nowMs
        lastEchoReplyMs = nowMs
        lastPingMs = nowMs
        lastLiveTxMs = nowMs
        liveTicksSent = 0
        linkLost = false
        connectedAtMs = nowMs
        autoLive = true // sesi baru -> auto-live kembali aktif
        return listOf(ping())
    }

    fun onDisconnected() {
        connected = false
        state = SessionState.Idle
        liveTicksSent = 0
        linkLost = false
    }

    /**
     * Setiap baris RX diterima pemanggil: perbarui sumber waktu watchdog,
     * lalu proses balasan yang relevan. Menghasilkan frame TX tambahan (bila ada).
     */
    fun onRxLine(line: String, nowMs: Long): List<String> {
        if (!connected) return emptyList()
        lastRxMs = nowMs
        val reply = ReplyDispatch.parse(line)
        return when {
            reply.isEchoAck -> {
                lastEchoReplyMs = nowMs
                linkLost = false
                // AUTO-LIVE jalur1: handshake oke -> langsung masuk live.
                if (autoLive && state == SessionState.Handshaking) enterLive(nowMs) else emptyList()
            }
            reply.isTelemetry && state == SessionState.LiveRequested -> {
                state = SessionState.Streaming
                liveTicksSent = 0
                emptyList()
            }
            else -> emptyList()
        }
    }

    /** Permintaan masuk mode live: kirim PC_READ_USER_DEF_2 (1617). */
    fun requestLive(nowMs: Long): List<String> {
        if (!connected || state == SessionState.LiveRequested) return emptyList()
        return enterLive(nowMs)
    }

    /** Keluar mode live: kirim EXIT_LIVE (160B) + matikan auto-live untuk sesi ini. */
    fun stopLive(nowMs: Long): List<String> {
        if (!connected || state == SessionState.Idle) return emptyList()
        autoLive = false // manual sampai connect/reconnect berikutnya
        state = SessionState.Handshaking
        liveTicksSent = 0
        lastLiveTxMs = nowMs
        return listOf(frameLiveExit())
    }

    private fun enterLive(nowMs: Long): List<String> {
        state = SessionState.LiveRequested
        liveTicksSent = 0
        lastLiveTxMs = nowMs
        return listOf(frameLiveEnterPrep())
    }

    /**
     * Dipanggil periodik (disarankan tiap100-500 ms) untuk:
     *  - keepalive "1B00" tiap [keepaliveIntervalMs]
     *  - AUTO-LIVE jalur2: fallback [autoLiveFallbackMs] sejak connect bila "1B01"
     *    belum pernah diterima -> masuk live juga
     *  - retry "160A" tiap [liveRetryIntervalMs] selama LiveRequested (maks [liveRetryMax])
     *  - watchdog: tanpa balasan echo selama [watchdogTimeoutMs] -> linkLost=true
     *    (di-suspend selama Streaming, persis app).
     */
    fun tick(nowMs: Long): List<String> {
        if (!connected || state == SessionState.Idle) return emptyList()
        val tx = ArrayList<String>(2)

        val watchdogActive = state != SessionState.Streaming
        if (watchdogActive && nowMs - lastEchoReplyMs > watchdogTimeoutMs) {
            linkLost = true
        }

        if (nowMs - lastPingMs >= keepaliveIntervalMs) {
            lastPingMs = nowMs
            tx += ping()
        }

        if (autoLive && state == SessionState.Handshaking &&
            nowMs - connectedAtMs >= autoLiveFallbackMs
        ) {
            tx += enterLive(nowMs) // lastLiveTxMs = nowMs -> retry "160A" mulai tick berikutnya
        }

        if (state == SessionState.LiveRequested && liveTicksSent < liveRetryMax &&
            nowMs - lastLiveTxMs >= liveRetryIntervalMs
        ) {
            lastLiveTxMs = nowMs
            liveTicksSent++
            tx += frameLiveEnter()
        }

        return tx
    }

    private fun ping(): String = FrameCodec.buildLine(Opcodes.Ack.ACK_PING)
    private fun frameLiveEnterPrep(): String = FrameCodec.buildLine(Opcodes.Read.PC_READ_USER_DEF_2)
    private fun frameLiveEnter(): String = FrameCodec.buildLine(Opcodes.Read.ENTER_LIVE)
    private fun frameLiveExit(): String = FrameCodec.buildLine(Opcodes.Read.EXIT_LIVE)
}

enum class SessionState {
    Idle,
    Handshaking,
    LiveRequested,
    Streaming,
}

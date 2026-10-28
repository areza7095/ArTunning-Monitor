package id.arvoid.artunningmonitor

import android.content.Context
import id.arvoid.artunningmonitor.protocol.DiagBit
import id.arvoid.artunningmonitor.protocol.DiagField
import id.arvoid.artunningmonitor.protocol.EcuModelSample
import id.arvoid.artunningmonitor.protocol.MetricFormatter
import id.arvoid.artunningmonitor.protocol.RuntimeSample
import id.arvoid.artunningmonitor.protocol.TelemetrySample
import id.arvoid.artunningmonitor.protocol.TpsCalSample
import id.arvoid.artunningmonitor.widget.TelemetryWidgetUpdater
import id.arvoid.artunningmonitor.widget.WidgetSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Status koneksi sesi ECU (enum dipakai UI, service, widget). */
enum class ConnStatus { PermissionNeeded, Disconnected, Connecting, Handshaking, LiveRequested, Streaming, Reconnecting, Error }

enum class LogDir { Tx, Rx, Info }

data class LogLine(val dir: LogDir, val text: String, val timeMs: Long)

/**
 * Repository pusat: satu-satunya sumber status koneksi + telemetri + log.
 *
 * Penulis = [EcUService] (foreground service yang memegang SppClient + LiveSession);
 * pembaca = MainViewModel (UI), widget Glance, dan media session Android Auto.
 * Konsol (tab Console) menulis lewat [sendRaw].
 */
object EcURepository {

    data class Snapshot(
        val status: ConnStatus = ConnStatus.Disconnected,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val telemetry: TelemetrySample? = null,
        val telemetryAtMs: Long = 0L,
        val error: String? = null,
    )

    /** Snapshot hasil diagnosa — diisi dari balasan A601/A602/9616/B800. */
    data class DiagSnapshot(
        val diagFields: List<DiagField>? = null,
        val tpsCal: TpsCalSample? = null,
        val ecuModel: EcuModelSample? = null,
        val runtime: RuntimeSample? = null,
    )

    /** Jembatan perintah dari UI/konsol ke service (didaftarkan service saat hidup). */
    interface EcULink {
        fun sendRawLine(line: String)
        fun requestLive()
        fun stopLive()
    }

    private const val LOG_CAP = 500

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val _diag = MutableStateFlow(DiagSnapshot())
    val diag: StateFlow<DiagSnapshot> = _diag.asStateFlow()

    @Volatile
    var link: EcULink? = null

    /** Application context untuk persist snapshot widget + dorong update (diisi saat service/app hidup). */
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Persist snapshot ke prefs widget + dorong render ulang home screen.
     * Dipanggil setiap state berubah — terpisah dari throttle notifikasi service,
     * supaya widget real-time walau jalur notifikasi belum/ tidak jalan.
     */
    private fun persistAndPushWidget() {
        val ctx = appContext ?: return
        WidgetSnapshot.write(ctx, _state.value)
        TelemetryWidgetUpdater.updateAll(ctx)
    }

    // ---------------------------------------------------------------- tulis (service)

    /**
     * Mulai koneksi baru: set status Connecting SEJAK DETIK PERTAMA (source of truth),
     * ganti seluruh snapshot supaya telemetry/error basi ikut hilang.
     *
     * Tanpa ini, mirror UI (combine state+log) masih membaca Disconnected dari repository
     * saat log "Menghubungkan…" muncul → status connect kedip "Terputus" lalu balik.
     * Idempoten — aman dipanggil dari MainViewModel DAN EcUService.onStartCommand.
     */
    fun beginConnect(address: String, name: String?) {
        _state.update {
            Snapshot(
                status = ConnStatus.Connecting,
                deviceName = name,
                deviceAddress = address,
            )
        }
        persistAndPushWidget()
    }

    fun updateStatus(status: ConnStatus, deviceName: String? = null, deviceAddress: String? = null, error: String? = null) {
        _state.update {
            it.copy(
                status = status,
                deviceName = deviceName ?: it.deviceName,
                deviceAddress = deviceAddress ?: it.deviceAddress,
                error = error,
            )
        }
        persistAndPushWidget()
    }

    fun setTelemetry(sample: TelemetrySample, atMs: Long) {
        _state.update { it.copy(telemetry = sample, telemetryAtMs = atMs) }
        persistAndPushWidget()
    }

    // ---------------------------------------------------------------- diagnosa

    fun setDiagFields(fields: List<DiagField>) {
        _diag.update { it.copy(diagFields = fields) }
    }

    fun setTpsCal(sample: TpsCalSample) {
        _diag.update { it.copy(tpsCal = sample) }
    }

    fun setEcuModel(sample: EcuModelSample) {
        _diag.update { it.copy(ecuModel = sample) }
    }

    fun setRuntime(sample: RuntimeSample) {
        _diag.update { it.copy(runtime = sample) }
    }

    /** Reset snapshot diagnosa saat disconnect (data basi jangan bertahan). */
    fun clearDiag() {
        _diag.value = DiagSnapshot()
    }

    /**
     * Minta ECU kirim data diagnostik: TPS cal (4601), DIAG2 (4602), model (1616),
     * runtime (B800). Balasan diproses EcUService.handleBytes → setDiag*.
     */
    fun requestDiagRefresh() {
        val l = link ?: run {
            appendLog(LogDir.Info, "Tidak tersambung — diagnostik butuh koneksi")
            return
        }
        l.sendRawLine(id.arvoid.artunningmonitor.protocol.FrameCodec.buildLine(
            id.arvoid.artunningmonitor.protocol.Opcodes.Write.READ_TPS_CAL,
        ))
        l.sendRawLine(id.arvoid.artunningmonitor.protocol.FrameCodec.buildLine(
            id.arvoid.artunningmonitor.protocol.Opcodes.Write.READ_DIAG2,
        ))
        l.sendRawLine(id.arvoid.artunningmonitor.protocol.FrameCodec.buildLine(
            id.arvoid.artunningmonitor.protocol.Opcodes.Read.READ_ECU_MODEL,
        ))
        l.sendRawLine(id.arvoid.artunningmonitor.protocol.FrameCodec.buildLine(
            id.arvoid.artunningmonitor.protocol.Opcodes.Write.MAINTAIN_REPORT,
        ))
        appendLog(LogDir.Info, "Meminta data diagnostik (4601/4602/1616/B800)")
    }

    /** Kirim actuator test (opcode Test 500x). @return true bila frame diteruskan ke link. */
    fun sendActuatorTest(opcode: Int): Boolean {
        val l = link ?: run {
            appendLog(LogDir.Info, "Tidak tersambung — actuator test butuh koneksi")
            return false
        }
        val frame = id.arvoid.artunningmonitor.protocol.FrameCodec.buildLine(opcode)
        l.sendRawLine(frame)
        return true
    }

    fun reset(keepAddress: Boolean = false) {
        _state.update {
            Snapshot(
                status = ConnStatus.Disconnected,
                deviceAddress = if (keepAddress) it.deviceAddress else null,
                deviceName = if (keepAddress) it.deviceName else null,
            )
        }
        clearDiag()
        persistAndPushWidget()
    }

    fun appendLog(dir: LogDir, text: String) {
        val line = LogLine(dir, text, System.currentTimeMillis())
        _log.update { old ->
            val next = old + line
            if (next.size > LOG_CAP) next.takeLast(LOG_CAP) else next
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    // ---------------------------------------------------------------- perintah (UI/konsol)

    /** true bila tersambung (service hidup). */
    fun sendRaw(line: String): Boolean {
        val l = link ?: run {
            appendLog(LogDir.Info, "Tidak tersambung - koneksi dipegang EcUService")
            return false
        }
        l.sendRawLine(line)
        return true
    }

    fun requestLive() {
        link?.requestLive() ?: appendLog(LogDir.Info, "Tidak tersambung - live hanya saat tersambung")
    }

    fun stopLive() {
        link?.stopLive()
    }

    // ---------------------------------------------------------------- util

    /** Ringkasan notifikasi/media: "3450 rpm · AFR12.50" utk metrik default. */
    fun statusLine(): String {
        val s = _state.value
        val t = s.telemetry
        return when {
            t != null -> MetricFormatter.summary(t, id.arvoid.artunningmonitor.protocol.MetricKey.DEFAULT)
            s.deviceName != null -> s.deviceName!!
            else -> "Tidak tersambung"
        }
    }
}

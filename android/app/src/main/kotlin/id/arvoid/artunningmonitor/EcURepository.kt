package id.arvoid.artunningmonitor

import id.arvoid.artunningmonitor.protocol.MetricFormatter
import id.arvoid.artunningmonitor.protocol.TelemetrySample
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

    @Volatile
    var link: EcULink? = null

    // ---------------------------------------------------------------- tulis (service)

    fun updateStatus(status: ConnStatus, deviceName: String? = null, deviceAddress: String? = null, error: String? = null) {
        _state.update {
            it.copy(
                status = status,
                deviceName = deviceName ?: it.deviceName,
                deviceAddress = deviceAddress ?: it.deviceAddress,
                error = error,
            )
        }
    }

    fun setTelemetry(sample: TelemetrySample, atMs: Long) {
        _state.update { it.copy(telemetry = sample, telemetryAtMs = atMs) }
    }

    fun reset(keepAddress: Boolean = false) {
        _state.update {
            Snapshot(
                status = ConnStatus.Disconnected,
                deviceAddress = if (keepAddress) it.deviceAddress else null,
                deviceName = if (keepAddress) it.deviceName else null,
            )
        }
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

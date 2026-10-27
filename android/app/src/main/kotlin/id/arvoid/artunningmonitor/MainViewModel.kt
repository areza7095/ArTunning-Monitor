package id.arvoid.artunningmonitor

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.arvoid.artunningmonitor.protocol.FrameCodec
import id.arvoid.artunningmonitor.protocol.LineSplitter
import id.arvoid.artunningmonitor.protocol.LiveSession
import id.arvoid.artunningmonitor.protocol.ReplyDispatch
import id.arvoid.artunningmonitor.protocol.SessionState
import id.arvoid.artunningmonitor.protocol.TelemetryParser
import id.arvoid.artunningmonitor.protocol.TelemetrySample
import id.arvoid.artunningmonitor.transport.PermissionHelper
import id.arvoid.artunningmonitor.transport.ReconnectPolicy
import id.arvoid.artunningmonitor.transport.SppClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnStatus { PermissionNeeded, Disconnected, Connecting, Handshaking, LiveRequested, Streaming, Reconnecting, Error }

enum class LogDir { Tx, Rx, Info }

data class LogLine(val dir: LogDir, val text: String, val timeMs: Long)

/** Potongan "name to address" perangkat Bluetooth. */
data class DeviceItem(val name: String, val address: String)

data class AppUiState(
    val status: ConnStatus = ConnStatus.Disconnected,
    val permissionsGranted: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val bonded: List<DeviceItem> = emptyList(),
    val discovered: List<DeviceItem> = emptyList(),
    val scanning: Boolean = false,
    val telemetry: TelemetrySample? = null,
    val error: String? = null,
    val log: List<LogLine> = emptyList(),
)

/**
 * Orkestrator aplikasi: menyambungkan [SppClient] (byte murni) dengan
 * [LiveSession]/[LineSplitter]/[TelemetryParser] (protokol) — tanpa pengetahuan
 * protokol di transport, tanpa pengetahuan Bluetooth di core:protocol.
 *
 * Semua timing dijalankan di sini (loop250 ms memanggil LiveSession.tick) —
 * lihat docs/juken5-protocol-spec.md bagian6 untuk angka-angkanya.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val client = SppClient()
    private val splitter = LineSplitter(filterValidChars = true)
    private val session = LiveSession()
    private val reconnectPolicy = ReconnectPolicy()

    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    private val adapter get() = (getApplication<Application>()
        .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    init {
        client.listener = object : SppClient.Listener {
            override fun onBytes(data: ByteArray) = onBytesFromEcu(data)
            override fun onClosed(reason: String) = onStreamClosed(reason)
        }
        refreshPermissions()
        refreshBonded()
    }

    // ------------------------------------------------------------------ izin
    fun refreshPermissions() {
        _state.update { it.copy(permissionsGranted = PermissionHelper.hasAll(getApplication())) }
    }

    // ------------------------------------------------------------- perangkat
    fun refreshBonded() {
        val a = adapter ?: return
        if (!PermissionHelper.hasAll(getApplication())) {
            refreshPermissions()
            return
        }
        val list = client.bondedDevices(a).mapNotNull { d ->
            try {
                DeviceItem(d.name ?: d.address, d.address)
            } catch (_: SecurityException) {
                null
            }
        }.sortedBy { it.name.lowercase() }
        _state.update { it.copy(bonded = list) }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val a = adapter ?: return
        if (!PermissionHelper.hasAll(getApplication())) {
            refreshPermissions()
            _state.update { it.copy(status = ConnStatus.PermissionNeeded) }
            return
        }
        if (_state.value.scanning) return
        val ctx = getApplication<Application>()
        stopDiscovery()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val d: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (d != null) {
                            val name = try {
                                d.name ?: d.address
                            } catch (_: SecurityException) {
                                d.address
                            }
                            _state.update { st ->
                                if (st.discovered.any { it.address == d.address }) st
                                else st.copy(discovered = st.discovered + DeviceItem(name, d.address))
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _state.update { it.copy(scanning = false) }
                    }
                }
            }
        }
        // Catatan: broadcast system (ACTION_FOUND) -> pakai RECEIVER_EXPORTED agar
        // diterima di semua API; daftar perangkat termasuk input debug, risiko rendah.
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        discoveryReceiver = receiver
        val started = a.startDiscovery()
        _state.update { it.copy(scanning = started) }
        log(LogDir.Info, if (started) "Memindai perangkat…" else "Gagal memulai scan")
    }

    fun stopDiscovery() {
        discoveryReceiver?.let {
            try {
                getApplication<Application>().unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        discoveryReceiver = null
        adapter?.let { a ->
            if (a.isDiscovering) a.cancelDiscovery()
        }
        _state.update { it.copy(scanning = false) }
    }

    // ---------------------------------------------------------------- koneksi
    fun connect(address: String, name: String? = null) {
        viewModelScope.launch {
            sessionJob?.cancel()
            reconnectJob?.cancel()
            _state.update {
                it.copy(
                    status = ConnStatus.Connecting,
                    deviceAddress = address,
                    deviceName = name ?: it.deviceName,
                    error = null,
                    telemetry = null,
                )
            }
            log(LogDir.Info, "Menghubungkan ke ${name ?: address}…")
            val ok = withContext(Dispatchers.IO) { doConnect(address) }
            if (ok) {
                reconnectPolicy.reset()
                startSessionLoop()
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        sessionJob?.cancel()
        reconnectJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { client.close() }
        session.onDisconnected()
        splitter.reset()
        reconnectPolicy.reset()
        _state.update { it.copy(status = ConnStatus.Disconnected, error = null) }
        log(LogDir.Info, "Terputus manual")
        manualDisconnect = false
    }

    @SuppressLint("MissingPermission")
    private fun doConnect(address: String): Boolean {
        val a = adapter ?: run {
            setError("Bluetooth tidak tersedia di perangkat ini")
            return false
        }
        if (!PermissionHelper.hasAll(getApplication())) {
            _state.update { it.copy(status = ConnStatus.PermissionNeeded) }
            return false
        }
        return try {
            a.cancelDiscovery()
            val device = a.getRemoteDevice(address)
            val devName = try {
                device.name ?: address
            } catch (_: SecurityException) {
                address
            }
            if (!client.connect(device)) {
                _state.update { it.copy(status = ConnStatus.Error, error = "Gagal tersambung — cek module sudah dipair & dekat") }
                log(LogDir.Info, "connect gagal ke $address")
                return false
            }
            splitter.reset()
            val tx = session.onConnected(System.currentTimeMillis())
            for (f in tx) {
                client.write(FrameCodec.ascii(f))
                log(LogDir.Tx, f.trim())
            }
            _state.update { it.copy(status = statusOfSession(), deviceName = devName, error = null) }
            log(LogDir.Info, "Tersambung ke $devName")
            true
        } catch (se: SecurityException) {
            _state.update { it.copy(status = ConnStatus.PermissionNeeded, permissionsGranted = false, error = "Izin Bluetooth dibutuhkan") }
            false
        } catch (e: Exception) {
            _state.update { it.copy(status = ConnStatus.Error, error = "Error koneksi: ${e.message}") }
            false
        }
    }

    // ------------------------------------------------------- loop sesi & timing
    private fun startSessionLoop() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            while (isActive) {
                delay(250) // resolusi tick; interval sebenarnya dikontrol LiveSession
                val tx = session.tick(System.currentTimeMillis())
                if (tx.isNotEmpty()) sendFrames(tx)
                if (session.linkLost) {
                    handleLinkLost()
                    break
                }
            }
        }
    }

    private suspend fun sendFrames(frames: List<String>) {
        for (f in frames) {
            try {
                withContext(Dispatchers.IO) { client.write(FrameCodec.ascii(f)) }
                log(LogDir.Tx, f.trim())
            } catch (e: Exception) {
                log(LogDir.Info, "TX gagal: ${e.message}")
                if (!manualDisconnect) scheduleReconnect("write gagal")
                return
            }
        }
    }

    // ------------------------------------------------------------- RX dari ECU
    private fun onBytesFromEcu(data: ByteArray) {
        val lines = splitter.feed(data)
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val txs = ArrayList<String>()
        for (line in lines) {
            log(LogDir.Rx, line)
            txs += session.onRxLine(line, now)
            if (ReplyDispatch.parse(line).isTelemetry) {
                TelemetryParser.parse(line)?.let { t ->
                    _state.update { it.copy(telemetry = t) }
                }
            }
        }
        if (session.connected) _state.update { it.copy(status = statusOfSession()) }
        if (txs.isNotEmpty()) viewModelScope.launch { sendFrames(txs) }
    }

    private fun onStreamClosed(reason: String) {
        if (manualDisconnect) return
        sessionJob?.cancel()
        log(LogDir.Info, "Koneksi terputus: $reason")
        scheduleReconnect(reason)
    }

    private fun handleLinkLost() {
        log(LogDir.Info, "Link lost — tidak ada balasan echo >17 dtk")
        viewModelScope.launch(Dispatchers.IO) { client.close() }
        scheduleReconnect("link lost")
    }

    private fun scheduleReconnect(reason: String) {
        val addr = _state.value.deviceAddress ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            _state.update { it.copy(status = ConnStatus.Reconnecting, error = "Reconnect ($reason)") }
            while (isActive) {
                val d = reconnectPolicy.nextDelayMs()
                if (d == null) {
                    _state.update { it.copy(status = ConnStatus.Error, error = "Reconnect otomatis habis ($reason). Tekan Connect.") }
                    log(LogDir.Info, "Reconnect otomatis habis")
                    return@launch
                }
                log(LogDir.Info, "Reconnect dalam ${d / 1000}s…")
                delay(d)
                val ok = withContext(Dispatchers.IO) { doConnect(addr) }
                if (ok) {
                    reconnectPolicy.reset()
                    startSessionLoop()
                    return@launch
                }
            }
        }
    }

    // ------------------------------------------------------------ aksi live
    fun startLive() {
        viewModelScope.launch {
            if (!client.isConnected) return@launch
            val tx = session.requestLive(System.currentTimeMillis())
            sendFrames(tx)
            _state.update { it.copy(status = statusOfSession()) }
            log(LogDir.Info, "Meminta mode live…")
        }
    }

    fun stopLive() {
        viewModelScope.launch {
            if (!client.isConnected) return@launch
            val tx = session.stopLive(System.currentTimeMillis())
            sendFrames(tx)
            _state.update { it.copy(status = statusOfSession()) }
        }
    }

    // ---------------------------------------------------------------- konsol
    fun sendRaw(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val frame = if (trimmed.endsWith("\r\n")) trimmed else "$trimmed\r\n"
        viewModelScope.launch { sendFrames(listOf(frame)) }
    }

    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    // -------------------------------------------------------------- util/intern
    private var manualDisconnect = false

    private fun setError(msg: String) {
        _state.update { it.copy(status = ConnStatus.Error, error = msg) }
    }

    private fun statusOfSession(): ConnStatus = when (session.state) {
        SessionState.Idle -> if (client.isConnected) ConnStatus.Handshaking else ConnStatus.Disconnected
        SessionState.Handshaking -> ConnStatus.Handshaking
        SessionState.LiveRequested -> ConnStatus.LiveRequested
        SessionState.Streaming -> ConnStatus.Streaming
    }

    private fun log(dir: LogDir, text: String) {
        val line = LogLine(dir, text, System.currentTimeMillis())
        _state.update { st ->
            val next = st.log + line
            st.copy(log = if (next.size > 500) next.takeLast(500) else next)
        }
    }

    override fun onCleared() {
        stopDiscovery()
        sessionJob?.cancel()
        reconnectJob?.cancel()
        client.close()
        super.onCleared()
    }
}

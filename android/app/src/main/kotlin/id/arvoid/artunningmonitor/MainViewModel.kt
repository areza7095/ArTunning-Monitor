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
import id.arvoid.artunningmonitor.core.EcUService
import id.arvoid.artunningmonitor.transport.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val status: ConnStatus = ConnStatus.Disconnected,
    val permissionsGranted: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val bonded: List<DeviceItem> = emptyList(),
    val discovered: List<DeviceItem> = emptyList(),
    val scanning: Boolean = false,
    val telemetry: id.arvoid.artunningmonitor.protocol.TelemetrySample? = null,
    val error: String? = null,
    val log: List<LogLine> = emptyList(),
)

data class DeviceItem(val name: String, val address: String)

/**
 * ViewModel UI — TIDAK memegang koneksi lagi (sejak fitur widget/Auto):
 * koneksi hidup di [EcUService] (foreground service) dan semua state dibaca dari
 * [EcURepository]. VM hanya: (1) mirror repository ke [AppUiState],
 * (2) menjalankan discovery Bluetooth, (3) meneruskan perintah ke service/repository.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val adapter get() = (getApplication<Application>()
        .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    init {
        // Mirror state + log repository (source of truth) ke UI.
        viewModelScope.launch {
            combine(EcURepository.state, EcURepository.log) { s, l -> s to l }
                .collect { (s, l) ->
                    _state.update {
                        it.copy(
                            status = s.status,
                            deviceName = s.deviceName,
                            deviceAddress = s.deviceAddress,
                            telemetry = s.telemetry,
                            error = s.error,
                            log = l,
                        )
                    }
                }
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
        val devices: Set<android.bluetooth.BluetoothDevice> = try {
            a.bondedDevices ?: emptySet()
        } catch (_: SecurityException) {
            emptySet()
        }
        val list = devices.mapNotNull { d ->
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
        stopDiscovery()
        val ctx = getApplication<Application>()
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
        // broadcast system (ACTION_FOUND) -> RECEIVER_EXPORTED agar diterima di semua API
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        discoveryReceiver = receiver
        val started = a.startDiscovery()
        _state.update { it.copy(scanning = started) }
        EcURepository.appendLog(LogDir.Info, if (started) "Memindai perangkat..." else "Gagal memulai scan")
    }

    private var discoveryReceiver: BroadcastReceiver? = null

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

    // ---------------------------------------------------------------- koneksi (via service)

    fun connect(address: String, name: String? = null) {
        viewModelScope.launch {
            if (!PermissionHelper.hasAll(getApplication())) {
                _state.update { it.copy(status = ConnStatus.PermissionNeeded) }
                return@launch
            }
            // optimis; EcUService akan mengonfirmasi lewat EcURepository
            _state.update {
                it.copy(
                    status = ConnStatus.Connecting,
                    deviceAddress = address,
                    deviceName = name ?: it.deviceName,
                    error = null,
                    telemetry = null,
                )
            }
            EcUService.start(getApplication(), address, name)
        }
    }

    fun disconnect() {
        EcUService.stop(getApplication()) // service mereset state jadi Disconnected
    }

    // ------------------------------------------------------------- aksi sesi

    fun startLive() {
        EcURepository.requestLive()
    }

    fun stopLive() {
        EcURepository.stopLive()
    }

    /** Kirim frame mentah dari tab Console (auto tambah CRLF bila belum ada). */
    fun sendRaw(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val frame = if (t.endsWith("\r\n")) t else "$t\r\n"
        EcURepository.sendRaw(frame)
    }

    fun clearLog() {
        EcURepository.clearLog()
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}

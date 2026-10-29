package id.arvoid.artunningmonitor.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.EcURepository
import id.arvoid.artunningmonitor.LogDir
import id.arvoid.artunningmonitor.MainActivity
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.protocol.DiagParser
import id.arvoid.artunningmonitor.protocol.FrameCodec
import id.arvoid.artunningmonitor.protocol.LineSplitter
import id.arvoid.artunningmonitor.protocol.LiveSession
import id.arvoid.artunningmonitor.protocol.ReplyDispatch
import id.arvoid.artunningmonitor.protocol.SessionState
import id.arvoid.artunningmonitor.protocol.TelemetryParser
import id.arvoid.artunningmonitor.transport.ReconnectPolicy
import id.arvoid.artunningmonitor.transport.SppClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service pemegang koneksi ECU (spec: transport di-background legal
 * dengan foregroundServiceType=connectedDevice).
 *
 * Alur: CONNECT -> beginForeground -> SppClient.connect -> LiveSession.onConnected
 *   -> loop tick250ms (keepalive1B00 / auto-live / watchdog) -> RX -> repository.
 * Semua state baca lewat [EcURepository]; UI hanyalah pengamat.
 */
class EcUService : android.app.Service(), SppClient.Listener, EcURepository.EcULink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = SppClient()
    private val splitter = LineSplitter()
    private val session = LiveSession()
    private val reconnectPolicy = ReconnectPolicy()

    private var tickJob: Job? = null
    private var reconnectJob: Job? = null
    private var manualClose = false
    private var lastNotifMs = 0L

    companion object {
        const val ACTION_CONNECT = "id.arvoid.artunningmonitor.core.CONNECT"
        const val ACTION_DISCONNECT = "id.arvoid.artunningmonitor.core.DISCONNECT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "ecu_connection"
        private const val NOTIF_ID = 41

        fun start(context: Context, address: String, name: String?) {
            val intent = Intent(context, EcUService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_NAME, name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val stop = Intent(context, EcUService::class.java).apply { action = ACTION_DISCONNECT }
            // DISCONNECT ditangani onStartCommand -> stopSelf. startService biasa cukup
            // karena selalu dipanggil dari UI yang sedang foreground (tombol Disconnect).
            try {
                context.startService(stop)
            } catch (_: IllegalStateException) {
                // app sudah di background -> paksa path foreground (service langsung berhenti)
                ContextCompat.startForegroundService(context, stop)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        client.listener = this
        EcURepository.init(this) // persist snapshot widget + push update dari repository
        EcURepository.link = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                manualClose = false
                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: run { failStop("Alamat perangkat hilang"); return START_NOT_STICKY }
                val name = intent.getStringExtra(EXTRA_NAME)
                // Source of truth: Connecting + clear snapshot basi SEBELUM doConnect appendLog
                // (idempoten dengan panggilan MainViewModel.beginConnect; nutup START_REDELIVER_INTENT)
                EcURepository.beginConnect(address, name)
                // foreground wajib dalam5 dtk setelah startForegroundService
                startAsForeground(getString(R.string.notif_connecting, name ?: address))
                scope.launch { doConnect(address, name) }
                return START_REDELIVER_INTENT
            }
            ACTION_DISCONNECT -> {
                manualClose = true
                teardown()
                stopEverything()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    // ------------------------------------------------------------------ koneksi

    private suspend fun doConnect(address: String, name: String?) {
        append(LogDir.Info, "Menghubungkan ke ${name ?: address}...")
        val connected = withContext(Dispatchers.IO) {
            try {
                val bt = adapter() ?: throw IllegalStateException("Bluetooth tidak tersedia")
                client.connect(bt.getRemoteDevice(address))
            } catch (se: SecurityException) {
                setStatus(ConnStatus.PermissionNeeded, error = "Izin Bluetooth dibutuhkan")
                return@withContext false
            } catch (e: Exception) {
                setStatus(ConnStatus.Error, error = "Gagal tersambung: ${e.message}")
                return@withContext false
            }
        }
        if (!connected) {
            append(LogDir.Info, "connect gagal ke $address")
            stopEverything()
            return
        }
        splitter.reset()
        reconnectPolicy.reset()
        val tx = session.onConnected(System.currentTimeMillis())
        sendFrames(tx)
        EcURepository.updateStatus(statusOfSession(), deviceName = name, deviceAddress = address, error = null)
        append(LogDir.Info, "Tersambung ke ${name ?: address}")
        refreshNotification(force = true)
        startMediaService()
        startTick()
    }

    private fun adapter(): android.bluetooth.BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private fun startTick() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(250)
                val tx = session.tick(System.currentTimeMillis())
                if (tx.isNotEmpty()) sendFrames(tx)
                if (session.linkLost) {
                    handleLinkLost()
                    return@launch
                }
            }
        }
    }

    private fun sendFrames(frames: List<String>) {
        for (f in frames) {
            try {
                client.write(FrameCodec.ascii(f))
                append(LogDir.Tx, f.trim())
            } catch (e: Exception) {
                append(LogDir.Info, "TX gagal: ${e.message}")
                scheduleReconnect("write gagal")
                return
            }
        }
    }

    // ------------------------------------------------------------- SppClient.Listener

    override fun onBytes(data: ByteArray) {
        scope.launch { handleBytes(data) }
    }

    override fun onClosed(reason: String) {
        scope.launch {
            if (manualClose) return@launch
            tickJob?.cancel()
            append(LogDir.Info, "Koneksi terputus: $reason")
            scheduleReconnect(reason)
        }
    }

    private fun handleBytes(data: ByteArray) {
        val lines = splitter.feed(data)
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val txs = ArrayList<String>()
        for (line in lines) {
            append(LogDir.Rx, line)
            txs += session.onRxLine(line, now)
            if (ReplyDispatch.parse(line).isTelemetry) {
                TelemetryParser.parse(line)?.let { EcURepository.setTelemetry(it, now) }
            }
            // Diagnostik: A601 (TPS cal), A602 (diag bits), 9616 (model), B800 (runtime)
            DiagParser.parseDiag2(line)?.let { EcURepository.setDiagFields(it) }
            DiagParser.parseTpsCal(line)?.let { EcURepository.setTpsCal(it) }
            DiagParser.parseEcuModel(line)?.let { EcURepository.setEcuModel(it) }
            DiagParser.parseRuntime(line)?.let { EcURepository.setRuntime(it) }
        }
        if (session.connected) EcURepository.updateStatus(statusOfSession())
        if (txs.isNotEmpty()) sendFrames(txs)
        refreshNotification() // throttle di dalamnya
    }

    private fun startMediaService() {
        try {
            EcUMediaService.start(this)
        } catch (_: Exception) {
        }
    }

    private fun stopMediaService() {
        try {
            EcUMediaService.stop(this)
        } catch (_: Exception) {
        }
    }

    private fun handleLinkLost() {
        append(LogDir.Info, "Link lost - tidak ada balasan echo >17 dtk")
        client.close()
        scheduleReconnect("link lost")
    }

    private fun scheduleReconnect(reason: String) {
        val address = EcURepository.state.value.deviceAddress ?: run { failStop("Tidak ada alamat untuk reconnect"); return }
        if (reconnectJob?.isActive == true) return
        setStatus(ConnStatus.Reconnecting, error = "Reconnect ($reason)")
        reconnectJob = scope.launch {
            while (isActive) {
                val d = reconnectPolicy.nextDelayMs()
                if (d == null) {
                    setStatus(ConnStatus.Error, error = "Reconnect otomatis habis ($reason). Tekan Connect.")
                    append(LogDir.Info, "Reconnect otomatis habis")
                    stopEverything()
                    return@launch
                }
                append(LogDir.Info, "Reconnect dalam ${d / 1000}s...")
                delay(d)
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val bt = adapter() ?: return@withContext false
                        client.connect(bt.getRemoteDevice(address))
                    } catch (e: Exception) {
                        false
                    }
                }
                if (ok) {
                    splitter.reset()
                    reconnectPolicy.reset()
                    sendFrames(session.onConnected(System.currentTimeMillis()))
                    EcURepository.updateStatus(statusOfSession())
                    append(LogDir.Info, "Reconnect berhasil")
                    refreshNotification(force = true)
                    startMediaService()
                    startTick()
                    return@launch
                }
            }
        }
    }

    // ------------------------------------------------------------- EcURepository.EcULink

    override fun sendRawLine(line: String) {
        scope.launch { sendFrames(listOf(line)) }
    }

    override fun requestLive() {
        scope.launch {
            if (!session.connected) return@launch
            sendFrames(session.requestLive(System.currentTimeMillis()))
            EcURepository.updateStatus(statusOfSession())
            append(LogDir.Info, "Meminta mode live...")
        }
    }

    override fun stopLive() {
        scope.launch {
            if (!session.connected) return@launch
            sendFrames(session.stopLive(System.currentTimeMillis()))
            EcURepository.updateStatus(statusOfSession())
        }
    }

    // ------------------------------------------------------------------ notifikasi

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_desc) }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        lastNotifMs = System.currentTimeMillis()
    }

    /** Update teks notifikasi; throttle1 dtk kecuali dipaksa. Widget sudah di-push oleh EcURepository. */
    private fun refreshNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifMs < 1000) return
        lastNotifMs = now
        val text = when (EcURepository.state.value.status) {
            ConnStatus.Streaming -> getString(R.string.notif_live, EcURepository.statusLine())
            else -> EcURepository.statusLine()
        }
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    // ------------------------------------------------------------------ lifecycle

    private fun setStatus(status: ConnStatus, error: String? = null) {
        EcURepository.updateStatus(status, error = error)
        refreshNotification(force = true)
    }

    private fun append(dir: LogDir, text: String) = EcURepository.appendLog(dir, text)

    private fun teardown() {
        tickJob?.cancel()
        reconnectJob?.cancel()
        session.onDisconnected()
        splitter.reset()
        client.close()
        stopMediaService()
    }

    private fun failStop(message: String) {
        EcURepository.updateStatus(ConnStatus.Error, error = message)
        teardown()
        stopEverything()
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        EcURepository.link = null
        val st = EcURepository.state.value.status
        // reset hanya utk status koneksi yang masih "hidup" (biar Error/Permission terbaca UI)
        if (st != ConnStatus.Disconnected && st != ConnStatus.Error && st != ConnStatus.PermissionNeeded) {
            EcURepository.reset()
        }
        scope.cancel()
        client.close()
        super.onDestroy()
    }

    private fun statusOfSession(): ConnStatus = when (session.state) {
        SessionState.Idle -> if (client.isConnected) ConnStatus.Handshaking else ConnStatus.Disconnected
        SessionState.Handshaking -> ConnStatus.Handshaking
        SessionState.LiveRequested -> ConnStatus.LiveRequested
        SessionState.Streaming -> ConnStatus.Streaming
    }
}

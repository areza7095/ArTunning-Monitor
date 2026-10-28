package id.arvoid.artunningmonitor.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Transport Bluetooth SPP (RFCOMM) — HANYA lewat byte, tidak tahu protokol.
 *
 * Urutan percobaan koneksi (semua hasil dari docs/en/juken5-protocol-spec.md bagian 8.3):
 *  1. UUID SPP standar00001101-0000-1000-8000-00805F9B34FB (secure)
 *  2. SPP standar (insecure)
 *  3. UUID custom ECU2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4 — bukti ApplicationUpdater
 *     SerialCommunication.mUUID (secure)
 *  4. UUID custom (insecure)
 *
 * Pemanggil wajib sudah memiliki BLUETOOTH_CONNECT (API31+) — lihat [PermissionHelper];
 * SecurityException diteruskan apa adanya agar UI bisa meminta izin.
 */
class SppClient {

    interface Listener {
        /** Dipanggil dari thread baca untuk setiap paket byte yang masuk. */
        fun onBytes(data: ByteArray)

        /** Koneksi putus TANPA diminta (stream berakhir). Close() sengaja tidak memicu ini. */
        fun onClosed(reason: String)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var running: Boolean = false

    var isConnected: Boolean = false
        private set

    /** Alamat perangkat yang sedang/sudah terakhir dihubungi (untuk auto-reconnect). */
    var connectedAddress: String? = null
        private set

    fun bondedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> = try {
        adapter.bondedDevices?.toList() ?: emptyList()
    } catch (se: SecurityException) {
        emptyList()
    }

    /**
     * Coba connect ke [device] dengan berurutan (4 kombinasi UUID x secure).
     * @return true bila socket terbuka dan loop baca berjalan.
     */
    @SuppressLint("MissingPermission") // pastikan PermissionHelper.hasPermissions() == true sebelum memanggil
    fun connect(device: BluetoothDevice): Boolean {
        close(notify = false)
        val attempts = listOf(
            SPP_UUID to true,
            SPP_UUID to false,
            CUSTOM_UUID to true,
            CUSTOM_UUID to false,
        )
        for ((uuid, secure) in attempts) {
            val s = try {
                if (secure) device.createRfcommSocketToServiceRecord(uuid)
                else device.createInsecureRfcommSocketToServiceRecord(uuid)
            } catch (se: SecurityException) {
                throw se // izin belum ada -> UI yang minta, bukan gagal koneksi
            } catch (_: IOException) {
                continue
            }
            try {
                s.connect()
                socket = s
                running = true
                isConnected = true
                connectedAddress = device.address
                startReadLoop(s)
                return true
            } catch (_: IOException) {
                try {
                    s.close()
                } catch (_: IOException) {
                }
            }
        }
        return false
    }

    /** Tulis byte mentah ke ECU. lempar IOException bila tidak tersambung/gagal. */
    @SuppressLint("MissingPermission")
    fun write(bytes: ByteArray) {
        val s = socket ?: throw IOException("not connected")
        val out = s.outputStream
        out.write(bytes)
        out.flush()
    }

    /** Tutup sengaja; TIDAK memicu [Listener.onClosed] — pemanggil mengatur state sendiri. */
    fun close() = close(notify = false)

    private fun close(notify: Boolean) {
        val wasRunning = running
        running = false
        isConnected = false
        val s = socket
        socket = null
        try {
            s?.close()
        } catch (_: IOException) {
        }
        readThread = null
        if (notify && wasRunning) listener?.onClosed("closed")
    }

    private var readThread: Thread? = null

    @SuppressLint("MissingPermission")
    private fun startReadLoop(s: BluetoothSocket) {
        val input: InputStream = s.inputStream
        readThread = Thread({
            val buf = ByteArray(1024)
            var endedGracefully = false
            try {
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) {
                        endedGracefully = true
                        break
                    }
                    if (n > 0) listener?.onBytes(buf.copyOf(n))
                }
            } catch (_: IOException) {
                // stream terputus
            } finally {
                val notify = running && !endedGracefully || (running && endedGracefully)
                running = false
                isConnected = false
                if (socket === s) {
                    socket = null
                    try {
                        s.close()
                    } catch (_: IOException) {
                    }
                }
                if (notify) listener?.onClosed("stream ended")
            }
        }, "spp-read").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        /** UUID Serial Port Profile standar (Bluetooth SIG). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** UUID custom ECU BRT — bukti ApplicationUpdater SerialCommunication.mUUID. */
        val CUSTOM_UUID: UUID = UUID.fromString("2DA0CC0E-1927-4CE8-A6BF-1B7DAF4BB9D4")
    }
}

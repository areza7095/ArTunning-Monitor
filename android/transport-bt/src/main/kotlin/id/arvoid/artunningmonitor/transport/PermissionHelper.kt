package id.arvoid.artunningmonitor.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Izin Bluetooth per level API (spec bagian 8.3 / best practice Android):
 *  - API31+ (Android12): BLUETOOTH_CONNECT (pairing/koneksi) + BLUETOOTH_SCAN.
 *    Scan memakai flag neverForLocation di manifest sehingga tidak butuh lokasi.
 *  - API<=30: permission BT bersifat install-time; untuk DISCOVERY butuh
 *    ACCESS_FINE_LOCATION runtime.
 */
object PermissionHelper {

    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean = requiredRuntimePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

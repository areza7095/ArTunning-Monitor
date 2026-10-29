package id.arvoid.artunningmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.arvoid.artunningmonitor.core.EcUMediaService
import id.arvoid.artunningmonitor.ui.AppRoot
import id.arvoid.artunningmonitor.ui.theme.ArTunningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Supaya persist/push widget dari repository aktif walau service belum hidup.
        EcURepository.init(applicationContext)
        // Start media session segera — Android Auto butuh MediaBrowserService aktif
        // agar app muncul di daftar Media (sebelum connect ECU pun sudah terlihat).
        EcUMediaService.start(this)
        setContent {
            ArTunningTheme {
                AppRoot()
            }
        }
    }
}

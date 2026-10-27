package id.arvoid.artunningmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.arvoid.artunningmonitor.ui.AppRoot
import id.arvoid.artunningmonitor.ui.theme.ArTunningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArTunningTheme {
                AppRoot()
            }
        }
    }
}

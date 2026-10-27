package id.arvoid.artunningmonitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.transport.PermissionHelper
import id.arvoid.artunningmonitor.ui.connect.ConnectScreen
import id.arvoid.artunningmonitor.ui.console.ConsoleScreen
import id.arvoid.artunningmonitor.ui.live.LiveScreen

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Auto-pindah ke tab Live saat streaming MULAI (hanya pada transisi masuk) —
    // jadi gauge langsung terlihat tanpa klik; tidak lompat saat user di tab lain
    // selagi stream berjalan.
    var streamingBefore by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        val streaming = state.status == ConnStatus.Streaming
        if (streaming && !streamingBefore) tab = 1
        streamingBefore = streaming
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.refreshPermissions()
        vm.refreshBonded()
    }
    val requestPermissions: () -> Unit = {
        permissionLauncher.launch(PermissionHelper.requiredRuntimePermissions())
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connect)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_live)) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_console)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ConnectScreen(state, vm, requestPermissions)
                1 -> LiveScreen(state, vm, requestPermissions)
                else -> ConsoleScreen(state, vm)
            }
        }
    }
}

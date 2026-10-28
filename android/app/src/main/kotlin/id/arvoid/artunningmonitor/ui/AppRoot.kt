package id.arvoid.artunningmonitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.transport.PermissionHelper
import id.arvoid.artunningmonitor.ui.connect.ConnectScreen
import id.arvoid.artunningmonitor.ui.console.ConsoleScreen
import id.arvoid.artunningmonitor.ui.diag.DiagScreen
import id.arvoid.artunningmonitor.ui.live.LiveScreen
import id.arvoid.artunningmonitor.ui.theme.Forest
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import id.arvoid.artunningmonitor.ui.theme.WarningAmber
import java.util.Locale

/**
 * Shell aplikasi: top bar brand NomadKit + navigasi 4 tab.
 * Aturan design system: indikator tab warna Sand; status selalu ikon + teks (di layar masing-masing).
 * Saat Streaming, top bar menampilkan mini RPM badge (info live tanpa buka tab Live).
 */
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
        topBar = {
            NomadTopBar(
                status = state.status,
                rpm = state.telemetry?.rpm,
                liveActive = state.status == ConnStatus.Streaming,
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sand,
                    selectedTextColor = Sand,
                    indicatorColor = Sand.copy(alpha = 0.16f),
                )
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connect)) },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_live)) },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_diag)) },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_console)) },
                    colors = navColors,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ConnectScreen(state, vm, requestPermissions)
                1 -> LiveScreen(state, vm, requestPermissions)
                2 -> DiagScreen(state, vm)
                else -> ConsoleScreen(state, vm)
            }
        }
    }
}

/** Top bar brand: titik Sand + nama app + (opsional) mini RPM badge — identitas visual NomadKit. */
@Composable
private fun NomadTopBar(status: ConnStatus, rpm: Float?, liveActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = when (status) {
                            ConnStatus.Streaming -> Forest
                            ConnStatus.Reconnecting,
                            ConnStatus.Connecting,
                            ConnStatus.Handshaking,
                            ConnStatus.LiveRequested,
                            -> WarningAmber
                            ConnStatus.Error,
                            ConnStatus.PermissionNeeded,
                            -> MaterialTheme.colorScheme.error
                            ConnStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            // Mini RPM badge — hanya saat live/streaming, angka kecil di pojok kanan
            if (liveActive && rpm != null) {
                MiniRpmBadge(rpm)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Badge RPM mini di top bar — pill kecil, angka bold, tint Sand. */
@Composable
private fun MiniRpmBadge(rpm: Float) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Sand,
                shape = RoundedCornerShape(999.dp),
            )
            .background(Sand.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "%,.0f", rpm),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Sand,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = "rpm",
                style = MaterialTheme.typography.labelMedium,
                color = Sand.copy(alpha = 0.8f),
            )
        }
    }
}

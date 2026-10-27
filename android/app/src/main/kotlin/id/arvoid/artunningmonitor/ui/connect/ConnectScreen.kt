package id.arvoid.artunningmonitor.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.AppUiState
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.DeviceItem
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.transport.PermissionHelper
import id.arvoid.artunningmonitor.ui.StatusCard
import id.arvoid.artunningmonitor.ui.SkeletonBox
import id.arvoid.artunningmonitor.ui.theme.Sand

@Composable
fun ConnectScreen(
    state: AppUiState,
    vm: MainViewModel,
    requestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var pendingAddress by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manualAddress by remember { mutableStateOf("") }

    fun tryConnect(item: DeviceItem) {
        if (PermissionHelper.hasAll(context)) {
            vm.connect(item.address, item.name)
        } else {
            pendingAddress = item.address to item.name
            requestPermissions()
        }
    }

    // Izin sudah diberikan dan ada permintaan koneksi tertunda -> lanjutkan.
    LaunchedEffect(state.permissionsGranted) {
        if (state.permissionsGranted && pendingAddress != null) {
            val (addr, name) = pendingAddress!!
            pendingAddress = null
            vm.connect(addr, name)
        }
    }

    val connected = state.status != ConnStatus.Disconnected && state.status != ConnStatus.Error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state)

        if (state.status == ConnStatus.Connecting || state.status == ConnStatus.Handshaking) {
            SkeletonBox(Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (PermissionHelper.hasAll(context)) vm.startDiscovery() else requestPermissions()
                },
                enabled = !state.scanning && !connected,
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = stringResource(R.string.cd_scan))
                Spacer(Modifier.width(6.dp))
                Text(if (state.scanning) "Scanning…" else stringResource(R.string.btn_scan))
            }
            OutlinedButton(
                onClick = {
                    vm.refreshPermissions()
                    vm.refreshBonded()
                },
                enabled = !connected,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_refresh))
            }
            if (connected) {
                Button(
                    onClick = { vm.disconnect() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.btn_disconnect))
                }
            }
        }

        Text(
            text = "Perangkat tersedia",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val items = state.bonded + state.discovered.filter { d ->
            state.bonded.none { it.address == d.address }
        }

        if (items.isEmpty()) {
            SkeletonBox(Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.empty_device_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.address }) { item ->
                    DeviceRow(
                        item = item,
                        bonded = state.bonded.any { it.address == item.address },
                        enabled = !connected,
                        onConnect = { tryConnect(item) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.hint_device_address)) },
            placeholder = { Text("AA:BB:CC:DD:EE:FF", fontFamily = FontFamily.Monospace) },
        )
        Button(
            onClick = {
                val addr = manualAddress.trim().uppercase()
                if (addr.isNotEmpty()) tryConnect(DeviceItem(addr, addr))
            },
            enabled = manualAddress.isNotBlank() && !connected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Link, contentDescription = stringResource(R.string.cd_connect))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.btn_connect))
        }
    }
}

@Composable
private fun DeviceRow(
    item: DeviceItem,
    bonded: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.address + if (bonded) "  •  paired" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Button(
                onClick = onConnect,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sand,
                    contentColor = id.arvoid.artunningmonitor.ui.theme.OnSand,
                ),
            ) {
                Text(stringResource(R.string.btn_connect))
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

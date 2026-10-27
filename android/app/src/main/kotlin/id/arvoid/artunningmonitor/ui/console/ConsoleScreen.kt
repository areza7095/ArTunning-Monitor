package id.arvoid.artunningmonitor.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.AppUiState
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.LogDir
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug console: log RX/TX mentah + input kirim frame manual.
 * Validasi protokol ke ECU asli dilakukan di layar ini (lihat README monorepo).
 */
@Composable
fun ConsoleScreen(state: AppUiState, vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val connected = state.status != ConnStatus.Disconnected && state.status != ConnStatus.Error

    // auto-scroll ke baris terbaru (list dibalik: index0 = paling bawah)
    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Log protokol",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = { vm.clearLog() }) {
                Text(stringResource(R.string.btn_clear))
            }
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            items(state.log.asReversed()) { line ->
                val prefix = when (line.dir) {
                    LogDir.Tx -> "TX"
                    LogDir.Rx -> "RX"
                    LogDir.Info -> "--"
                }
                val color = when (line.dir) {
                    // Prefiks teks selalu ada: warna hanya pelengkap (aturan NomadKit)
                    LogDir.Tx -> id.arvoid.artunningmonitor.ui.theme.Ocean
                    LogDir.Rx -> MaterialTheme.colorScheme.onBackground
                    LogDir.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "${timeFormat.format(Date(line.timeMs))}  $prefix  ${line.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.hint_raw_frame)) },
                enabled = connected,
            )
            Button(
                onClick = {
                    vm.sendRaw(input)
                    input = ""
                },
                enabled = connected && input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sand,
                    contentColor = OnSand,
                ),
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send))
            }
        }
    }
}

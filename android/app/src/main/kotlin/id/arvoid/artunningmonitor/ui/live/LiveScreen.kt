package id.arvoid.artunningmonitor.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.AppUiState
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.ui.StatusCard
import id.arvoid.artunningmonitor.ui.MetricCard
import id.arvoid.artunningmonitor.ui.SkeletonBox
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import java.util.Locale

private data class Metric(val title: String, val value: String, val unit: String)

@Composable
fun LiveScreen(
    state: AppUiState,
    vm: MainViewModel,
    requestPermissions: () -> Unit,
) {
    val t = state.telemetry
    val status = state.status
    val liveActive = status == ConnStatus.LiveRequested || status == ConnStatus.Streaming
    val canStart = status == ConnStatus.Handshaking

    val metrics: List<Metric> = if (t == null) emptyList() else listOf(
        Metric("RPM", String.format(Locale.US, "%,.0f", t.rpm), "rpm"),
        Metric("TPS", String.format(Locale.US, "%.0f", t.tpsPercent), "%"),
        Metric("AFR", String.format(Locale.US, "%.2f", t.afrRaw), "λ"),
        Metric("EOT", String.format(Locale.US, "%.1f", t.eotCelsius), "°C"),
        Metric("MAP", String.format(Locale.US, "%.1f", t.mapValue), "kPa"),
        Metric("IAT", String.format(Locale.US, "%.1f", t.iatCelsius), "°C"),
        Metric("BATT", String.format(Locale.US, "%.2f", t.batteryVolt), "V"),
        Metric("INJ", String.format(Locale.US, "%.3f", t.injAmps), "A"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusCard(state, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!liveActive) {
                Button(
                    onClick = { vm.startLive() },
                    enabled = canStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sand,
                        contentColor = OnSand,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_start_live))
                }
            } else {
                OutlinedButton(
                    onClick = { vm.stopLive() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_stop_live))
                }
            }
        }

        when {
            t != null -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(metrics) { m ->
                    MetricCard(title = m.title, value = m.value, unit = m.unit)
                }
            }

            liveActive -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.waiting_stream),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SkeletonBox(Modifier.fillMaxWidth())
                SkeletonBox(Modifier.fillMaxWidth())
                SkeletonBox(Modifier.fillMaxWidth())
            }

            else -> Text(
                text = stringResource(R.string.live_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.deviceName != null && t != null) {
            Text(
                text = "ECU: ${state.deviceName}  •  core ${t.coreId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

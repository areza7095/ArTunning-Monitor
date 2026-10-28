package id.arvoid.artunningmonitor.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.AppUiState
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.MainViewModel
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.ui.MetricCard
import id.arvoid.artunningmonitor.ui.NomadCard
import id.arvoid.artunningmonitor.ui.SkeletonBox
import id.arvoid.artunningmonitor.ui.StatusCard
import id.arvoid.artunningmonitor.ui.theme.ErrorRed
import id.arvoid.artunningmonitor.ui.theme.Forest
import id.arvoid.artunningmonitor.ui.theme.NomadCardRadius
import id.arvoid.artunningmonitor.ui.theme.Ocean
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import id.arvoid.artunningmonitor.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private data class Metric(val title: String, val value: String, val unit: String, val accent: Color)

/** RPM engine: ON bila RPM di atas ambang idling (telemetri, bukan balasan opcode). */
const val ENGINE_ON_RPM = 200f

/** Warna band AFR sesuai aturan app: band = (int)AFR - 9 (biru<=2, hijau=3, kuning=4, merah>4). */
private fun afrBandColor(bandIndex: Int): Color = when {
    bandIndex <= 2 -> Color(0xFF2563EB) // biru — lean
    bandIndex == 3 -> Forest             // hijau — ideal
    bandIndex == 4 -> WarningAmber       // kuning
    else -> ErrorRed                     // merah — rich
}

private fun afrBandLabel(bandIndex: Int): String = when {
    bandIndex <= 2 -> "LEAN"
    bandIndex == 3 -> "IDEAL"
    bandIndex == 4 -> "RICH"
    else -> "RICH+"
}

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
    val connected = status != ConnStatus.Disconnected && status != ConnStatus.Error &&
        status != ConnStatus.PermissionNeeded
    val engineOn = t != null && t.rpm > ENGINE_ON_RPM
    val afrBand = t?.afrBandIndex

    val metrics: List<Metric> = if (t == null) emptyList() else listOf(
        Metric("RPM", String.format(Locale.US, "%,.0f", t.rpm), "rpm", Sand),
        Metric("TPS", String.format(Locale.US, "%.0f", t.tpsPercent), "%", Ocean),
        Metric("AFR", String.format(Locale.US, "%.2f", t.afrRaw), "λ", afrBandColor(t.afrBandIndex)),
        Metric("EOT", String.format(Locale.US, "%.1f", t.eotCelsius), "°C", WarningAmber),
        Metric("MAP", String.format(Locale.US, "%.1f", t.mapValue), "kPa", Ocean),
        Metric("IAT", String.format(Locale.US, "%.1f", t.iatCelsius), "°C", WarningAmber),
        Metric("BATT", String.format(Locale.US, "%.2f", t.batteryVolt), "V", Forest),
        Metric("PUMP", String.format(Locale.US, "%.3f", t.pumpAmps), "A", Sand),
        Metric("INJ", String.format(Locale.US, "%.3f", t.injAmps), "A", Sand),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusCard(state)

        // ---- RPM hero: kartu besar, angka dominan + AFR band bar ----
        RpmHeroCard(
            rpm = t?.rpm,
            engineOn = engineOn,
            liveActive = liveActive,
            afr = t?.afrRaw,
            afrBand = afrBand,
        )

        // ---- Action row: Start/Stop Live + Engine compact control ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_start_live))
                }
            } else {
                OutlinedButton(
                    onClick = { vm.stopLive() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_stop_live))
                }
            }
            // Engine control compact: 2 tombol ikon kecil
            CompactEngineControl(
                connected = connected,
                engineOn = engineOn,
                liveActive = liveActive,
                onStart = { vm.startEngine() },
                onStop = { vm.stopEngine() },
                onStopLive = { vm.stopLive() },
            )
        }

        when {
            t != null -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(metrics) { m ->
                    MetricCard(
                        title = m.title,
                        value = m.value,
                        unit = m.unit,
                        accent = m.accent,
                    )
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

/** Kartu hero RPM — angka besar + lampu status engine + AFR band bar. */
@Composable
private fun RpmHeroCard(
    rpm: Float?,
    engineOn: Boolean,
    liveActive: Boolean,
    afr: Float?,
    afrBand: Int?,
) {
    Card(
        shape = RoundedCornerShape(NomadCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, if (engineOn) Forest.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "RPM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = rpm?.let { String.format(Locale.US, "%,.0f", it) } ?: "----",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "rpm",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = (if (engineOn) Forest else if (liveActive) Sand else MaterialTheme.colorScheme.surfaceVariant)
                        .copy(alpha = if (engineOn) 0.16f else 1f),
                    border = BorderStroke(
                        1.dp,
                        when {
                            engineOn -> Forest
                            liveActive -> Sand
                            else -> Color.Transparent
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when {
                                engineOn -> Forest
                                liveActive -> Sand
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = when {
                                engineOn -> "ENGINE ON"
                                liveActive -> "MENUNGGU"
                                else -> "OFF"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                engineOn -> Forest
                                liveActive -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            // AFR band bar — indikator visual warna + label teks
            if (afr != null && afrBand != null) {
                Spacer(Modifier.height(8.dp))
                AfrBandBar(afr = afr, bandIndex = afrBand)
            }
        }
    }
}

/** Bar AFR: gradient band + indikator posisi + label teks (warna + teks, bukan warna saja). */
@Composable
private fun AfrBandBar(afr: Float, bandIndex: Int) {
    val bandColor = afrBandColor(bandIndex)
    val bandLabel = afrBandLabel(bandIndex)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "AFR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = String.format(Locale.US, "%.2f", afr),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bandColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, bandColor.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = bandLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = bandColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // Gradient bar: biru → hijau → kuning → merah
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2563EB), // lean
                            Forest,           // ideal
                            WarningAmber,     // rich
                            ErrorRed,         // rich+
                        ),
                    ),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
    }
}

/**
 * Engine control COMPACT — 2 tombol ikon (Start/Stop) di pojok kanan action row.
 * Konfirmasi dialog tetap ada (keamanan); Start saat live akan stop live dulu.
 */
@Composable
private fun CompactEngineControl(
    connected: Boolean,
    engineOn: Boolean,
    liveActive: Boolean,
    onStart: () -> Boolean,
    onStop: () -> Boolean,
    onStopLive: () -> Unit,
) {
    var confirmStart by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackOk by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(4000)
            feedback = null
        }
    }

    fun report(ok: Boolean, opcode: String, note: String = "") {
        feedbackOk = ok
        feedback = if (ok) "✓ $opcode terkirim$note" else "✗ $opcode gagal — tidak tersambung"
    }

    // Ikon Start / Stop engine
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            onClick = { confirmStart = true },
            enabled = connected && !engineOn,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Start Engine",
                tint = if (connected && !engineOn) Sand else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { confirmStop = true },
            enabled = connected && engineOn,
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Stop Engine",
                tint = if (connected && engineOn) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Feedback inline (melayang singkat)
    feedback?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.labelMedium,
            color = if (feedbackOk) Forest else ErrorRed,
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Kirim START_ENGINE?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Frame 5007 (opcode Test protokol Juken) akan dikirim ke ECU. " +
                            "Pastikan motor di stand / netral dan area aman.",
                    )
                    if (liveActive) {
                        Text(
                            "Live mode sedang jalan — stream 160A bisa membuat ECU " +
                                "mengabaikan frame test, jadi live akan dihentikan dulu.",
                            color = WarningAmber,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    scope.launch {
                        if (liveActive) {
                            onStopLive()
                            delay(400)
                        }
                        val ok = onStart()
                        report(ok, "5007", if (liveActive) " (live dihentikan)" else "")
                    }
                }) {
                    Text(if (liveActive) "Stop Live & Kirim" else "Kirim 5007")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Batal") }
            },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Kirim STOP_ENGINE?") },
            text = {
                Text(
                    if (engineOn) {
                        "Engine terdeteksi ON (RPM > 0). Kirim 5003 akan mematikan pembakaran " +
                            "via ECU — konfirmasi hanya jika aman."
                    } else {
                        "Kirim frame 5003 ke ECU."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    scope.launch {
                        val ok = onStop()
                        report(ok, "5003")
                    }
                }) { Text(if (engineOn) "Ya, matikan" else "Kirim 5003") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("Batal") }
            },
        )
    }
}

package id.arvoid.artunningmonitor.ui.diag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import id.arvoid.artunningmonitor.protocol.DiagBit
import id.arvoid.artunningmonitor.protocol.Opcodes
import id.arvoid.artunningmonitor.ui.NomadCard
import id.arvoid.artunningmonitor.ui.SectionHeader
import id.arvoid.artunningmonitor.ui.StatusCard
import id.arvoid.artunningmonitor.ui.theme.ErrorRed
import id.arvoid.artunningmonitor.ui.theme.Forest
import id.arvoid.artunningmonitor.ui.theme.NomadCardRadius
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import id.arvoid.artunningmonitor.ui.theme.WarningAmber

/** Definition actuator test: opcode + label + deskripsi singkat. */
private data class ActuatorDef(val opcode: Int, val label: String, val desc: String)

private val ACTUATORS = listOf(
    ActuatorDef(Opcodes.Test.TEST_PUMP, "Fuel Pump", "5001 — aktifkan pompa bahan bakar"),
    ActuatorDef(Opcodes.Test.TEST_VVA, "VVA", "5002 — aktifkan VVA"),
    ActuatorDef(Opcodes.Test.TEST_LAMP, "Lamp", "5004 — tes lampu"),
    ActuatorDef(Opcodes.Test.TEST_MIL, "MIL", "500A — MIL / check engine"),
    ActuatorDef(Opcodes.Test.TEST_TEMP_IND, "Temp Ind", "500B — indikator temperatur"),
    ActuatorDef(Opcodes.Test.TEST_FAN, "Fan", "500C — kipas radiator"),
    ActuatorDef(Opcodes.Test.TEST_CHOKE, "Choke", "500D — choke"),
    ActuatorDef(Opcodes.Test.TEST_TACHO, "Tacho", "500E — tachometer"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagScreen(state: AppUiState, vm: MainViewModel) {
    val status = state.status
    val connected = status != ConnStatus.Disconnected && status != ConnStatus.Error &&
        status != ConnStatus.PermissionNeeded
    val diag = state.diag

    // Actuator confirm dialog state
    var pendingActuator by remember { mutableStateOf<ActuatorDef?>(null) }
    var lastFeedback by remember { mutableStateOf<String?>(null) }
    var lastFeedbackOk by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state)

        // ---- Section: kalibrasi TPS + model + runtime ----
        SectionHeader(
            title = stringResource(R.string.diag_ecu_model),
            trailing = {
                IconButton(onClick = { vm.requestDiagRefresh() }, enabled = connected) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.diag_refresh))
                }
            },
        )

        NomadCard(accent = Sand) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Model ECU
                InfoRow(
                    icon = { Icon(Icons.Default.Memory, null, tint = Sand, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.diag_ecu_model),
                    value = diag.ecuModel?.modelName ?: stringResource(R.string.diag_empty_val),
                )
                // TPS cal voltage
                InfoRow(
                    icon = { Icon(Icons.Default.Build, null, tint = Sand, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.diag_tps_cal),
                    value = diag.tpsCal?.let {
                        stringResource(R.string.diag_tps_volts, it.volts)
                    } ?: stringResource(R.string.diag_empty_val),
                )
                // Runtime
                InfoRow(
                    icon = { Icon(Icons.Default.Info, null, tint = Sand, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.diag_runtime),
                    value = diag.runtime?.formatHms() ?: stringResource(R.string.diag_empty_val),
                )
                if (diag.ecuModel == null && diag.tpsCal == null && diag.runtime == null) {
                    Text(
                        text = stringResource(R.string.diag_not_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Section: fault bits ----
        SectionHeader(title = stringResource(R.string.diag_fault_bits))
        NomadCard(accent = if (diag.diagFields?.any { it.bit == DiagBit.ERROR } == true) ErrorRed else Forest) {
            if (diag.diagFields == null || diag.diagFields.isEmpty()) {
                Text(
                    text = stringResource(R.string.diag_no_bits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    diag.diagFields.forEach { field ->
                        FaultChip(field)
                    }
                }
            }
        }

        // ---- Section: actuator tests ----
        SectionHeader(title = stringResource(R.string.diag_actuator))
        Text(
            text = stringResource(R.string.diag_actuator_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        lastFeedback?.let { msg ->
            Surface(
                shape = RoundedCornerShape(NomadCardRadius),
                color = (if (lastFeedbackOk) Forest else ErrorRed).copy(alpha = 0.1f),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lastFeedbackOk) Forest else ErrorRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Grid actuator buttons (2 kolom)
        ACTUATORS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { def ->
                    OutlinedButton(
                        onClick = { pendingActuator = def },
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(def.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // Confirm dialog actuator
    pendingActuator?.let { def ->
        AlertDialog(
            onDismissRequest = { pendingActuator = null },
            title = { Text("Kirim ${def.label}?") },
            text = {
                Text("${def.desc}\n\nPastikan motor aman, netral, dan area bebas sebelum mengirim.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingActuator = null
                    val ok = vm.sendActuatorTest(def.opcode)
                    lastFeedbackOk = ok
                    lastFeedback = if (ok) {
                        "✓ ${def.label} (${Integer.toHexString(def.opcode).uppercase()}) terkirim"
                    } else {
                        "✗ ${def.label} gagal — tidak tersambung ke service ECU"
                    }
                }) { Text("Kirim") }
            },
            dismissButton = {
                TextButton(onClick = { pendingActuator = null }) { Text("Batal") }
            },
        )
    }
}

/** Baris info ikon + label + nilai. */
@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Chip status satu bit fault: ikon + teks (warna hanya pelengkap). */
@Composable
private fun FaultChip(field: id.arvoid.artunningmonitor.protocol.DiagField) {
    val (iconTint, bgColor) = when (field.bit) {
        DiagBit.OK -> Forest to Forest.copy(alpha = 0.12f)
        DiagBit.ERROR -> ErrorRed to ErrorRed.copy(alpha = 0.14f)
        DiagBit.NA -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
        DiagBit.RESERVED -> WarningAmber to WarningAmber.copy(alpha = 0.12f)
    }
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = "Bit ${field.index}: ${field.bit.label}",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (field.bit == DiagBit.ERROR) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = bgColor),
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.4f)),
    )
}

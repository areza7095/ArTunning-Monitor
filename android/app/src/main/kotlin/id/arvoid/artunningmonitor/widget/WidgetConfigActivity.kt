package id.arvoid.artunningmonitor.widget

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.protocol.MetricKey
import id.arvoid.artunningmonitor.ui.theme.ArTunningTheme
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand

/**
 * Layar konfigurasi widget (dibuka sistem saat widget ditambahkan).
 * Pilihan metrik disimpan GLOBAL ([WidgetConfig]) lalu render ulang semua widget.
 * Hasil RESULT_OK = widget ditambahkan oleh launcher.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArTunningTheme {
                val context = LocalContext.current
                var selected by remember {
                    mutableStateOf(WidgetConfig.readMetrics(context).toSet())
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.widget_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    MetricKey.ALL.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(key)) selected - key else selected + key
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected.contains(key),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + key else selected - key
                                },
                            )
                            val unit = if (key.unit.isEmpty()) "" else " (${key.unit})"
                            Text(
                                text = "${key.label}$unit",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            // simpan dalam urutan baku MetricKey.ALL
                            val ordered = MetricKey.ALL.filter { selected.contains(it) }
                            WidgetConfig.saveMetrics(context, ordered)
                            TelemetryWidgetUpdater.updateAll(context)
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Sand,
                            contentColor = OnSand,
                        ),
                    ) {
                        Text(stringResource(R.string.widget_config_save))
                    }
                }
            }
        }
    }
}

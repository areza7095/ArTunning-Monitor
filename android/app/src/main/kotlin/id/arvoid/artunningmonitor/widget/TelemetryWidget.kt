package id.arvoid.artunningmonitor.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.background
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.EcURepository
import id.arvoid.artunningmonitor.protocol.MetricFormatter
import id.arvoid.artunningmonitor.protocol.MetricKey
import id.arvoid.artunningmonitor.protocol.MetricSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receiver Glance (wajib utk manifest). */
class TelemetryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TelemetryWidget()
}

/**
 * Penyimpanan pilihan metrik widget — GLOBAL (semua instance widget memakai pilihan yang sama,
 * disimpan dari WidgetConfigActivity). Kunci: [WidgetConfig.PREFS]/[WidgetConfig.KEY].
 */
object WidgetConfig {
    const val PREFS = "telemetry_widget"
    const val KEY = "metrics"

    fun readMetrics(context: Context): List<MetricKey> =
        MetricSet.deserialize(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )

    fun saveMetrics(context: Context, metrics: List<MetricKey>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, MetricSet.serialize(metrics))
            .apply()
    }
}

/** Mendorong render ulang semua instance widget (dipanggil service, throttle mengikuti notifikasi). */
object TelemetryWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                TelemetryWidget().updateAll(appContext)
            } catch (_: Exception) {
                // widget mungkin belum pernah ditambahkan - abaikan
            }
        }
    }
}

// Warna NomadKit (docs/design/nomadkit.md) untuk mode terang/gelap perangkat.
private object W {
    val headerBgDay = Color(0xFFD4A373)      // Sand
    val headerBgNight = Color(0xFF8A6741)    // Sand gelap (turunan)
    val headerTextDay = Color(0xFF2A1D0F)    // OnSand
    val headerTextNight = Color(0xFFF5E6D3)
    val bodyBgDay = Color(0xFFFFFFFF)        // Surface
    val bodyBgNight = Color(0xFF292524)      // Surface gelap (turunan)
    val valueDay = Color(0xFF2B2419)         // OnLight
    val valueNight = Color(0xFFEDE6DB)       // OnDark
    val mutedDay = Color(0xFF6B6154)
    val mutedNight = Color(0xFFA8A29E)
    val liveDay = Color(0xFF166534)          // Forest
    val liveNight = Color(0xFF2E9E63)        // Forest terang (turunan)
    val warnDay = Color(0xFFCA8A04)          // Warning
    val warnNight = Color(0xFFFBBF24)
}

/**
 * Widget home screen telemetry (Glance).
 * - render snapshot terakhir dari [EcURepository] + pilihan metrik (konfigurasi per penambahan widget)
 * - staleness eksplisit: "update N dtk lalu" (widget TIDAK membuka koneksi sendiri;
 *   service foreground yang mendorong updateAll, throttle mengikuti notifikasi ~1 dtk)
 */
class TelemetryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val metrics = WidgetConfig.readMetrics(context)
        val snap = EcURepository.state.value

        provideContent {
            val statusText = when (snap.status) {
                ConnStatus.Streaming -> "LIVE"
                ConnStatus.Connecting,
                ConnStatus.Handshaking,
                ConnStatus.LiveRequested,
                ConnStatus.Reconnecting -> "…"
                ConnStatus.PermissionNeeded -> "IZIN"
                ConnStatus.Error -> "ERR"
                ConnStatus.Disconnected -> "OFF"
            }
            val isLive = snap.status == ConnStatus.Streaming

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(day = W.bodyBgDay, night = W.bodyBgNight),
            ) {
                // header
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(day = W.headerBgDay, night = W.headerBgNight)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "ArTunning",
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = ColorProvider(day = W.headerTextDay, night = W.headerTextNight),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = if (isLive) {
                                ColorProvider(day = W.liveDay, night = W.liveNight)
                            } else {
                                ColorProvider(day = W.headerTextDay, night = W.headerTextNight)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    val rows = metrics.chunked(3)
                    for (row in rows) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            for (m in row) {
                                Column(modifier = GlanceModifier.defaultWeight().padding(end = 6.dp)) {
                                    Text(
                                        text = snap.telemetry?.let { MetricFormatter.value(it, m) } ?: "--",
                                        style = TextStyle(
                                            color = ColorProvider(day = W.valueDay, night = W.valueNight),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    Text(
                                        text = m.label,
                                        style = TextStyle(
                                            color = ColorProvider(day = W.mutedDay, night = W.mutedNight),
                                            fontSize = 10.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    val footer = if (snap.telemetry != null && snap.telemetryAtMs > 0) {
                        val agoSec = ((System.currentTimeMillis() - snap.telemetryAtMs) / 1000).coerceAtLeast(0)
                        "update ${agoSec}s lalu"
                    } else {
                        "belum ada data - buka app"
                    }
                    Text(
                        text = footer,
                        modifier = GlanceModifier.padding(top = 4.dp),
                        style = TextStyle(
                            color = ColorProvider(day = W.mutedDay, night = W.mutedNight),
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
        }
    }
}

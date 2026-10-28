package id.arvoid.artunningmonitor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.arvoid.artunningmonitor.AppUiState
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.ui.theme.NomadCardRadius
import id.arvoid.artunningmonitor.ui.theme.OnSand
import id.arvoid.artunningmonitor.ui.theme.Sand
import id.arvoid.artunningmonitor.ui.theme.WarningAmber

/**
 * Komponen bersama. Penerapan aturan NomadKit:
 *  - setiap status SELALU punya teks + ikon (jangan andalkan warna saja)
 *  - skeleton state untuk masa tunggu (offline-first)
 *  - kartu: radius konsisten + accent bar tipis di kiri (hierarki visual tanpa warna saja)
 */

/**
 * Kartu dasar NomadKit — Surface radius konsisten + outline tipis + elevation halus.
 * Semua kartu di app memakai ini supaya style seragam (radius 16, outline 1dp, elev 2dp).
 */
@Composable
fun NomadCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    accentSide: AccentSide = AccentSide.Top,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NomadCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        if (accent != null && accentSide == AccentSide.Left) {
            Row {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.85f)),
                )
                Box(Modifier.padding(12.dp)) { content() }
            }
        } else {
            Column {
                if (accent != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(accent),
                    )
                }
                Box(Modifier.padding(12.dp)) { content() }
            }
        }
    }
}

/** Posisi accent bar pada [NomadCard]. */
enum class AccentSide { Top, Left }

/**
 * Judul bagian: teks bold + garis tipis pemisah di bawah.
 * Dipakai untuk mengelompokkan konten panjang (mis. layar Diagnosa).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Baris status: ikon + teks utama + sub-teks. Warna HANYA pelengkap, teks selalu ada. */
@Composable
fun StatusCard(state: AppUiState, modifier: Modifier = Modifier) {
    val (icon: ImageVector, title: String, tint: Color) = when (state.status) {
        ConnStatus.PermissionNeeded -> Triple(
            Icons.Default.Lock,
            "Izin Bluetooth dibutuhkan",
            MaterialTheme.colorScheme.error,
        )
        ConnStatus.Disconnected -> Triple(
            Icons.Default.WifiOff,
            "Terputus",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnStatus.Connecting -> Triple(
            Icons.Default.Sync,
            "Menghubungkan…",
            MaterialTheme.colorScheme.secondary,
        )
        ConnStatus.Handshaking -> Triple(
            Icons.Default.Sync,
            "Handshake (kirim 1B00)…",
            MaterialTheme.colorScheme.secondary,
        )
        ConnStatus.LiveRequested -> Triple(
            Icons.Default.HourglassTop,
            "Meminta mode live (160A)…",
            WarningAmber,
        )
        ConnStatus.Streaming -> Triple(
            Icons.Default.CheckCircle,
            "Live streaming",
            id.arvoid.artunningmonitor.ui.theme.Forest,
        )
        ConnStatus.Reconnecting -> Triple(
            Icons.Default.Refresh,
            "Reconnecting…",
            WarningAmber,
        )
        ConnStatus.Error -> Triple(
            Icons.Default.Error,
            "Error",
            MaterialTheme.colorScheme.error,
        )
    }
    val sub = when {
        state.error != null -> state.error!!
        state.deviceName != null -> state.deviceName!!
        else -> state.status.name
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NomadCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // accent bar kiri — hierarki visual; teks+ikon tetap sumber makna utama
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(tint.copy(alpha = 0.85f)),
            )
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Placeholder berdenyut (skeleton) — aturan "design for offline-first with skeleton states". */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NomadCardRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
    ) {
        Spacer(Modifier.height(76.dp))
    }
}

/**
 * Kartu metrik: label + nilai + satuan (nilai selalu diberi LABEL teks — bukan warna saja).
 * [accent] opsional: bar warna tipis di atas kartu (aksen turunan, bukan sumber makna).
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NomadCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Column {
            if (accent != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accent),
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

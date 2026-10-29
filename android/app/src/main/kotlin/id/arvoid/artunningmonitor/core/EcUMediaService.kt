package id.arvoid.artunningmonitor.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import id.arvoid.artunningmonitor.ConnStatus
import id.arvoid.artunningmonitor.EcURepository
import id.arvoid.artunningmonitor.MainActivity
import id.arvoid.artunningmonitor.R
import id.arvoid.artunningmonitor.protocol.MetricFormatter
import id.arvoid.artunningmonitor.protocol.MetricKey
import id.arvoid.artunningmonitor.widget.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MediaBrowserService utk Android Auto (kategori Media).
 *
 * Telemetri live dipublikasikan sebagai "now-playing" track:
 * judul = status + deret metrik (throttle 1 dtk), artwork = tile palet NomadKit.
 * Koneksi Bluetooth tetap dipegang [EcUService]; service ini hanya memantau
 * [EcURepository] dan memperbarui MediaSession.
 *
 * Start dari MainActivity.onCreate (discovery Auto) + EcUService saat connect.
 * MediaStyle notification wajib agar Auto menganggap app "aktif" (API26+).
 *
 * Test: Desktop Head Unit (DHU) + HP (lihat README).
 */
class EcUMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var lastMetaMs = 0L
    private var lastStatus: ConnStatus? = null

    companion object {
        private const val EMPTY_ROOT = "artunning.root"
        private const val ROOT_ID = "artunning"
        private const val LIVE_ID = "artunning.live"
        private const val META_THROTTLE_MS = 1000L
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIF_ID = 42

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, EcUMediaService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                    // background start dibatasi — Auto tetap bind via intent-filter
                }
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(android.content.Intent(context, EcUMediaService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()

        session = MediaSessionCompat(this, "ArTunningMonitor").apply {
            setSessionActivity(
                PendingIntent.getActivity(
                    this@EcUMediaService,
                    0,
                    Intent(this@EcUMediaService, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            isActive = true
        }
        sessionToken = session.sessionToken
        setPlaybackStopped()

        collectJob = scope.launch {
            EcURepository.state.collectLatest { snap ->
                val now = System.currentTimeMillis()
                val statusChanged = snap.status != lastStatus
                if (statusChanged || now - lastMetaMs >= META_THROTTLE_MS) {
                    lastStatus = snap.status
                    lastMetaMs = now
                    publish(snap)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.media_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Foreground wajib dalam 5 dtk — notifikasi MediaStyle agar Auto anggap app aktif. */
    private fun startAsForeground() {
        val text = EcURepository.statusLine()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(text), type)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (this::session.isInitialized) {
            builder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0),
            )
        }
        return builder.build()
    }

    private fun refreshNotification(text: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(text))
    }

    private fun setPlaybackStopped() {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f)
                .build(),
        )
    }

    private fun publish(snap: EcURepository.Snapshot) {
        val isLive = snap.status == ConnStatus.Streaming && snap.telemetry != null
        val metrics = WidgetConfig.readMetrics(this).ifEmpty { MetricKey.DEFAULT }

        val title = when {
            isLive -> MetricFormatter.summary(snap.telemetry!!, metrics)
            snap.status == ConnStatus.Streaming -> "Menunggu data…"
            snap.status == ConnStatus.Connecting ||
                snap.status == ConnStatus.Handshaking ||
                snap.status == ConnStatus.LiveRequested -> "Menghubungkan…"
            snap.status == ConnStatus.Reconnecting -> "Reconnecting…"
            snap.status == ConnStatus.PermissionNeeded -> "Izin Bluetooth dibutuhkan"
            snap.status == ConnStatus.Error -> snap.error ?: "Error"
            else -> "Tidak tersambung"
        }
        val subtitle = snap.deviceName ?: getString(R.string.app_name)

        val art = tileArtwork(isLive)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .build(),
        )
        session.setQueue(emptyList())
        session.setQueueTitle(getString(R.string.app_name))

        val state = when {
            isLive -> PlaybackStateCompat.STATE_PLAYING
            snap.status == ConnStatus.Disconnected -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_BUFFERING
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(state, 0, 1f)
                .build(),
        )
        refreshNotification(title)
    }

    /** Tile artwork sederhana: latar Sand + huruf status (LIVE/OFF) — palet NomadKit. */
    private fun tileArtwork(isLive: Boolean): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            color = if (isLive) 0xFF166534.toInt() else 0xFFD4A373.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
        val fg = Paint().apply {
            color = if (isLive) 0xFFFFFFFF.toInt() else 0xFF2A1D0F.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 72f
            isFakeBoldText = true
        }
        val text = if (isLive) "LIVE" else "ArTunning"
        val ty = size / 2f - (fg.descent() + fg.ascent()) / 2f
        canvas.drawText(text, size / 2f, ty, fg)
        return bmp
    }

    // ------------------------------------------------------------------ browse

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot = BrowserRoot(EMPTY_ROOT, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        // Auto menyaring app dgn browse-tree kosong — return minimal 1 root
        // browsable + sub-item "Live" playable (metadata = telemetri / status).
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId == EMPTY_ROOT) {
            val rootDesc = MediaDescriptionCompat.Builder()
                .setMediaId(ROOT_ID)
                .setTitle(getString(R.string.app_name))
                .setSubtitle("Telemetri ECU Juken")
                .build()
            items.add(
                MediaBrowserCompat.MediaItem(
                    rootDesc,
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
                ),
            )
        } else if (parentId == ROOT_ID) {
            val liveDesc = MediaDescriptionCompat.Builder()
                .setMediaId(LIVE_ID)
                .setTitle("Live")
                .setSubtitle("Telemetri live")
                .build()
            items.add(
                MediaBrowserCompat.MediaItem(
                    liveDesc,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
                ),
            )
        }
        result.sendResult(items)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        session.isActive = false
        session.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

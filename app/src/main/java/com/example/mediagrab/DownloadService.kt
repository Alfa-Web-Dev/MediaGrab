package com.example.mediagrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service: keeps downloads alive when the app is closed and shows ONE notification
 * with overall progress and a "Cancel all" button. Up to Settings.concurrent items run in parallel.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    @Volatile private var lastStartId = 0
    private val lastSummary = AtomicLong(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL_ALL) DownloadCenter.cancelAll()

        // Must be called quickly after startForegroundService().
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            progressNotification(this, "Preparing download…", 0f, ""),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (worker?.isActive != true) {
            worker = scope.launch { runQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        val repo = DownloadRepository(applicationContext)

        // The engine unpacks on first launch; wait for it (max 2 minutes).
        val ready = withTimeoutOrNull(120_000) { MediaGrabApp.instance.engineReady.first { it } } == true

        while (true) {
            // coroutineScope waits until every download launched inside it has finished.
            coroutineScope {
                while (true) {
                    if (!ready) {
                        DownloadCenter.nextQueued()?.let {
                            DownloadCenter.setFailed(it.id, MediaGrabApp.instance.engineError.value ?: "Engine not ready")
                        }
                        if (!DownloadCenter.hasActive()) break
                        continue
                    }
                    val max = Settings.concurrent.value.coerceIn(1, 5)
                    if (DownloadCenter.runningCount() < max) {
                        val item = DownloadCenter.nextQueued()
                        if (item != null) {
                            DownloadCenter.setRunning(item.id)   // claim it before launching
                            launch { process(repo, item) }
                            continue
                        }
                    }
                    if (!DownloadCenter.hasActive()) break
                    delay(300)
                }
            }
            // Returns false if a newer start request arrived meanwhile: then loop again.
            if (stopSelfResult(lastStartId)) break
        }
    }

    private suspend fun process(repo: DownloadRepository, item: DownloadItem) {
        if (Settings.wifiOnly.value && isMetered()) {
            DownloadCenter.setFailed(item.id, "Wi-Fi only is on. Connect to Wi-Fi and retry.")
            notifyFailed(item, "Wi-Fi only is on")
            return
        }
        updateSummary(force = true)

        try {
            val uri = repo.download(item = item, options = Settings.options()) { p, eta ->
                DownloadCenter.setProgress(item.id, p, eta)
                updateSummary(force = false)
            }
            if (DownloadCenter.get(item.id)?.status == Status.CANCELLED) {
                // User cancelled just as it finished: honour the cancel, remove the file.
                uri?.let { runCatching { contentResolver.delete(Uri.parse(it), null, null) } }
                return
            }
            DownloadCenter.setDone(item.id, uri)
            notifyDone(item, uri)
        } catch (e: CancellationException) {
            DownloadCenter.setFailed(item.id, "Interrupted")
            throw e
        } catch (e: Throwable) {
            if (DownloadCenter.get(item.id)?.status == Status.CANCELLED) return
            val msg = friendlyError(e)
            DownloadCenter.setFailed(item.id, msg)
            notifyFailed(item, msg)
        } finally {
            updateSummary(force = true)
        }
    }

    /** One ongoing notification summarising every running download. */
    private fun updateSummary(force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastSummary.get()
        if (!force && now - last < 800) return
        lastSummary.set(now)

        val running = DownloadCenter.items.value.filter { it.status == Status.RUNNING }
        val waiting = DownloadCenter.queuedCount()
        if (running.isEmpty() && waiting == 0) return

        val title = when {
            running.size == 1 -> running[0].title
            running.isEmpty() -> "Waiting to start…"
            else -> "Downloading ${running.size} files"
        }
        val avg = if (running.isEmpty()) 0f else running.map { it.progress }.average().toFloat()
        val extra = if (waiting > 0) "$waiting waiting" else ""
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, progressNotification(this, title, avg, extra))
    }

    private fun notifyDone(item: DownloadItem, uri: String?) {
        val builder = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(item.title)
            .setAutoCancel(true)
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), contentResolver.getType(Uri.parse(uri)) ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(this, item.id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            builder.setContentIntent(openAppIntent(this))
        }
        getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), builder.build())
    }

    private fun notifyFailed(item: DownloadItem, message: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("${item.title}: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n$message"))
            .setContentIntent(openAppIntent(this))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n)
    }

    private fun isMetered(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL_ALL = "com.example.mediagrab.CANCEL_ALL"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Download results", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        fun openAppIntent(context: Context): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun progressNotification(context: Context, title: String, progress: Float, subtitle: String): Notification {
            val pct = (progress * 100).toInt().coerceIn(0, 100)
            val text = if (progress <= 0f) "Starting…" else "$pct%"
            val cancelAll = Intent(context, DownloadService::class.java).apply { action = ACTION_CANCEL_ALL }
            return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(listOf(text, subtitle).filter { it.isNotBlank() }.joinToString("  ·  "))
                .setProgress(100, pct, progress <= 0f)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent(context))
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel all",
                    PendingIntent.getService(context, 7, cancelAll, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
                .build()
        }

        fun friendlyError(e: Throwable): String {
            val m = e.message.orEmpty()
            return when {
                "Unsupported URL" in m -> "This link isn't supported."
                "Sign in" in m || "login" in m.lowercase() -> "This content needs a login, which isn't supported."
                "Private video" in m || "unavailable" in m.lowercase() -> "This content is unavailable."
                "HTTP Error 403" in m || "Unable to extract" in m || "nsig" in m ->
                    "Extraction failed. Update the engine in Settings and retry."
                "No space left" in m -> "Not enough storage space."
                else -> "Something went wrong: ${m.take(140)}"
            }
        }
    }
}

package com.example.mediagrab

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.util.UUID

/**
 * Single source of truth for the queue and history.
 * The UI observes [items]; the foreground service reads and updates it.
 */
object DownloadCenter {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val loaded = runCatching {
            val arr = JSONArray(prefs.getString("items", "[]"))
            (0 until arr.length()).map { DownloadItem.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        // Anything that was queued/running when the app died is marked failed so the user can retry.
        _items.value = loaded.map {
            if (it.status == Status.QUEUED || it.status == Status.RUNNING)
                it.copy(status = Status.FAILED, error = "Interrupted", eta = "")
            else it
        }
        persist()
    }

    fun enqueue(context: Context, url: String, info: MediaInfo, preset: Preset) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = info.title,
            thumbnail = info.thumbnail,
            presetLabel = preset.label,
            audioOnly = preset.audioOnly,
            maxHeight = preset.maxHeight,
            audioFormat = preset.audioFormat,
            sizeBytes = preset.sizeBytes,
        )
        _items.update { it + item }
        persist()
        startService(context)
    }

    fun retry(context: Context, id: String) {
        mutate(id) { it.copy(status = Status.QUEUED, progress = 0f, eta = "", error = null) }
        persist()
        startService(context)
    }

    fun cancel(id: String) {
        val cur = get(id) ?: return
        if (cur.status != Status.QUEUED && cur.status != Status.RUNNING) return
        mutate(id) { it.copy(status = Status.CANCELLED, eta = "", error = null) }
        persist()
        // Stops the yt-dlp process if this item is the one running.
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    fun cancelAll() {
        _items.value.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
            .forEach { cancel(it.id) }
    }

    /** Remove from history, optionally deleting the saved file as well. */
    fun remove(id: String, deleteFile: Boolean) {
        val cur = get(id) ?: return
        if (cur.status == Status.RUNNING) cancel(id)
        if (deleteFile) {
            cur.savedUri?.let { u ->
                runCatching { appContext.contentResolver.delete(Uri.parse(u), null, null) }
            }
        }
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _items.update { list ->
            list.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
        }
        persist()
    }

    // ---- used by the service ----

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun nextQueued(): DownloadItem? = _items.value.firstOrNull { it.status == Status.QUEUED }

    fun runningCount(): Int = _items.value.count { it.status == Status.RUNNING }

    fun queuedCount(): Int = _items.value.count { it.status == Status.QUEUED }

    fun hasActive(): Boolean = _items.value.any { it.status == Status.QUEUED || it.status == Status.RUNNING }

    /** Called by the service the moment it picks an item, so it is never picked twice. */
    fun setRunning(id: String) {
        mutate(id) { it.copy(status = Status.RUNNING, progress = 0f, eta = "", error = null) }
        persist()
    }

    /** Progress updates are frequent, so they are not written to disk. */
    fun setProgress(id: String, progress: Float, eta: String) {
        mutate(id) { if (it.status == Status.RUNNING) it.copy(progress = progress, eta = eta) else it }
    }

    fun setDone(id: String, uri: String?) {
        mutate(id) { it.copy(status = Status.DONE, progress = 1f, eta = "", savedUri = uri, error = null) }
        persist()
    }

    fun setFailed(id: String, message: String) {
        mutate(id) { it.copy(status = Status.FAILED, eta = "", error = message) }
        persist()
    }

    // ---- internals ----

    private fun mutate(id: String, f: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    @Synchronized
    private fun persist() {
        val arr = JSONArray()
        _items.value.takeLast(200).forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }
}

package com.example.mediagrab

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Thin wrapper around the yt-dlp engine. No UI code in here. */
class DownloadRepository(private val context: Context) {

    private val ytdl get() = YoutubeDL.getInstance()

    /** Lightweight copy of the fields we need from each yt-dlp format. */
    private data class Fmt(
        val height: Int,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val ext: String,
        val size: Long,
    )

    /**
     * Ask yt-dlp for metadata and build quality presets from the heights that really exist,
     * each with an estimated file size (video stream + audio stream).
     */
    suspend fun fetchInfo(url: String): MediaInfo = withContext(Dispatchers.IO) {
        // NOTE: the library's getInfo() only exposes "requested_formats" (the 2 default picks),
        // but we need EVERY format to build the quality list and size estimates.
        // So we run yt-dlp ourselves and parse the full JSON.
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
        }
        val root = JSONObject(ytdl.execute(request).out)
        val dur = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() }
        val compat = Settings.compatible.value

        val arr = root.optJSONArray("formats") ?: JSONArray()
        val fmts = (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            val exact = f.optLong("filesize", 0L).takeIf { it > 0 }
                ?: f.optLong("filesize_approx", 0L).takeIf { it > 0 }
            val tbr = f.optDouble("tbr", 0.0)
            val estimated = exact ?: if (tbr > 0 && dur > 0) {
                (tbr * 125.0 * dur).toLong()   // tbr is kbit/s -> bytes/s = *125
            } else 0L
            Fmt(
                height = f.optInt("height", 0),
                hasVideo = f.hasCodec("vcodec"),
                hasAudio = f.hasCodec("acodec"),
                ext = f.optString("ext", ""),
                size = estimated,
            )
        }

        val audioOnly = fmts.filter { !it.hasVideo && it.hasAudio }
        val audioPick = (if (compat) audioOnly.filter { it.ext == "m4a" } else audioOnly)
            .ifEmpty { audioOnly }
            .maxByOrNull { it.size }
        val audioSize = audioPick?.size ?: 0L

        fun videoSize(height: Int): Long {
            val all = fmts.filter { it.hasVideo && it.height == height }
            val pick = (if (compat) all.filter { it.ext == "mp4" } else all)
                .ifEmpty { all }
                .maxByOrNull { it.size } ?: return 0L
            return pick.size + if (pick.hasAudio) 0L else audioSize
        }

        val heights = fmts.filter { it.hasVideo && it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
            .filter { it in STANDARD_HEIGHTS }

        val videoPresets = buildList {
            if (heights.isNotEmpty()) {
                add(Preset("best", "Best available", audioOnly = false, sizeBytes = videoSize(heights.first())))
            } else {
                add(Preset("best", "Best available", audioOnly = false))
            }
            heights.forEach { h ->
                add(Preset("v$h", "${h}p", audioOnly = false, maxHeight = h, sizeBytes = videoSize(h)))
            }
        }
        // MP3 at quality 0 is roughly 245 kbit/s (~30.6 KB/s).
        val mp3Size = if (dur > 0) dur * 30_625L else 0L
        val audioPresets = listOf(
            Preset("mp3", "MP3 audio", audioOnly = true, audioFormat = "mp3", sizeBytes = mp3Size),
            Preset("m4a", "M4A audio", audioOnly = true, audioFormat = "m4a", sizeBytes = audioSize),
        )

        MediaInfo(
            title = root.textOrNull("title") ?: "Untitled",
            uploader = root.textOrNull("uploader") ?: root.textOrNull("channel"),
            thumbnail = root.textOrNull("thumbnail"),
            durationSec = dur,
            presets = videoPresets + audioPresets,
        )
    }

    /**
     * Downloads into app-private cache, then publishes into Downloads/<folder> via MediaStore.
     * The yt-dlp process id is the item id, so [DownloadCenter.cancel] can stop it.
     * Returns the saved file's Uri as a string.
     */
    suspend fun download(
        item: DownloadItem,
        options: DownloadOptions,
        onProgress: (Float, String) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "dl_${item.id}").apply { mkdirs() }
        try {
            val request = YoutubeDLRequest(item.url).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--windows-filenames")
                addOption("--retries", "10")
                addOption("--fragment-retries", "10")
                addOption("--socket-timeout", "20")
                addOption("-o", File(workDir, "%(title).80B [%(id)s].%(ext)s").absolutePath)

                if (options.fastMode) {
                    // Download 8 fragments at once: big speed-up on HLS/DASH sites.
                    addOption("-N", "8")
                }

                if (item.audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", item.audioFormat ?: "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    val h = item.maxHeight
                    val selector = if (h == null) "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]"
                    addOption("-f", selector)
                    if (options.compatible) addOption("-S", "res,ext:mp4:m4a")
                    addOption("--merge-output-format", "mp4")
                }
                if (options.embedMetadata) {
                    addOption("--embed-metadata")
                    if (item.audioOnly && item.audioFormat == "mp3") addOption("--embed-thumbnail")
                }
            }

            ytdl.execute(request, item.id) { progress, etaSec, _ ->
                val p = (progress / 100f).coerceIn(0f, 1f)
                onProgress(p, if (etaSec > 0) formatEta(etaSec) else "")
            }

            val result = workDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in OUTPUT_EXTENSIONS }
                ?.maxByOrNull { it.length() }
                ?: error("Download finished but no output file was found")

            saveToDownloads(result, options.folder)?.toString()
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Update yt-dlp itself. Sites change often, so expose this in the UI. */
    suspend fun updateEngine(): String = withContext(Dispatchers.IO) {
        when (ytdl.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
            YoutubeDL.UpdateStatus.DONE -> "Engine updated"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine already up to date"
            else -> "Update finished"
        }
    }

    private fun saveToDownloads(file: File, folder: String): Uri? {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext == "m4a") "audio/mp4" else "application/octet-stream"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun JSONObject.textOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    /** True when the format has a real codec ("none" or missing means no such stream). */
    private fun JSONObject.hasCodec(key: String): Boolean =
        !isNull(key) && optString(key).let { it.isNotEmpty() && it != "none" }

    private fun formatEta(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m}m ${s}s left" else "${s}s left"
    }

    private companion object {
        val STANDARD_HEIGHTS = setOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
        val OUTPUT_EXTENSIONS = setOf("mp4", "mkv", "webm", "mp3", "m4a", "opus", "ogg", "aac")
    }
}

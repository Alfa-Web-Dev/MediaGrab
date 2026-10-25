package com.example.mediagrab

import org.json.JSONObject

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** A quality choice shown in the picker. [sizeBytes] is an estimate (0 = unknown). */
data class Preset(
    val id: String,
    val label: String,
    val audioOnly: Boolean,
    val maxHeight: Int? = null,
    val audioFormat: String? = null,
    val sizeBytes: Long = 0L,
)

data class MediaInfo(
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Int,
    val presets: List<Preset>,
)

/** One entry in the queue / history. */
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val presetLabel: String,
    val audioOnly: Boolean,
    val maxHeight: Int?,
    val audioFormat: String?,
    val sizeBytes: Long = 0L,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val eta: String = "",
    val error: String? = null,
    val savedUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("title", title)
        put("thumbnail", thumbnail ?: JSONObject.NULL)
        put("presetLabel", presetLabel)
        put("audioOnly", audioOnly)
        put("maxHeight", maxHeight ?: JSONObject.NULL)
        put("audioFormat", audioFormat ?: JSONObject.NULL)
        put("sizeBytes", sizeBytes)
        put("status", status.name)
        put("progress", progress.toDouble())
        put("error", error ?: JSONObject.NULL)
        put("savedUri", savedUri ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem = DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.getString("title"),
            thumbnail = o.str("thumbnail"),
            presetLabel = o.getString("presetLabel"),
            audioOnly = o.getBoolean("audioOnly"),
            maxHeight = if (o.isNull("maxHeight")) null else o.getInt("maxHeight"),
            audioFormat = o.str("audioFormat"),
            sizeBytes = o.optLong("sizeBytes", 0L),
            status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.FAILED),
            progress = o.optDouble("progress", 0.0).toFloat(),
            error = o.str("error"),
            savedUri = o.str("savedUri"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )

        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    }
}

/** Options read from Settings at the moment a download starts. */
data class DownloadOptions(
    val embedMetadata: Boolean,
    val folder: String,
    val fastMode: Boolean,
    val compatible: Boolean,
)

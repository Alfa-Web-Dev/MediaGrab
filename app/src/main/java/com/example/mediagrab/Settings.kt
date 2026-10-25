package com.example.mediagrab

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Simple settings store. Each value is a StateFlow so the UI updates instantly. */
object Settings {
    private lateinit var prefs: SharedPreferences

    /** "best", "1080", "720", "480", "360", "mp3", "m4a" */
    val defaultQuality = MutableStateFlow("best")

    /** 0 = system, 1 = light, 2 = dark */
    val theme = MutableStateFlow(0)
    val dynamicColors = MutableStateFlow(false)
    val embedMetadata = MutableStateFlow(true)
    val wifiOnly = MutableStateFlow(false)
    val folder = MutableStateFlow("MediaGrab")

    /** How many downloads run at the same time (1..5). */
    val concurrent = MutableStateFlow(3)

    /** Download several fragments in parallel (much faster for HLS/DASH sites). */
    val fastMode = MutableStateFlow(true)

    /** Prefer H.264 + AAC (MP4/M4A) so files play on every phone and TV. */
    val compatible = MutableStateFlow(true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        defaultQuality.value = prefs.getString("quality", "best") ?: "best"
        theme.value = prefs.getInt("theme", 0)
        dynamicColors.value = prefs.getBoolean("dynamic", false)
        embedMetadata.value = prefs.getBoolean("embed", true)
        wifiOnly.value = prefs.getBoolean("wifi", false)
        folder.value = sanitizeFolder(prefs.getString("folder", "MediaGrab"))
        concurrent.value = prefs.getInt("concurrent", 3).coerceIn(1, 5)
        fastMode.value = prefs.getBoolean("fast", true)
        compatible.value = prefs.getBoolean("compat", true)
    }

    fun setDefaultQuality(v: String) { defaultQuality.value = v; prefs.edit().putString("quality", v).apply() }
    fun setTheme(v: Int) { theme.value = v; prefs.edit().putInt("theme", v).apply() }
    fun setDynamicColors(v: Boolean) { dynamicColors.value = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun setEmbedMetadata(v: Boolean) { embedMetadata.value = v; prefs.edit().putBoolean("embed", v).apply() }
    fun setWifiOnly(v: Boolean) { wifiOnly.value = v; prefs.edit().putBoolean("wifi", v).apply() }
    fun setConcurrent(v: Int) { val n = v.coerceIn(1, 5); concurrent.value = n; prefs.edit().putInt("concurrent", n).apply() }
    fun setFastMode(v: Boolean) { fastMode.value = v; prefs.edit().putBoolean("fast", v).apply() }
    fun setCompatible(v: Boolean) { compatible.value = v; prefs.edit().putBoolean("compat", v).apply() }
    fun setFolder(v: String) {
        val clean = sanitizeFolder(v)
        folder.value = clean
        prefs.edit().putString("folder", clean).apply()
    }

    fun options() = DownloadOptions(
        embedMetadata = embedMetadata.value,
        folder = folder.value,
        fastMode = fastMode.value,
        compatible = compatible.value,
    )

    private fun sanitizeFolder(v: String?): String =
        (v ?: "").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { "MediaGrab" }
}

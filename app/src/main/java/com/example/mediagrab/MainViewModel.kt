package com.example.mediagrab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val url: String = "",
    val loadingInfo: Boolean = false,
    val info: MediaInfo? = null,
    val selectedPreset: Preset? = null,
    val message: String? = null,
    val updatingEngine: Boolean = false,
    val engineMessage: String? = null,
)

class MainViewModel : ViewModel() {

    private val app = MediaGrabApp.instance
    private val repo = DownloadRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 0 = Home, 1 = Browser, 2 = Downloads, 3 = Settings */
    val tab = MutableStateFlow(0)

    val engineReady = app.engineReady
    val engineError = app.engineError

    private var fetchJob: Job? = null

    fun onUrlChange(url: String) = _state.update { it.copy(url = url, message = null) }

    fun onPresetSelected(p: Preset) = _state.update { it.copy(selectedPreset = p) }

    /** Fetch info for a link typed on Home or for the page open in the browser. */
    fun fetch(url: String = _state.value.url) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(url = clean, loadingInfo = true, info = null, message = null) }
            runCatching { repo.fetchInfo(clean) }
                .onSuccess { info ->
                    _state.update {
                        it.copy(
                            loadingInfo = false,
                            info = info,
                            selectedPreset = pickPreset(info.presets, Settings.defaultQuality.value),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingInfo = false, message = DownloadService.friendlyError(e)) }
                }
        }
    }

    fun dismissInfo() {
        fetchJob?.cancel()
        _state.update { it.copy(info = null, loadingInfo = false) }
    }

    /** Adds to the queue; the foreground service does the work. */
    fun addToQueue() {
        val s = _state.value
        val info = s.info ?: return
        val preset = s.selectedPreset ?: return
        DownloadCenter.enqueue(app, s.url.trim(), info, preset)
        _state.update { it.copy(message = "Added to queue", info = null, url = "") }
    }

    fun updateEngine() {
        viewModelScope.launch {
            _state.update { it.copy(updatingEngine = true, engineMessage = null) }
            val msg = runCatching { repo.updateEngine() }.getOrElse { "Update failed: ${it.message}" }
            _state.update { it.copy(updatingEngine = false, engineMessage = msg) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Free space where downloads are staged before being copied to Downloads. */
    fun freeSpaceBytes(): Long = app.cacheDir.usableSpace

    private fun pickPreset(presets: List<Preset>, quality: String): Preset? = when (quality) {
        "best" -> presets.firstOrNull()
        "mp3", "m4a" -> presets.firstOrNull { it.id == quality } ?: presets.firstOrNull()
        else -> {
            val h = quality.toIntOrNull()
            presets.filter { !it.audioOnly && it.maxHeight != null && h != null && it.maxHeight <= h }
                .maxByOrNull { it.maxHeight!! }
                ?: presets.firstOrNull()
        }
    }
}

package com.example.mediagrab

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MediaGrabApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError

    override fun onCreate() {
        super.onCreate()
        instance = this
        Settings.init(this)
        DownloadCenter.init(this)
        DownloadService.createChannels(this)

        // Unpacking Python + yt-dlp + ffmpeg takes a moment on first launch, so do it off the main thread.
        scope.launch {
            try {
                YoutubeDL.getInstance().init(this@MediaGrabApp)
                FFmpeg.getInstance().init(this@MediaGrabApp)
                _engineReady.value = true
            } catch (t: Throwable) {
                _engineError.value = "Engine failed to start: ${t.message}"
            }
        }
    }

    companion object {
        lateinit var instance: MediaGrabApp
            private set
    }
}

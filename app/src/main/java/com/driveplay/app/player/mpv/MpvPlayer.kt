package com.driveplay.app.player.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MPV Player wrapper using reflection to call MPVLib from mpv-android-lib.
 *
 * Correct lifecycle:
 *  1. MPVLib.create(context)      — one param only
 *  2. MPVLib.setOptionString(...)  — set options before init
 *  3. MPVLib.init()               — initialize mpv core
 *  4. MPVLib.attachSurface(surface)
 *  5. MPVLib.command(["loadfile", url])
 *  6. MPVLib.destroy()
 */
class MpvPlayer(private val context: Context) {

    private var isInitialized = false
    private var surfaceAttached = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mpvLibClass: Class<*>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    interface EventListener {
        fun onPropertyChange(property: String, value: Any?)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onDurationChanged(durationMs: Long)
        fun onPositionChanged(positionMs: Long)
        fun onError(message: String)
        fun onBuffering(isBuffering: Boolean)
        fun onTracksChanged() {}
    }

    private var eventListener: EventListener? = null

    fun setEventListener(listener: EventListener?) {
        eventListener = listener
    }

    fun initialize() {
        if (isInitialized) return
        try {
            mpvLibClass = Class.forName("is.xyz.mpv.MPVLib")
            val cls = mpvLibClass!!

            // MPVLib.create(context) — one param: Context
            val createMethod = cls.getMethod("create", Context::class.java)
            createMethod.invoke(null, context)

            // Set options BEFORE init()
            setOption("vo", "gpu")
            setOption("gpu-context", "android")
            setOption("opengl-es", "yes")
            setOption("hwdec", "mediacodec-copy")
            setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            setOption("ao", "audiotrack,opensles")
            setOption("tls-verify", "no")
            setOption("tls-ca-file", "")
            setOption("demuxer-max-bytes", "150MiB")
            setOption("demuxer-max-back-bytes", "75MiB")
            setOption("cache", "yes")
            setOption("cache-secs", "120")

            // MPVLib.init() — no params
            val initMethod = cls.getMethod("init")
            initMethod.invoke(null)

            // Observe properties (format: 0=NONE, 3=FLAG, 4=INT64, 5=DOUBLE)
            observeProperty("pause", 3)
            observeProperty("time-pos", 4)
            observeProperty("duration", 4)
            observeProperty("paused-for-cache", 3)
            observeProperty("eof-reached", 3)
            observeProperty("track-list/count", 4)

            isInitialized = true
            Log.d(TAG, "MPV initialized successfully")
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "MPV library not found", e)
            eventListener?.onError("MPV library not found")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            eventListener?.onError("Failed to initialize MPV: ${e.message}")
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        if (!isInitialized) return

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    val cls = mpvLibClass ?: return
                    val method = cls.getMethod("attachSurface", android.view.Surface::class.java)
                    method.invoke(null, holder.surface)
                    surfaceAttached = true
                    setPropertyString("force-window", "yes")
                    Log.d(TAG, "Surface attached")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to attach surface", e)
                    eventListener?.onError("Failed to attach surface: ${e.message}")
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                try {
                    val cls = mpvLibClass ?: return
                    val method = cls.getMethod("detachSurface")
                    method.invoke(null)
                    surfaceAttached = false
                } catch (_: Exception) {}
            }
        })
    }

    fun loadFile(url: String, headers: Map<String, String> = emptyMap()) {
        if (!isInitialized) return
        try {
            if (headers.isNotEmpty()) {
                val headerFields = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                setOption("http-header-fields", headerFields)
            }
            command(arrayOf("loadfile", url, "replace"))
            Log.d(TAG, "Loading file: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load file", e)
            eventListener?.onError("Failed to load file: ${e.message}")
        }
    }

    fun play() {
        if (!isInitialized) return
        setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    fun pause() {
        if (!isInitialized) return
        setPropertyBoolean("pause", true)
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        if (!isInitialized) return
        command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute"))
    }

    fun seekRelative(deltaMs: Long) {
        if (!isInitialized) return
        command(arrayOf("seek", (deltaMs / 1000.0).toString(), "relative"))
    }

    fun setVolume(volume: Int) {
        if (!isInitialized) return
        setPropertyInt("volume", volume.coerceIn(0, 150))
    }

    fun getVolume(): Int = if (isInitialized) getPropertyInt("volume") else 100

    fun setSpeed(speed: Float) {
        if (!isInitialized) return
        setPropertyDouble("speed", speed.toDouble())
    }

    fun setAudioTrack(trackId: Int) {
        if (!isInitialized) return
        setPropertyInt("aid", trackId)
    }

    fun setSubtitleTrack(trackId: Int) {
        if (!isInitialized) return
        setPropertyInt("sid", trackId)
    }

    fun disableSubtitles() {
        if (!isInitialized) return
        setPropertyString("sid", "no")
    }

    fun setSubtitleDelay(delaySeconds: Double) {
        if (!isInitialized) return
        setPropertyDouble("sub-delay", delaySeconds)
    }

    fun addExternalSubtitle(path: String) {
        if (!isInitialized) return
        command(arrayOf("sub-add", path, "auto"))
    }

    fun getTrackCount(): Int = if (isInitialized) getPropertyInt("track-list/count") else 0
    fun getTrackType(index: Int): String = getPropertyString("track-list/$index/type")
    fun getTrackTitle(index: Int): String = getPropertyString("track-list/$index/title")
    fun getTrackLang(index: Int): String = getPropertyString("track-list/$index/lang")
    fun getTrackCodec(index: Int): String = getPropertyString("track-list/$index/codec")
    fun getTrackId(index: Int): Int = getPropertyInt("track-list/$index/id")
    fun isTrackSelected(index: Int): Boolean = getPropertyBoolean("track-list/$index/selected")

    /**
     * Called by native code via EventObserver when properties change.
     * We register via addObserver reflection.
     */
    fun onPropertyChanged(property: String, value: Any?) {
        mainHandler.post {
            when (property) {
                "pause" -> {
                    val paused = value as? Boolean ?: false
                    _isPlaying.value = !paused
                    eventListener?.onPlaybackStateChanged(!paused)
                }
                "time-pos" -> {
                    val secs = when (value) {
                        is Long -> value; is Int -> value.toLong(); is Double -> value.toLong()
                        else -> return@post
                    }
                    val ms = secs * 1000
                    _currentPosition.value = ms
                    eventListener?.onPositionChanged(ms)
                }
                "duration" -> {
                    val secs = when (value) {
                        is Long -> value; is Int -> value.toLong(); is Double -> value.toLong()
                        else -> return@post
                    }
                    val ms = secs * 1000
                    _duration.value = ms
                    eventListener?.onDurationChanged(ms)
                }
                "paused-for-cache" -> {
                    val buffering = value as? Boolean ?: false
                    _isBuffering.value = buffering
                    eventListener?.onBuffering(buffering)
                }
                "eof-reached" -> {
                    if (value as? Boolean == true) _isPlaying.value = false
                }
                "track-list/count" -> {
                    eventListener?.onTracksChanged()
                }
            }
            eventListener?.onPropertyChange(property, value)
        }
    }

    fun destroy() {
        if (!isInitialized) return
        try {
            command(arrayOf("quit"))
            mpvLibClass?.getMethod("destroy")?.invoke(null)
        } catch (_: Exception) {}
        isInitialized = false
    }

    // ──── Internal helpers ────

    private fun command(args: Array<String>) {
        try {
            mpvLibClass?.getMethod("command", Array<String>::class.java)?.invoke(null, args)
        } catch (_: Exception) {}
    }

    private fun setOption(key: String, value: String) {
        try {
            mpvLibClass?.getMethod("setOptionString", String::class.java, String::class.java)
                ?.invoke(null, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyBoolean(key: String, value: Boolean) {
        try {
            mpvLibClass?.getMethod("setPropertyBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                ?.invoke(null, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyInt(key: String, value: Int) {
        try {
            mpvLibClass?.getMethod("setPropertyInt", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(null, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyDouble(key: String, value: Double) {
        try {
            mpvLibClass?.getMethod("setPropertyDouble", String::class.java, Double::class.javaPrimitiveType)
                ?.invoke(null, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyString(key: String, value: String) {
        try {
            mpvLibClass?.getMethod("setPropertyString", String::class.java, String::class.java)
                ?.invoke(null, key, value)
        } catch (_: Exception) {}
    }

    private fun getPropertyInt(key: String): Int {
        return try {
            mpvLibClass?.getMethod("getPropertyInt", String::class.java)
                ?.invoke(null, key) as? Int ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun getPropertyBoolean(key: String): Boolean {
        return try {
            mpvLibClass?.getMethod("getPropertyBoolean", String::class.java)
                ?.invoke(null, key) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    private fun getPropertyString(key: String): String {
        return try {
            mpvLibClass?.getMethod("getPropertyString", String::class.java)
                ?.invoke(null, key) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private fun observeProperty(name: String, format: Int) {
        try {
            mpvLibClass?.getMethod("observeProperty", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(null, name, format)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "MpvPlayer"
    }
}

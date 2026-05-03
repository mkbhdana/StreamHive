package com.mkbhdana.streamhive.player.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
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
    private var surfaceRefreshPending = false
    private var subtitleRendererRefreshPending = false
    private var recoverVideoOnNextAttach = false
    private var videoReloadPending = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mpvLibClass: Class<*>? = null
    private var mpvInstance: Any? = null
    private var currentSurface: Surface? = null
    private var currentSurfaceWidth: Int = 0
    private var currentSurfaceHeight: Int = 0
    private val videoOutputName = "gpu"
    private var libassSubtitlesEnabled = false
    private var overrideAssStylesEnabled = false
    private var decoderMode = "hw+"

    // Pending file load: queued if loadFile() is called before surface is ready
    private var pendingFileUrl: String? = null
    private var pendingFileHeaders: Map<String, String> = emptyMap()

    var onSurfaceReady: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val audioFocusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .build()
    } else null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause()
        } else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN) {
            play()
        }
    }

    private fun requestAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

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

    fun initialize(
        useLibassSubtitles: Boolean = false,
        overrideAssStyles: Boolean = false,
        decoderMode: String = "hw+"
    ) {
        if (isInitialized) return
        libassSubtitlesEnabled = useLibassSubtitles
        overrideAssStylesEnabled = overrideAssStyles
        this.decoderMode = normalizeDecoderMode(decoderMode)
        try {
            mpvLibClass = Class.forName("is.xyz.mpv.MPV")
            val cls = mpvLibClass!!
            mpvInstance = try { cls.getField("INSTANCE").get(null) } catch (e: Exception) { cls.newInstance() }

            // MPVLib.create(context) — one param: Context
            val createMethod = cls.getMethod("create", Context::class.java)
            createMethod.invoke(mpvInstance, context)

            // Set options BEFORE init()
            setOption("vo", videoOutputName)
            setOption("gpu-context", "android")
            setOption("opengl-es", "yes")
            setOption("hwdec", hwdecForDecoderMode(this.decoderMode))
            setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            setOption("hwdec-software-fallback", "yes")
            setOption("ao", "audiotrack,opensles")
            setOption("tls-verify", "no")
            setOption("tls-ca-file", "")
            setOption("demuxer-max-bytes", "150MiB")
            setOption("demuxer-max-back-bytes", "75MiB")
            setOption("cache", "yes")
            setOption("cache-secs", "120")
            setOption("sub-auto", "fuzzy")
            applyAssStyleModeAsOptions()

            // MPVLib.init() — no params
            val initMethod = cls.getMethod("init")
            initMethod.invoke(mpvInstance)

            // Observe properties (format: 0=NONE, 3=FLAG, 4=INT64, 5=DOUBLE)
            observeProperty("pause", 3)
            observeProperty("time-pos", 4)
            observeProperty("duration", 4)
            observeProperty("paused-for-cache", 3)
            observeProperty("eof-reached", 3)
            observeProperty("track-list/count", 4)

            // Register EventObserver via reflection + dynamic Proxy
            registerEventObserver(cls)

            isInitialized = true
            Log.d(TAG, "MPV initialized successfully")
            currentSurface?.takeIf { it.isValid }?.let { surface ->
                attachSurfaceInternal(surface, currentSurfaceWidth, currentSurfaceHeight)
            }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "MPV library not found", e)
            eventListener?.onError("MPV library not found")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            eventListener?.onError("Failed to initialize MPV: ${e.message}")
        }
    }

    /**
     * Dynamically implements MPVLib.EventObserver and registers it via addObserver().
     * This routes native callbacks to our onPropertyChanged() method.
     */
    private fun registerEventObserver(cls: Class<*>) {
        try {
            // Find the inner EventObserver interface
            val observerInterface = cls.declaredClasses.firstOrNull { it.simpleName == "EventObserver" }
            if (observerInterface == null) {
                Log.w(TAG, "EventObserver interface not found in MPVLib")
                return
            }

            // Create a dynamic proxy that routes all calls to onPropertyChanged
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                observerInterface.classLoader,
                arrayOf(observerInterface)
            ) { _, method, args ->
                when (method.name) {
                    "eventProperty" -> {
                        // eventProperty(String property, <T> value)
                        val property = args?.getOrNull(0) as? String ?: return@newProxyInstance null
                        val value = args.getOrNull(1)
                        onPropertyChanged(property, value)
                    }
                    "event" -> {
                        // event(int eventId) — can handle lifecycle events if needed
                        val eventId = args?.getOrNull(0)
                        Log.d(TAG, "MPV event: $eventId")
                    }
                    else -> {
                        // Default: toString, equals, hashCode
                        when (method.name) {
                            "toString" -> return@newProxyInstance "MpvPlayerEventObserver"
                            "hashCode" -> return@newProxyInstance System.identityHashCode(this).hashCode()
                            "equals" -> return@newProxyInstance (args?.getOrNull(0) === this)
                        }
                    }
                }
                null
            }

            // Call MPVLib.addObserver(observer)
            val addObserverMethod = cls.getMethod("addObserver", observerInterface)
            addObserverMethod.invoke(mpvInstance, proxy)
            Log.d(TAG, "EventObserver registered successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register EventObserver: ${e.message}")
        }
    }

    private fun attachSurfaceInternal(surface: Surface, width: Int = currentSurfaceWidth, height: Int = currentSurfaceHeight) {
        try {
            if (!surface.isValid) return
            val previousSurface = currentSurface
            if (width > 0 && height > 0) {
                currentSurfaceWidth = width
                currentSurfaceHeight = height
            }
            currentSurface = surface
            if (!isInitialized) return

            val cls = mpvLibClass ?: return
            if (width > 0 && height > 0) {
                setAndroidSurfaceSize(width, height)
            }
            if (surfaceAttached && previousSurface === surface) {
                restoreVideoOutput()
                reloadVideoTrackIfNeeded()
                return
            }
            if (surfaceAttached && previousSurface !== surface) {
                detachSurfaceInternal(clearSurface = false)
            }
            val method = cls.getMethod("attachSurface", android.view.Surface::class.java)
            method.invoke(mpvInstance, surface)
            surfaceAttached = true
            restoreVideoOutput()
            Log.d(TAG, "Surface attached")
            reloadVideoTrackIfNeeded()

            onSurfaceReady?.invoke()

            pendingFileUrl?.let { url ->
                loadFileInternal(url, pendingFileHeaders)
                pendingFileUrl = null
                pendingFileHeaders = emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface", e)
            eventListener?.onError("Failed to attach surface: ${e.message}")
        }
    }

    private fun attachHolder(holder: SurfaceHolder) {
        attachSurfaceInternal(
            surface = holder.surface,
            width = holder.surfaceFrame.width(),
            height = holder.surfaceFrame.height()
        )
    }

    private fun detachSurfaceInternal(clearSurface: Boolean, disableOutput: Boolean = true) {
        if (!isInitialized) {
            surfaceAttached = false
            if (clearSurface) {
                currentSurface = null
            }
            return
        }
        if (disableOutput) {
            disableVideoOutput()
        }
        if (!surfaceAttached) {
            if (clearSurface) {
                currentSurface = null
            }
            return
        }
        try {
            mpvLibClass?.getMethod("detachSurface")?.invoke(mpvInstance)
            surfaceAttached = false
            if (clearSurface) {
                currentSurface = null
            }
            Log.d(TAG, "Surface detached")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detach surface: ${e.message}")
        }
    }

    fun detachSurfaceForPause() {
        detachSurfaceInternal(clearSurface = false)
    }

    fun refreshSurface() {
        recoverVideoOnNextAttach = true
        val surface = currentSurface ?: return
        if (!surface.isValid || surfaceRefreshPending) return
        surfaceRefreshPending = true
        detachSurfaceInternal(clearSurface = false)
        mainHandler.postDelayed({
            surfaceRefreshPending = false
            if (surface.isValid) {
                attachSurfaceInternal(surface, currentSurfaceWidth, currentSurfaceHeight)
            }
        }, 80)
    }

    fun suspendVideoOutputForTransientView() {
        if (!isInitialized) return
        recoverVideoOnNextAttach = true
        detachSurfaceInternal(clearSurface = false)
    }

    fun recoverVideoOutput() {
        if (!isInitialized) return
        recoverVideoOnNextAttach = true
        refreshSurface()
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        attachSurfaceInternal(surface, width, height)
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        currentSurfaceWidth = width
        currentSurfaceHeight = height
        setAndroidSurfaceSize(width, height)
    }

    fun detachSurface() {
        recoverVideoOnNextAttach = true
        detachSurfaceInternal(clearSurface = true)
    }

    fun attachSurface(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                attachHolder(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                attachHolder(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                detachSurfaceInternal(clearSurface = currentSurface === holder.surface)
            }
        })

        attachHolder(surfaceView.holder)
    }

    fun loadFile(url: String, headers: Map<String, String> = emptyMap()) {
        if (!isInitialized) return
        if (!surfaceAttached) {
            // Queue for later — surface will trigger load when ready
            pendingFileUrl = url
            pendingFileHeaders = headers
            Log.d(TAG, "Surface not ready, queuing file: $url")
            return
        }
        loadFileInternal(url, headers)
    }

    private fun loadFileInternal(url: String, headers: Map<String, String>) {
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
        requestAudioFocus()
        setPropertyBoolean("pause", false)
        _isPlaying.value = true
    }

    fun pause() {
        if (!isInitialized) return
        abandonAudioFocus()
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

    fun setDecoderMode(mode: String) {
        val normalized = normalizeDecoderMode(mode)
        if (decoderMode == normalized) return
        decoderMode = normalized
        applyDecoderMode()
        reloadVideoTrack()
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

    fun setSubScale(scale: Double) {
        if (!isInitialized) return
        setPropertyDouble("sub-scale", scale)
    }

    fun setSubtitleDelay(delaySeconds: Double) {
        if (!isInitialized) return
        setPropertyDouble("sub-delay", delaySeconds)
    }

    fun setSubtitleSpeed(speed: Float) {
        if (!isInitialized) return
        setPropertyDouble("sub-speed", speed.coerceIn(0.25f, 4.0f).toDouble())
    }

    fun setAssStyleOverride(enabled: Boolean) {
        if (!isInitialized) return
        overrideAssStylesEnabled = enabled
        applyAssStyleModeAsProperties()
        refreshSubtitleRenderer()
    }

    fun setSubtitleStyle(
        fontSize: Int,
        color: Long,
        backgroundOpacity: Float,
        position: Int,
        edgeType: String,
        edgeSize: Int,
        outlineColor: Long
    ) {
        if (!isInitialized) return

        setPropertyString("sub-color", mpvColor(color))
        setPropertyString("sub-back-color", mpvColor(0xFF000000, (backgroundOpacity * 255).toInt()))
        setPropertyString("sub-border-color", mpvColor(outlineColor))
        setPropertyString("sub-outline-color", mpvColor(outlineColor))
        setPropertyDouble("sub-font-size", fontSize.coerceIn(10, 48).toDouble())
        setPropertyInt("sub-pos", position.coerceIn(0, 100))
        setPropertyString(
            "sub-border-style",
            if (backgroundOpacity > 0f) "background-box" else "outline-and-shadow"
        )

        val normalizedEdge = edgeType.lowercase()
        val requestedEdgeSize = edgeSize.coerceIn(0, 20)
        when (normalizedEdge) {
            "none" -> {
                setPropertyDouble("sub-border-size", 0.0)
                setPropertyDouble("sub-outline-size", 0.0)
                setPropertyDouble("sub-shadow-offset", 0.0)
            }
            "shadow" -> {
                setPropertyDouble("sub-border-size", if (requestedEdgeSize > 0) 1.0 else 0.0)
                setPropertyDouble("sub-outline-size", if (requestedEdgeSize > 0) 1.0 else 0.0)
                setPropertyDouble("sub-shadow-offset", requestedEdgeSize.toDouble())
            }
            else -> {
                setPropertyDouble("sub-border-size", requestedEdgeSize.toDouble())
                setPropertyDouble("sub-outline-size", requestedEdgeSize.toDouble())
                setPropertyDouble("sub-shadow-offset", 0.0)
            }
        }

        setSubScale((fontSize.coerceIn(10, 48) / 18.0).coerceIn(0.55, 2.7))
        if (overrideAssStylesEnabled) {
            setAssStyleOverrides(
                fontSize = fontSize,
                color = color,
                backgroundOpacity = backgroundOpacity,
                position = position,
                edgeSize = edgeSize,
                outlineColor = outlineColor
            )
            refreshSubtitleRenderer()
        }
    }

    private fun setAssStyleOverrides(
        fontSize: Int,
        color: Long,
        backgroundOpacity: Float,
        position: Int,
        edgeSize: Int,
        outlineColor: Long
    ) {
        val bottomMargin = ((100 - position.coerceIn(0, 100)) * 3).coerceIn(0, 240)
        val outline = edgeSize.coerceIn(0, 20)
        val styleOverrides = listOf(
            "Fontsize=${fontSize.coerceIn(10, 48)}",
            "PrimaryColour=${assColor(color)}",
            "OutlineColour=${assColor(outlineColor)}",
            "BackColour=${assColor(0xFF000000, (backgroundOpacity * 255).toInt())}",
            "BorderStyle=${if (backgroundOpacity > 0f) 3 else 1}",
            "Outline=$outline",
            "Shadow=0",
            "Alignment=2",
            "MarginV=$bottomMargin"
        ).joinToString(",")
        setOption("sub-ass-style-overrides", styleOverrides)
        setPropertyString("sub-ass-style-overrides", styleOverrides)
    }

    private fun applyAssStyleModeAsOptions() {
        if (overrideAssStylesEnabled) {
            setOption("sub-ass", "yes")
            setOption("sub-ass-override", "scale")
            setOption("embeddedfonts", "yes")
        } else {
            setOption("sub-ass", if (libassSubtitlesEnabled) "yes" else "no")
            setOption("sub-ass-override", "no")
            setOption("embeddedfonts", "yes")
            setOption("sub-ass-style-overrides", "")
        }
    }

    private fun applyAssStyleModeAsProperties() {
        if (overrideAssStylesEnabled) {
            setOption("sub-ass", "yes")
            setOption("sub-ass-override", "scale")
            setOption("embeddedfonts", "yes")
            setPropertyString("sub-ass", "yes")
            setPropertyString("sub-ass-override", "scale")
            setPropertyString("embeddedfonts", "yes")
        } else {
            setOption("sub-ass", if (libassSubtitlesEnabled) "yes" else "no")
            setOption("sub-ass-override", "no")
            setOption("embeddedfonts", "yes")
            setOption("sub-ass-style-overrides", "")
            setPropertyString("sub-ass", if (libassSubtitlesEnabled) "yes" else "no")
            setPropertyString("sub-ass-override", "no")
            setPropertyString("embeddedfonts", "yes")
            setPropertyString("sub-ass-style-overrides", "")
        }
    }

    private fun refreshSubtitleRenderer() {
        if (subtitleRendererRefreshPending) return
        subtitleRendererRefreshPending = true
        val selectedSubtitle = getPropertyString("sid")
            .takeIf { it.isNotBlank() && it != "no" }
            ?: getPropertyInt("sid").takeIf { it > 0 }?.toString()
        if (selectedSubtitle == null) {
            subtitleRendererRefreshPending = false
            return
        }
        setPropertyString("sid", "no")
        mainHandler.postDelayed({
            subtitleRendererRefreshPending = false
            setPropertyString("sid", selectedSubtitle)
        }, 80)
    }

    fun addExternalSubtitle(path: String) {
        if (!isInitialized) return
        command(arrayOf("sub-add", path, "auto", path.substringAfterLast('/')))
    }

    fun removeSubtitleTrack(trackId: Int) {
        if (!isInitialized) return
        command(arrayOf("sub-remove", trackId.toString()))
    }

    fun getTrackCount(): Int = if (isInitialized) getPropertyInt("track-list/count") else 0
    fun getTrackType(index: Int): String = getPropertyString("track-list/$index/type")
    fun getTrackTitle(index: Int): String = getPropertyString("track-list/$index/title")
    fun getTrackLang(index: Int): String = getPropertyString("track-list/$index/lang")
    fun getTrackCodec(index: Int): String = getPropertyString("track-list/$index/codec")
    fun getTrackId(index: Int): Int = getPropertyInt("track-list/$index/id")
    fun getTrackExternalFileName(index: Int): String = getPropertyString("track-list/$index/external-filename")
    fun isTrackExternal(index: Int): Boolean = getPropertyBoolean("track-list/$index/external")
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
        abandonAudioFocus()
        try {
            command(arrayOf("quit"))
            mpvLibClass?.getMethod("destroy")?.invoke(mpvInstance)
        } catch (_: Exception) {}
        isInitialized = false
    }

    // ──── Internal helpers ────

    private fun command(args: Array<String>) {
        try {
            mpvLibClass?.getMethod("command", Array<String>::class.java)?.invoke(mpvInstance, args)
        } catch (_: Exception) {}
    }

    private fun setOption(key: String, value: String) {
        try {
            mpvLibClass?.getMethod("setOptionString", String::class.java, String::class.java)
                ?.invoke(mpvInstance, key, value)
        } catch (_: Exception) {}
    }

    private fun setAndroidSurfaceSize(width: Int, height: Int) {
        val size = "${width.coerceAtLeast(1)}x${height.coerceAtLeast(1)}"
        setOption("android-surface-size", size)
        setPropertyString("android-surface-size", size)
    }

    private fun setPropertyBoolean(key: String, value: Boolean) {
        try {
            mpvLibClass?.getMethod("setPropertyBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyInt(key: String, value: Int) {
        try {
            mpvLibClass?.getMethod("setPropertyInt", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyDouble(key: String, value: Double) {
        try {
            mpvLibClass?.getMethod("setPropertyDouble", String::class.java, Double::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (_: Exception) {}
    }

    private fun setPropertyString(key: String, value: String) {
        try {
            mpvLibClass?.getMethod("setPropertyString", String::class.java, String::class.java)
                ?.invoke(mpvInstance, key, value)
        } catch (_: Exception) {}
    }

    private fun getPropertyInt(key: String): Int {
        return try {
            mpvLibClass?.getMethod("getPropertyInt", String::class.java)
                ?.invoke(mpvInstance, key) as? Int ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun getPropertyBoolean(key: String): Boolean {
        return try {
            mpvLibClass?.getMethod("getPropertyBoolean", String::class.java)
                ?.invoke(mpvInstance, key) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    private fun getPropertyString(key: String): String {
        return try {
            mpvLibClass?.getMethod("getPropertyString", String::class.java)
                ?.invoke(mpvInstance, key) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private fun observeProperty(name: String, format: Int) {
        try {
            mpvLibClass?.getMethod("observeProperty", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(mpvInstance, name, format)
        } catch (_: Exception) {}
    }

    private fun restoreVideoOutput() {
        setOption("force-window", "yes")
        setPropertyString("force-window", "yes")
        setPropertyString("vo", videoOutputName)
        applyDecoderMode()
    }

    private fun disableVideoOutput() {
        setPropertyString("vo", "null")
        setOption("force-window", "no")
        setPropertyString("force-window", "no")
    }

    private fun reloadVideoTrackIfNeeded() {
        if (!recoverVideoOnNextAttach || videoReloadPending) return
        recoverVideoOnNextAttach = false
        reloadVideoTrack()
    }

    private fun reloadVideoTrack() {
        if (videoReloadPending) return
        videoReloadPending = true
        mainHandler.postDelayed({
            videoReloadPending = false
            if (!isInitialized || !surfaceAttached) return@postDelayed
            command(arrayOf("video-reload"))
        }, 120)
    }

    private fun applyDecoderMode() {
        val hwdec = hwdecForDecoderMode(decoderMode)
        setOption("hwdec", hwdec)
        setPropertyString("hwdec", hwdec)
        setOption("hwdec-software-fallback", "yes")
        setPropertyString("hwdec-software-fallback", "yes")
    }

    private fun normalizeDecoderMode(mode: String): String {
        return when (mode.lowercase()) {
            "hw", "sw", "hw+", "auto" -> mode.lowercase()
            else -> "hw+"
        }
    }

    private fun hwdecForDecoderMode(mode: String): String {
        return when (normalizeDecoderMode(mode)) {
            "sw" -> "no"
            "hw" -> "mediacodec"
            "auto" -> "auto-copy"
            else -> "mediacodec-copy,auto-copy"
        }
    }

    private fun mpvColor(argb: Long, alphaOverride: Int? = null): String {
        val a = alphaOverride ?: ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return "#%02X%02X%02X%02X".format(
            a.coerceIn(0, 255),
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
    }

    private fun assColor(argb: Long, alphaOverride: Int? = null): String {
        val argbAlpha = alphaOverride ?: ((argb shr 24) and 0xFF).toInt()
        val assAlpha = 255 - argbAlpha.coerceIn(0, 255)
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return "&H%02X%02X%02X%02X".format(
            assAlpha.coerceIn(0, 255),
            b.coerceIn(0, 255),
            g.coerceIn(0, 255),
            r.coerceIn(0, 255)
        )
    }

    companion object {
        private const val TAG = "MpvPlayer"
    }
}

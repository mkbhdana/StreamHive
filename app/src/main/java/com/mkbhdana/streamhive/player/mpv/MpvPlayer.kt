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
 * MPV Player wrapper using reflection to call mpv-android-lib (version 0.1.12).
 * In v0.1.12, the class is `is.xyz.mpv.MPV` (renamed from `MPVLib` in older versions).
 *
 * Correct lifecycle:
 *  1. Utils.copyAssets(context)       — copy bundled fonts/assets
 *  2. MPV.create(context)             — create native mpv context
 *  3. MPV.setOptionString(key, value) — set options before init
 *  4. MPV.init()                      — initialize mpv core
 *  5. MPV.attachSurface(surface)      — attach rendering surface
 *  6. MPV.command(["loadfile", url])  — load media
 *  7. MPV.destroy()                   — tear down
 */
class MpvPlayer(private val context: Context) {

    private var isInitialized = false
    private var surfaceAttached = false
    private var surfaceRefreshPending = false
    private var subtitleRendererRefreshPending = false
    private var recoverVideoOnNextAttach = false
    private var videoReloadPending = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mpvClass: Class<*>? = null
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
        /** Fired once when the current file plays through to its end. */
        fun onEndFile() {}
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
            // ── Step 1: Copy mpv-android-lib's bundled assets (subfont.ttf, etc.) ──
            // This is what mpvKt does via Utils.copyAssets(context)
            try {
                val utilsClass = Class.forName("is.xyz.mpv.Utils")
                // Utils is a Kotlin object — get INSTANCE then call copyAssets
                val utilsInstance = try { utilsClass.getField("INSTANCE").get(null) } catch (_: Exception) { null }
                if (utilsInstance != null) {
                    utilsClass.getMethod("copyAssets", Context::class.java).invoke(utilsInstance, context)
                } else {
                    // Try static call (older versions)
                    utilsClass.getMethod("copyAssets", Context::class.java).invoke(null, context)
                }
                Log.d(TAG, "Utils.copyAssets() completed")
            } catch (e: Exception) {
                Log.w(TAG, "Utils.copyAssets() failed (non-fatal): ${e.message}")
            }

            // ── Step 2: Get MPV class and instance ──
            mpvClass = Class.forName("is.xyz.mpv.MPV")
            val cls = mpvClass!!
            mpvInstance = try {
                cls.getField("INSTANCE").get(null)
            } catch (_: Exception) {
                cls.getDeclaredConstructor().newInstance()
            }

            // ── Step 3: MPV.create(context) ──
            cls.getMethod("create", Context::class.java).invoke(mpvInstance, context)

            // ── Step 4: Set config-dir and cache-dir (matching BaseMPVView.initialize) ──
            val configDir = context.filesDir.path
            val cacheDir = context.cacheDir.path
            setOption("config", "yes")
            setOption("config-dir", configDir)
            setOption("gpu-shader-cache-dir", cacheDir)
            setOption("icc-cache-dir", cacheDir)

            // ── Step 5: Set video/audio/network options BEFORE init() ──
            setOption("vo", videoOutputName)
            setOption("gpu-context", "android")
            setOption("opengl-es", "yes")
            setOption("hwdec", hwdecForDecoderMode(this.decoderMode))
            setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            setOption("ao", "audiotrack,opensles")
            setOption("tls-verify", "no")
            setOption("tls-ca-file", "")
            setOption("demuxer-max-bytes", "150MiB")
            setOption("demuxer-max-back-bytes", "75MiB")
            setOption("cache", "yes")
            setOption("cache-secs", "120")

            // ── Step 6: Subtitle options (matching mpvKt setupSubtitlesOptions) ──
            setOption("sub-auto", "fuzzy")
            val fontsDir = java.io.File(configDir, "fonts")
            if (!fontsDir.exists()) fontsDir.mkdirs()
            setOption("sub-fonts-dir", fontsDir.absolutePath + "/")
            setOption("sub-font-size", "55")
            setOption("sub-color", "#FFFFFFFF")
            setOption("sub-border-color", "#FF000000")
            setOption("sub-back-color", "#00000000")
            setOption("sub-border-size", "2")
            setOption("sub-shadow-offset", "1")
            setOption("sub-pos", "90")
            setOption("sub-border-style", "outline-and-shadow")
            applyAssStyleModeAsOptions()

            // ── Step 7: MPV.init() ──
            cls.getMethod("init").invoke(mpvInstance)
            Log.d(TAG, "MPV.init() completed")

            // ── Step 8: Post-init properties (matching mpvKt/BaseMPVView) ──
            setPropertyBoolean("keep-open", true)
            setPropertyBoolean("input-default-bindings", true)

            // ── Step 9: Observe properties ──
            // Format constants: FLAG=3, INT64=4, DOUBLE=5, STRING=7
            observeProperty("pause", 3)
            observeProperty("time-pos", 4)
            observeProperty("duration", 4)
            observeProperty("paused-for-cache", 3)
            observeProperty("eof-reached", 3)
            observeProperty("track-list/count", 4)

            // ── Step 10: Register EventObserver ──
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
     * Dynamically implements MPV.EventObserver and registers it via addObserver().
     */
    private fun registerEventObserver(cls: Class<*>) {
        try {
            val observerInterface = cls.declaredClasses.firstOrNull { it.simpleName == "EventObserver" }
            if (observerInterface == null) {
                Log.w(TAG, "EventObserver interface not found in MPV class")
                return
            }

            val proxyHolder = arrayOfNulls<Any>(1)
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                observerInterface.classLoader,
                arrayOf(observerInterface)
            ) { _, method, args ->
                when (method.name) {
                    "eventProperty" -> {
                        val property = args?.getOrNull(0) as? String ?: return@newProxyInstance null
                        val value = args.getOrNull(1)
                        onPropertyChanged(property, value)
                    }
                    "event" -> {
                        val eventId = args?.getOrNull(0)
                        Log.d(TAG, "MPV event: $eventId")
                    }
                    "toString" -> return@newProxyInstance "MpvPlayerEventObserver"
                    "hashCode" -> return@newProxyInstance System.identityHashCode(proxyHolder[0])
                    "equals" -> return@newProxyInstance (args?.getOrNull(0) === proxyHolder[0])
                }
                null
            }
            proxyHolder[0] = proxy

            val addObserverMethod = cls.getMethod("addObserver", observerInterface)
            addObserverMethod.invoke(mpvInstance, proxy)
            Log.d(TAG, "EventObserver registered successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register EventObserver: ${e.message}")
        }
    }

    // ──── Surface management ────

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
            mpvClass?.getMethod("attachSurface", Surface::class.java)?.invoke(mpvInstance, surface)
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
            if (clearSurface) currentSurface = null
            return
        }
        if (disableOutput) disableVideoOutput()
        if (!surfaceAttached) {
            if (clearSurface) currentSurface = null
            return
        }
        try {
            mpvClass?.getMethod("detachSurface")?.invoke(mpvInstance)
            surfaceAttached = false
            if (clearSurface) currentSurface = null
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
            override fun surfaceCreated(holder: SurfaceHolder) { attachHolder(holder) }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { attachHolder(holder) }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                detachSurfaceInternal(clearSurface = currentSurface === holder.surface)
            }
        })
        attachHolder(surfaceView.holder)
    }

    // ──── Playback controls ────

    fun loadFile(url: String, headers: Map<String, String> = emptyMap()) {
        if (!isInitialized) return
        if (!surfaceAttached) {
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

    // ──── Track selection ────

    fun setAudioTrack(trackId: Int) {
        if (!isInitialized) return
        setPropertyInt("aid", trackId)
    }

    fun setSubtitleTrack(trackId: Int) {
        if (!isInitialized) return
        Log.d(TAG, "Setting sid=$trackId, sub-visibility=yes")
        if (trackId == -1) {
            setPropertyString("sid", "no")
        } else {
            setPropertyInt("sid", trackId)
        }
        setPropertyString("sub-visibility", "yes")
    }

    fun disableSubtitles() {
        if (!isInitialized) return
        Log.d(TAG, "Disabling subtitles (sid=no)")
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
        outlineColor: Long,
        scale: Float = 1.0f,
        font: String = "sans-serif",
        bold: Boolean = false,
        italic: Boolean = false,
        alignment: String = "center"
    ) {
        if (!isInitialized) return

        val colorStr = mpvColor(color)
        val backColorStr = mpvColor(0xFF000000, (backgroundOpacity * 255).toInt())

        setPropertyString("sub-font", font)
        setPropertyString("sub-bold", if (bold) "yes" else "no")
        setPropertyString("sub-italic", if (italic) "yes" else "no")
        setPropertyString("sub-justify", alignment)
        setPropertyString("sub-color", colorStr)
        setPropertyString("sub-back-color", backColorStr)
        setPropertyString("sub-border-color", mpvColor(outlineColor))
        setPropertyString("sub-font-size", fontSize.coerceIn(10, 100).toString())
        setPropertyString("sub-pos", position.coerceIn(0, 100).toString())
        setPropertyString(
            "sub-border-style",
            if (backgroundOpacity > 0f) "background-box" else "outline-and-shadow"
        )

        val normalizedEdge = edgeType.lowercase()
        val requestedEdgeSize = edgeSize.coerceIn(0, 20)
        when (normalizedEdge) {
            "none" -> {
                setPropertyString("sub-border-size", "0")
                setPropertyString("sub-shadow-offset", "0")
            }
            "shadow" -> {
                setPropertyString("sub-border-size", if (requestedEdgeSize > 0) "1" else "0")
                setPropertyString("sub-shadow-offset", requestedEdgeSize.toString())
            }
            else -> {
                setPropertyString("sub-border-size", requestedEdgeSize.toString())
                setPropertyString("sub-shadow-offset", "0")
            }
        }

        // Apply sub-scale from the dedicated scale setting (not derived from fontSize)
        setSubScale(scale.coerceIn(0.5f, 3.0f).toDouble())
        if (overrideAssStylesEnabled) {
            setAssStyleOverrides(
                fontSize = fontSize,
                color = color,
                backgroundOpacity = backgroundOpacity,
                position = position,
                edgeSize = edgeSize,
                outlineColor = outlineColor,
                font = font,
                bold = bold,
                italic = italic,
                alignment = alignment
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
        outlineColor: Long,
        font: String = "sans-serif",
        bold: Boolean = false,
        italic: Boolean = false,
        alignment: String = "center"
    ) {
        val bottomMargin = ((100 - position.coerceIn(0, 100)) * 3).coerceIn(0, 240)
        val outline = edgeSize.coerceIn(0, 20)
        val styleOverrides = listOf(
            "FontName=$font",
            "Bold=${if (bold) 1 else 0}",
            "Italic=${if (italic) 1 else 0}",
            "Fontsize=${fontSize.coerceIn(10, 48)}",
            "PrimaryColour=${assColor(color)}",
            "OutlineColour=${assColor(outlineColor)}",
            "BackColour=${assColor(0xFF000000, (backgroundOpacity * 255).toInt())}",
            "BorderStyle=${if (backgroundOpacity > 0f) 3 else 1}",
            "Outline=$outline",
            "Shadow=0",
            "Alignment=${when(alignment) { "left" -> 1; "right" -> 3; else -> 2 }}",
            "MarginV=$bottomMargin"
        ).joinToString(",")
        setOption("sub-ass-style-overrides", styleOverrides)
        setPropertyString("sub-ass-style-overrides", styleOverrides)
    }

    fun setResizeMode(mode: String) {
        if (!isInitialized) return
        when (mode.lowercase()) {
            "fit" -> {
                setPropertyString("keepaspect", "yes")
                setPropertyString("panscan", "0.0")
                setPropertyString("video-aspect-override", "-1")
            }
            "fill" -> {
                setPropertyString("keepaspect", "no")
                setPropertyString("panscan", "0.0")
                setPropertyString("video-aspect-override", "-1")
            }
            "zoom" -> {
                setPropertyString("keepaspect", "yes")
                setPropertyString("panscan", "1.0")
                setPropertyString("video-aspect-override", "-1")
            }
            "16:9" -> {
                setPropertyString("keepaspect", "yes")
                setPropertyString("panscan", "0.0")
                setPropertyString("video-aspect-override", "16:9")
            }
            "4:3" -> {
                setPropertyString("keepaspect", "yes")
                setPropertyString("panscan", "0.0")
                setPropertyString("video-aspect-override", "4:3")
            }
            else -> {
                setPropertyString("keepaspect", "yes")
                setPropertyString("panscan", "0.0")
                setPropertyString("video-aspect-override", "-1")
            }
        }
    }

    fun setVideoZoom(zoom: Float, panX: Float = 0f, panY: Float = 0f) {
        if (!isInitialized) return
        
        if (zoom <= 1.0f) {
            // Reset to default
            setPropertyDouble("video-zoom", 0.0)
            setPropertyDouble("video-pan-x", 0.0)
            setPropertyDouble("video-pan-y", 0.0)
        } else {
            // MPV video-zoom is logarithmic (base 2). 
            // e.g., 2^(video-zoom) = scale factor
            // video-zoom = log2(scale)
            val log2Zoom = kotlin.math.log2(zoom.toDouble())
            setPropertyDouble("video-zoom", log2Zoom)
            
            // Pan values in MPV are relative to the video size (e.g. 0.5 is half the video)
            // Adjust pan coordinates based on the zoom factor.
            // When scaling up, the visible area decreases, so pan has more effect.
            setPropertyDouble("video-pan-x", panX.toDouble() / zoom)
            setPropertyDouble("video-pan-y", panY.toDouble() / zoom)
        }
    }

    private fun applyAssStyleModeAsOptions() {
        if (overrideAssStylesEnabled) {
            setOption("sub-ass-override", "force")
        } else {
            setOption("sub-ass-override", "scale")
        }
    }

    private fun applyAssStyleModeAsProperties() {
        if (overrideAssStylesEnabled) {
            setPropertyString("sub-ass-override", "force")
        } else {
            setPropertyString("sub-ass-override", "scale")
        }
    }

    private fun refreshSubtitleRenderer() {
        if (subtitleRendererRefreshPending) return
        subtitleRendererRefreshPending = true
        val selectedSubtitle = getPropertyInt("sid").takeIf { it > 0 }?.toString()
        if (selectedSubtitle == null) {
            subtitleRendererRefreshPending = false
            return
        }
        setPropertyString("sid", "no")
        mainHandler.postDelayed({
            subtitleRendererRefreshPending = false
            setPropertyString("sub-visibility", "yes")
            setPropertyInt("sid", selectedSubtitle.toInt())
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

    // ──── Track info ────

    fun getTrackCount(): Int = if (isInitialized) getPropertyInt("track-list/count") else 0
    fun getTrackType(index: Int): String = getPropertyString("track-list/$index/type")
    fun getTrackTitle(index: Int): String = getPropertyString("track-list/$index/title")
    fun getTrackLang(index: Int): String = getPropertyString("track-list/$index/lang")
    fun getTrackCodec(index: Int): String = getPropertyString("track-list/$index/codec")
    fun getTrackId(index: Int): Int = getPropertyInt("track-list/$index/id")
    fun getTrackExternalFileName(index: Int): String = getPropertyString("track-list/$index/external-filename")
    fun isTrackExternal(index: Int): Boolean = getPropertyBoolean("track-list/$index/external")
    fun isTrackSelected(index: Int): Boolean = getPropertyBoolean("track-list/$index/selected")

    // ──── Property change callback ────

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
                    if (value as? Boolean == true) {
                        _isPlaying.value = false
                        eventListener?.onEndFile()
                    }
                }
                "track-list/count" -> {
                    eventListener?.onTracksChanged()
                }
            }
            eventListener?.onPropertyChange(property, value)
        }
    }

    // ──── Destroy ────

    fun destroy() {
        if (!isInitialized) return
        Log.d(TAG, "Destroying MPV player")
        abandonAudioFocus()
        try {
            command(arrayOf("stop"))
            if (surfaceAttached) {
                try { mpvClass?.getMethod("detachSurface")?.invoke(mpvInstance) } catch (_: Exception) {}
                surfaceAttached = false
            }
            currentSurface = null
            mpvClass?.getMethod("destroy")?.invoke(mpvInstance)
            Log.d(TAG, "MPV player destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying MPV: ${e.message}")
        }
        isInitialized = false
    }

    // ──── Zoom compensation stub ────

    fun applySubtitleZoomCompensation(zoomLevel: Float) {
        // No-op — zoom compensation handled at UI layer
    }

    // ──── Reflection helpers ────

    private fun command(args: Array<String>) {
        try {
            mpvClass?.getMethod("command", Array<String>::class.java)?.invoke(mpvInstance, args)
        } catch (e: Exception) {
            Log.e(TAG, "command(${args.joinToString()}) failed: ${e.message}")
        }
    }

    private fun setOption(key: String, value: String) {
        try {
            val result = mpvClass?.getMethod("setOptionString", String::class.java, String::class.java)
                ?.invoke(mpvInstance, key, value)
            Log.d(TAG, "setOption($key, $value) -> $result")
        } catch (e: Exception) {
            Log.e(TAG, "setOption($key, $value) failed: ${e.message}")
        }
    }

    private fun setPropertyBoolean(key: String, value: Boolean) {
        try {
            mpvClass?.getMethod("setPropertyBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (e: Exception) {
            Log.e(TAG, "setPropertyBoolean($key, $value) failed: ${e.message}")
        }
    }

    private fun setPropertyInt(key: String, value: Int) {
        try {
            mpvClass?.getMethod("setPropertyInt", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (e: Exception) {
            Log.e(TAG, "setPropertyInt($key, $value) failed: ${e.message}")
        }
    }

    private fun setPropertyDouble(key: String, value: Double) {
        try {
            mpvClass?.getMethod("setPropertyDouble", String::class.java, Double::class.javaPrimitiveType)
                ?.invoke(mpvInstance, key, value)
        } catch (e: Exception) {
            Log.e(TAG, "setPropertyDouble($key, $value) failed: ${e.message}")
        }
    }

    private fun setPropertyString(key: String, value: String) {
        try {
            mpvClass?.getMethod("setPropertyString", String::class.java, String::class.java)
                ?.invoke(mpvInstance, key, value)
        } catch (e: Exception) {
            Log.e(TAG, "setPropertyString($key, $value) failed: ${e.message}")
        }
    }

    private fun getPropertyInt(key: String): Int {
        return try {
            mpvClass?.getMethod("getPropertyInt", String::class.java)
                ?.invoke(mpvInstance, key) as? Int ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun getPropertyBoolean(key: String): Boolean {
        return try {
            mpvClass?.getMethod("getPropertyBoolean", String::class.java)
                ?.invoke(mpvInstance, key) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    private fun getPropertyString(key: String): String {
        return try {
            mpvClass?.getMethod("getPropertyString", String::class.java)
                ?.invoke(mpvInstance, key) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private fun observeProperty(name: String, format: Int) {
        try {
            mpvClass?.getMethod("observeProperty", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(mpvInstance, name, format)
        } catch (e: Exception) {
            Log.e(TAG, "observeProperty($name, $format) failed: ${e.message}")
        }
    }

    private fun setAndroidSurfaceSize(width: Int, height: Int) {
        val size = "${width.coerceAtLeast(1)}x${height.coerceAtLeast(1)}"
        setOption("android-surface-size", size)
        setPropertyString("android-surface-size", size)
    }

    private fun restoreVideoOutput() {
        setPropertyString("vo", videoOutputName)
        applyDecoderMode()
    }

    private fun disableVideoOutput() {
        setPropertyString("vo", "null")
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
            else -> "auto"
        }
    }

    private fun mpvColor(argb: Long, alphaOverride: Int? = null): String {
        val androidAlpha = alphaOverride ?: ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return "#%02X%02X%02X%02X".format(
            androidAlpha.coerceIn(0, 255),
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

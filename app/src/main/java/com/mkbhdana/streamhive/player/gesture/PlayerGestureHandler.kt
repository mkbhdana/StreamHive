package com.mkbhdana.streamhive.player.gesture

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class GestureState(
    val showVolumeIndicator: Boolean = false,
    val showBrightnessIndicator: Boolean = false,
    val showSeekIndicator: Boolean = false,
    val showZoomIndicator: Boolean = false,
    val showSpeedIndicator: Boolean = false,
    val showLockIndicator: Boolean = false,
    val volumePercent: Float = 0f,
    val brightnessPercent: Float = 0f,
    val seekDeltaSeconds: Int = 0,
    val seekToPosition: Long = 0L,
    val showSeekTimestamp: Boolean = false,
    val zoomLevel: Float = 1f,
    val tapChainCount: Int = 0,
    val isLockActive: Boolean = false
)

@Composable
fun PlayerGestureHandler(
    currentPosition: Long,
    duration: Long,
    onToggleControls: () -> Unit,
    onCenterTap: () -> Unit = onToggleControls,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSpeedHoldStart: () -> Unit = {},
    onSpeedHoldEnd: () -> Unit = {},
    onLockToggle: () -> Unit = {},
    onProgressiveTapSeek: ((isForward: Boolean, tapCount: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    gestureState: MutableState<GestureState> = remember { mutableStateOf(GestureState()) },
    isLocked: Boolean = false,
    onZoomChange: ((Float) -> Unit)? = null,
    seekEnabled: Boolean = true,
    volumeEnabled: Boolean = true,
    brightnessEnabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
    zoomEnabled: Boolean = true,
    speedPressEnabled: Boolean = true,
    lockPressEnabled: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    gestureSensitivity: Float = 1.0f,
    tapSeekDuration: Int = 10,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val hapticView = LocalView.current

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Apply sensitivity: lower sensitivity = higher threshold = fewer accidental triggers
    val sensitivityInverse = (1f / gestureSensitivity.coerceIn(0.5f, 2.0f))
    val dragThreshold = with(density) { (24.dp * sensitivityInverse).toPx() }
    val tapMaxMovement = with(density) { (12.dp).toPx() }
    val doubleTapProximity = with(density) { (60.dp).toPx() }

    // Use rememberUpdatedState so currentPosition is read inside the gesture
    // without being part of the pointerInput key (which would restart the coroutine)
    val updatedPosition by rememberUpdatedState(currentPosition)
    val updatedOnSpeedHoldStart by rememberUpdatedState(onSpeedHoldStart)
    val updatedOnSpeedHoldEnd by rememberUpdatedState(onSpeedHoldEnd)

    // Auto-hide gesture indicators
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(800)
            gestureState.value = gestureState.value.copy(
                showVolumeIndicator = false,
                showBrightnessIndicator = false,
                showSeekIndicator = false,
                showZoomIndicator = false,
                showSpeedIndicator = false
            )
        }
    }

    fun scheduleLockIndicatorHide() {
        coroutineScope.launch {
            delay(1000)
            gestureState.value = gestureState.value.copy(showLockIndicator = false)
        }
    }

    fun isCenterTap(position: androidx.compose.ui.geometry.Offset): Boolean {
        val centerStartX = screenWidthPx * 0.35f
        val centerEndX = screenWidthPx * 0.65f
        // Tightened vertical zone: 38%-62% instead of 25%-75%
        val centerStartY = screenHeightPx * 0.38f
        val centerEndY = screenHeightPx * 0.62f
        return position.x in centerStartX..centerEndX && position.y in centerStartY..centerEndY
    }

    fun handleSingleTap(position: androidx.compose.ui.geometry.Offset) {
        if (isCenterTap(position)) {
            onCenterTap()
        } else {
            onToggleControls()
        }
    }

    // Locked mode: tap to show controls, long-press left side to unlock
    if (isLocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(lockPressEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        val isLeftSide = lockPressEnabled && downPos.x < size.width * LOCK_PRESS_ZONE_END_FRACTION

                        if (!isLeftSide) {
                            // Not in unlock zone — just handle tap for controls
                            val up = waitForUpOrCancellation()
                            if (up != null) onToggleControls()
                        } else {
                            // Left side: race between long-press (unlock) and release (tap)
                            var unlocked = false
                            val longPressJob = coroutineScope.launch {
                                delay(LOCK_LONG_PRESS_MS)
                                unlocked = true
                                if (hapticFeedbackEnabled) {
                                    hapticView.performPlayerHaptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                                }
                                gestureState.value = gestureState.value.copy(
                                    showLockIndicator = true,
                                    isLockActive = false,
                                    showVolumeIndicator = false,
                                    showBrightnessIndicator = false,
                                    showSeekIndicator = false,
                                    showZoomIndicator = false,
                                    showSpeedIndicator = false
                                )
                                onLockToggle()
                                delay(1000)
                                gestureState.value = gestureState.value.copy(showLockIndicator = false)
                            }
                            val up = waitForUpOrCancellation()
                            longPressJob.cancel()
                            if (up != null && !unlocked) {
                                onToggleControls()
                            }
                        }
                    }
                }
        ) { content() }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(
                duration,
                screenWidthPx,
                screenHeightPx,
                seekEnabled,
                volumeEnabled,
                brightnessEnabled,
                doubleTapEnabled,
                zoomEnabled,
                speedPressEnabled,
                hapticFeedbackEnabled,
                gestureSensitivity,
                tapSeekDuration
            ) {
                coroutineScope {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = firstDown.position

                        var totalDragX = 0f
                        var totalDragY = 0f
                        var gestureDir: GestureDir? = null
                        var isDragging = false
                        var isPinching = false
                        var lastPinchDist = 0f
                        var currentZoom = gestureState.value.zoomLevel.coerceIn(0.5f, 3f)
                        var lastSeekFeedbackStep = (updatedPosition / SEEK_HAPTIC_INTERVAL_MS).toInt()
                        var lastZoomFeedbackStep = (currentZoom * ZOOM_HAPTIC_STEPS).toInt()
                        var consumed = false
                        var isSpeedPressActive = false
                        var speedPressJob: kotlinx.coroutines.Job? = null
                        var isLockPressActive = false
                        var lockPressJob: kotlinx.coroutines.Job? = null

                        // Edge exclusion: 10% on sides, 12% on top/bottom
                        var ignoreGesture = false
                        val edgeMarginX = screenWidthPx * 0.10f
                        val edgeMarginY = screenHeightPx * 0.12f
                        if (downPos.x < edgeMarginX || downPos.x > screenWidthPx - edgeMarginX ||
                            downPos.y < edgeMarginY || downPos.y > screenHeightPx - edgeMarginY) {
                            ignoreGesture = true
                        }

                        val initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        var lastVolumeFeedbackStep = initialVolume
                        val window = (context as? android.app.Activity)?.window
                        val initialBrightness = run {
                            val lp = window?.attributes
                            if (lp?.screenBrightness != null && lp.screenBrightness >= 0f) {
                                lp.screenBrightness
                            } else {
                                try {
                                    Settings.System.getFloat(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    ) / 255f
                                } catch (_: Exception) { 0.5f }
                            }
                        }
                        var lastBrightnessFeedbackStep = (initialBrightness * BRIGHTNESS_HAPTIC_STEPS).toInt()

                        // Speed hold: right half of screen
                        if (speedPressEnabled && downPos.x >= screenWidthPx * SPEED_PRESS_ZONE_START_FRACTION && !ignoreGesture) {
                            speedPressJob = launch {
                                delay(SPEED_LONG_PRESS_MS)
                                if (!isDragging && !isPinching && !consumed) {
                                    isSpeedPressActive = true
                                    consumed = true
                                    hideJob?.cancel()
                                    if (hapticFeedbackEnabled) {
                                        hapticView.performPlayerHaptic(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                    gestureState.value = gestureState.value.copy(
                                        showSpeedIndicator = true,
                                        showVolumeIndicator = false,
                                        showBrightnessIndicator = false,
                                        showSeekIndicator = false,
                                        showZoomIndicator = false,
                                        showLockIndicator = false
                                    )
                                    updatedOnSpeedHoldStart()
                                }
                            }
                        }

                        // Lock gesture: left half of screen
                        if (lockPressEnabled && downPos.x < screenWidthPx * LOCK_PRESS_ZONE_END_FRACTION && !ignoreGesture) {
                            lockPressJob = launch {
                                delay(LOCK_LONG_PRESS_MS)
                                if (!isDragging && !isPinching && !consumed && !isSpeedPressActive) {
                                    isLockPressActive = true
                                    consumed = true
                                    if (hapticFeedbackEnabled) {
                                        hapticView.performPlayerHaptic(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                    // Show lock indicator briefly, then toggle
                                    val willBeLocked = !gestureState.value.isLockActive
                                    gestureState.value = gestureState.value.copy(
                                        showLockIndicator = true,
                                        isLockActive = willBeLocked,
                                        showVolumeIndicator = false,
                                        showBrightnessIndicator = false,
                                        showSeekIndicator = false,
                                        showZoomIndicator = false,
                                        showSpeedIndicator = false
                                    )
                                    onLockToggle()
                                    scheduleLockIndicatorHide()
                                }
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.all { !it.pressed }) {
                                speedPressJob?.cancel()
                                lockPressJob?.cancel()
                                if (isSpeedPressActive) {
                                    isSpeedPressActive = false
                                    updatedOnSpeedHoldEnd()
                                    gestureState.value = gestureState.value.copy(
                                        showSpeedIndicator = false
                                    )
                                }
                                // All pointers up
                                if (isDragging && gestureDir == GestureDir.HORIZONTAL && seekEnabled) {
                                    onSeekTo(gestureState.value.seekToPosition)
                                    if (hapticFeedbackEnabled) {
                                        hapticView.performGestureTickHaptic()
                                    }
                                }
                                if (isDragging || isPinching) {
                                    scheduleHide()
                                }
                                if (!isDragging && !isPinching && !consumed) {
                                    // It was a tap — compute tap metrics
                                    val upTime = System.currentTimeMillis()
                                    val tapDuration = upTime - downTime

                                    // Compute movement during this tap
                                    val lastChange = changes.firstOrNull()
                                    val tapMovement = if (lastChange != null) {
                                        sqrt(
                                            (lastChange.position.x - downPos.x).pow(2) +
                                                (lastChange.position.y - downPos.y).pow(2)
                                        )
                                    } else 0f

                                    // Tap duration guard: 30ms-250ms, motion filter: <12dp movement
                                    val isValidTap = tapDuration in TAP_MIN_DURATION_MS..TAP_MAX_DURATION_MS
                                        && tapMovement < tapMaxMovement

                                    if (isValidTap && doubleTapEnabled) {
                                        // Try to detect double-tap (progressive tap chain)
                                        val secondDown = withTimeoutOrNull(TAP_CHAIN_WINDOW_MS) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (secondDown != null) {
                                            // Check proximity: second tap must be near first tap
                                            val tapDist = sqrt(
                                                (secondDown.position.x - downPos.x).pow(2) +
                                                    (secondDown.position.y - downPos.y).pow(2)
                                            )
                                            val secondUp = waitForUpOrCancellation()
                                            if (secondUp != null && tapDist < doubleTapProximity) {
                                                // Double-tap detected — start progressive tap chain
                                                val tapSide = secondDown.position.x > screenWidthPx / 2
                                                var totalTapCount = 1 // first seek action

                                                // Execute first seek
                                                if (onProgressiveTapSeek != null) {
                                                    onProgressiveTapSeek(tapSide, totalTapCount)
                                                } else {
                                                    if (tapSide) onSeekForward() else onSeekBackward()
                                                }

                                                // Keep looking for more taps in the chain
                                                while (true) {
                                                    val nextDown = withTimeoutOrNull(TAP_CHAIN_WINDOW_MS) {
                                                        awaitFirstDown(requireUnconsumed = false)
                                                    } ?: break

                                                    // Must be same side
                                                    val nextSide = nextDown.position.x > screenWidthPx / 2
                                                    if (nextSide != tapSide) break

                                                    val nextUp = waitForUpOrCancellation() ?: break

                                                    totalTapCount++
                                                    if (onProgressiveTapSeek != null) {
                                                        onProgressiveTapSeek(tapSide, totalTapCount)
                                                    } else {
                                                        if (tapSide) onSeekForward() else onSeekBackward()
                                                    }
                                                }
                                            } else {
                                                // Second tap too far away or cancelled — single tap
                                                handleSingleTap(downPos)
                                            }
                                        } else {
                                            // Single tap (no second tap within window)
                                            handleSingleTap(downPos)
                                        }
                                    } else if (!isValidTap && tapDuration < TAP_MIN_DURATION_MS) {
                                        // Too short — likely accidental screen brush, ignore
                                    } else {
                                        handleSingleTap(downPos)
                                    }
                                }
                                break
                            }

                            val activePointers = changes.filter { it.pressed }
                            if (activePointers.size >= 2) {
                                speedPressJob?.cancel()
                                lockPressJob?.cancel()
                            }

                            if (isSpeedPressActive || isLockPressActive) {
                                changes.fastForEach { it.consume() }
                                continue
                            }

                            if (activePointers.size >= 2 && !isDragging && zoomEnabled && !ignoreGesture) {
                                // Pinch gesture
                                isPinching = true
                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val dist = sqrt(
                                    (p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2)
                                )
                                if (lastPinchDist > 0f) {
                                    val scale = dist / lastPinchDist
                                    currentZoom = (currentZoom * scale).coerceIn(0.5f, 3f)
                                    val zoomStep = (currentZoom * ZOOM_HAPTIC_STEPS).toInt()
                                    if (hapticFeedbackEnabled && zoomStep != lastZoomFeedbackStep) {
                                        hapticView.performGestureTickHaptic()
                                        lastZoomFeedbackStep = zoomStep
                                    } else if (!hapticFeedbackEnabled) {
                                        lastZoomFeedbackStep = zoomStep
                                    }
                                    gestureState.value = gestureState.value.copy(
                                        showZoomIndicator = true,
                                        showVolumeIndicator = false,
                                        showBrightnessIndicator = false,
                                        showSeekIndicator = false,
                                        showSpeedIndicator = false,
                                        showLockIndicator = false,
                                        zoomLevel = currentZoom
                                    )
                                    onZoomChange?.invoke(currentZoom)
                                }
                                lastPinchDist = dist
                                changes.fastForEach { it.consume() }
                                consumed = true
                            } else if (activePointers.size == 1 && !isPinching) {
                                // Single-pointer drag
                                val change = activePointers[0]
                                val dragDelta = change.position - (change.previousPosition)
                                totalDragX += dragDelta.x
                                totalDragY += dragDelta.y

                                if (!isDragging && !ignoreGesture) {
                                    val absX = abs(totalDragX)
                                    val absY = abs(totalDragY)
                                    if (absX > dragThreshold || absY > dragThreshold) {
                                        speedPressJob?.cancel()
                                        lockPressJob?.cancel()
                                        isDragging = true
                                        consumed = true
                                        gestureDir = if (absX > absY) {
                                            GestureDir.HORIZONTAL
                                        } else {
                                            if (downPos.x < screenWidthPx / 2)
                                                GestureDir.VERT_LEFT
                                            else
                                                GestureDir.VERT_RIGHT
                                        }
                                    }
                                }

                                if (isDragging) {
                                    change.consume()
                                    when (gestureDir) {
                                        GestureDir.HORIZONTAL -> {
                                            if (seekEnabled) {
                                                val seekSensitivity = 0.5f * gestureSensitivity
                                                val norm = totalDragX / (screenWidthPx * 0.8f)
                                                val eased = norm.sign() * abs(norm).pow(1.5f)
                                                val seekDelta = (eased * duration * seekSensitivity).toLong()
                                                val newPos = (updatedPosition + seekDelta).coerceIn(0, duration)
                                                val seekFeedbackStep = (newPos / SEEK_HAPTIC_INTERVAL_MS).toInt()
                                                if (hapticFeedbackEnabled && seekFeedbackStep != lastSeekFeedbackStep) {
                                                    hapticView.performGestureTickHaptic()
                                                    lastSeekFeedbackStep = seekFeedbackStep
                                                } else if (!hapticFeedbackEnabled) {
                                                    lastSeekFeedbackStep = seekFeedbackStep
                                                }
                                                gestureState.value = gestureState.value.copy(
                                                    showSeekIndicator = true,
                                                    showVolumeIndicator = false,
                                                    showBrightnessIndicator = false,
                                                    showZoomIndicator = false,
                                                    showSpeedIndicator = false,
                                                    showLockIndicator = false,
                                                    seekDeltaSeconds = (seekDelta / 1000).toInt(),
                                                    seekToPosition = newPos,
                                                    showSeekTimestamp = true,
                                                    tapChainCount = 0
                                                )
                                            }
                                        }
                                        GestureDir.VERT_RIGHT -> {
                                            if (volumeEnabled) {
                                                val vDelta = -totalDragY / (screenHeightPx * 0.5f)
                                                val eased = vDelta.sign() * abs(vDelta).pow(0.8f)
                                                val newVol = (initialVolume + (eased * maxVolume).toInt())
                                                    .coerceIn(0, maxVolume)
                                                val pct = if (maxVolume > 0) newVol.toFloat() / maxVolume else 0f
                                                if (hapticFeedbackEnabled && newVol != lastVolumeFeedbackStep) {
                                                    hapticView.performGestureTickHaptic()
                                                    lastVolumeFeedbackStep = newVol
                                                } else if (!hapticFeedbackEnabled) {
                                                    lastVolumeFeedbackStep = newVol
                                                }
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                onVolumeChange(pct)
                                                gestureState.value = gestureState.value.copy(
                                                    showVolumeIndicator = true,
                                                    showBrightnessIndicator = false,
                                                    showSeekIndicator = false,
                                                    showZoomIndicator = false,
                                                    showSpeedIndicator = false,
                                                    showLockIndicator = false,
                                                    volumePercent = pct
                                                )
                                            }
                                        }
                                        GestureDir.VERT_LEFT -> {
                                            if (brightnessEnabled) {
                                                val bDelta = -totalDragY / (screenHeightPx * 0.5f)
                                                val eased = bDelta.sign() * abs(bDelta).pow(0.8f)
                                                val newBright = (initialBrightness + eased).coerceIn(0.01f, 1f)
                                                val brightnessStep = (newBright * BRIGHTNESS_HAPTIC_STEPS).toInt()
                                                if (hapticFeedbackEnabled && brightnessStep != lastBrightnessFeedbackStep) {
                                                    hapticView.performGestureTickHaptic()
                                                    lastBrightnessFeedbackStep = brightnessStep
                                                } else if (!hapticFeedbackEnabled) {
                                                    lastBrightnessFeedbackStep = brightnessStep
                                                }
                                                window?.let { w ->
                                                    val lp = w.attributes
                                                    lp.screenBrightness = newBright
                                                    w.attributes = lp
                                                }
                                                onBrightnessChange(newBright)
                                                gestureState.value = gestureState.value.copy(
                                                    showBrightnessIndicator = true,
                                                    showVolumeIndicator = false,
                                                    showSeekIndicator = false,
                                                    showZoomIndicator = false,
                                                    showSpeedIndicator = false,
                                                    showLockIndicator = false,
                                                    brightnessPercent = newBright
                                                )
                                            }
                                        }
                                        null -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}

private fun Float.sign(): Float = if (this >= 0f) 1f else -1f

private fun View.performGestureTickHaptic() {
    performPlayerHaptic(HapticFeedbackConstants.CLOCK_TICK)
}

private fun View.performPlayerHaptic(effect: Int) {
    isHapticFeedbackEnabled = true
    val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    if (!performHapticFeedback(effect, flags) && effect != HapticFeedbackConstants.VIRTUAL_KEY) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, flags)
    }
}

private const val BRIGHTNESS_HAPTIC_STEPS = 20
private const val SEEK_HAPTIC_INTERVAL_MS = 10_000L
private const val SPEED_LONG_PRESS_MS = 450L
private const val SPEED_PRESS_ZONE_START_FRACTION = 0.5f
private const val LOCK_LONG_PRESS_MS = 600L
private const val LOCK_PRESS_ZONE_END_FRACTION = 0.5f
private const val ZOOM_HAPTIC_STEPS = 10
private const val TAP_CHAIN_WINDOW_MS = 350L
private const val TAP_MIN_DURATION_MS = 30L
private const val TAP_MAX_DURATION_MS = 250L

private enum class GestureDir {
    HORIZONTAL, VERT_LEFT, VERT_RIGHT
}

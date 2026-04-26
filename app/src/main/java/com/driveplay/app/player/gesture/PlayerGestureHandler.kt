package com.driveplay.app.player.gesture

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class GestureState(
    val showVolumeIndicator: Boolean = false,
    val showBrightnessIndicator: Boolean = false,
    val showSeekIndicator: Boolean = false,
    val showZoomIndicator: Boolean = false,
    val volumePercent: Float = 0f,
    val brightnessPercent: Float = 0f,
    val seekDeltaSeconds: Int = 0,
    val seekToPosition: Long = 0L,
    val zoomLevel: Float = 1f
)

@Composable
fun PlayerGestureHandler(
    currentPosition: Long,
    duration: Long,
    onToggleControls: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gestureState: MutableState<GestureState> = remember { mutableStateOf(GestureState()) },
    isLocked: Boolean = false,
    onZoomChange: ((Float) -> Unit)? = null,
    seekEnabled: Boolean = true,
    volumeEnabled: Boolean = true,
    brightnessEnabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
    zoomEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dragThreshold = with(density) { 15.dp.toPx() }

    // Use rememberUpdatedState so currentPosition is read inside the gesture
    // without being part of the pointerInput key (which would restart the coroutine)
    val updatedPosition by rememberUpdatedState(currentPosition)

    // Auto-hide gesture indicators
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(800)
            gestureState.value = GestureState()
        }
    }

    // Locked mode: tap only
    if (isLocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            onToggleControls()
                        }
                    }
                }
        ) { content() }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(duration, screenWidthPx, screenHeightPx) {
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

                        val initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
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

                        var consumed = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.all { !it.pressed }) {
                                // All pointers up
                                if (isDragging && gestureDir == GestureDir.HORIZONTAL) {
                                    onSeekTo(gestureState.value.seekToPosition)
                                }
                                if (isDragging || isPinching) {
                                    scheduleHide()
                                }
                                if (!isDragging && !isPinching && !consumed) {
                                    // It was a tap — check for double-tap
                                    val upTime = System.currentTimeMillis()
                                    val tapDuration = upTime - downTime
                                    if (tapDuration < 300 && doubleTapEnabled) {
                                        // Try to detect double-tap
                                        val secondDown = withTimeoutOrNull(300) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (secondDown != null) {
                                            // Double-tap detected
                                            val secondUp = waitForUpOrCancellation()
                                            if (secondUp != null) {
                                                if (secondDown.position.x < screenWidthPx / 2) {
                                                    onSeekBackward()
                                                } else {
                                                    onSeekForward()
                                                }
                                            }
                                        } else {
                                            // Single tap
                                            onToggleControls()
                                        }
                                    } else {
                                        onToggleControls()
                                    }
                                }
                                break
                            }

                            val activePointers = changes.filter { it.pressed }

                            if (activePointers.size >= 2 && !isDragging && zoomEnabled) {
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
                                    gestureState.value = gestureState.value.copy(
                                        showZoomIndicator = true,
                                        showVolumeIndicator = false,
                                        showBrightnessIndicator = false,
                                        showSeekIndicator = false,
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

                                if (!isDragging) {
                                    val absX = abs(totalDragX)
                                    val absY = abs(totalDragY)
                                    if (absX > dragThreshold || absY > dragThreshold) {
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
                                                val norm = totalDragX / (screenWidthPx * 0.8f)
                                                val eased = norm.sign() * abs(norm).pow(1.5f)
                                                val seekDelta = (eased * duration * 0.5f).toLong()
                                                val newPos = (updatedPosition + seekDelta).coerceIn(0, duration)
                                                gestureState.value = gestureState.value.copy(
                                                    showSeekIndicator = true,
                                                    showVolumeIndicator = false,
                                                    showBrightnessIndicator = false,
                                                    showZoomIndicator = false,
                                                    seekDeltaSeconds = (seekDelta / 1000).toInt(),
                                                    seekToPosition = newPos
                                                )
                                            }
                                        }
                                        GestureDir.VERT_RIGHT -> {
                                            if (volumeEnabled) {
                                                val vDelta = -totalDragY / (screenHeightPx * 0.5f)
                                                val eased = vDelta.sign() * abs(vDelta).pow(0.8f)
                                                val newVol = (initialVolume + (eased * maxVolume).toInt())
                                                    .coerceIn(0, maxVolume)
                                                val pct = newVol.toFloat() / maxVolume
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                onVolumeChange(pct)
                                                gestureState.value = gestureState.value.copy(
                                                    showVolumeIndicator = true,
                                                    showBrightnessIndicator = false,
                                                    showSeekIndicator = false,
                                                    showZoomIndicator = false,
                                                    volumePercent = pct
                                                )
                                            }
                                        }
                                        GestureDir.VERT_LEFT -> {
                                            if (brightnessEnabled) {
                                                val bDelta = -totalDragY / (screenHeightPx * 0.5f)
                                                val eased = bDelta.sign() * abs(bDelta).pow(0.8f)
                                                val newBright = (initialBrightness + eased).coerceIn(0.01f, 1f)
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

private enum class GestureDir {
    HORIZONTAL, VERT_LEFT, VERT_RIGHT
}

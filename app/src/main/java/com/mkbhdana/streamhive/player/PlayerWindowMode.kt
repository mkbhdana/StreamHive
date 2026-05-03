package com.mkbhdana.streamhive.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun PlayerWindowMode(restoreOnDispose: Boolean = true) {
    val activity = LocalContext.current as? Activity
    val shouldRestoreOnDispose = rememberUpdatedState(restoreOnDispose)

    DisposableEffect(activity) {
        activity?.enterPlayerWindowMode()
        onDispose {
            if (shouldRestoreOnDispose.value) {
                activity?.exitPlayerWindowMode()
            }
        }
    }

    LaunchedEffect(activity) {
        activity ?: return@LaunchedEffect
        activity.enterPlayerWindowMode()
        delay(120)
        activity.enterPlayerWindowMode()
        delay(300)
        activity.enterPlayerWindowMode()
    }
}

fun Activity.enterPlayerWindowMode() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun Activity.exitPlayerWindowMode() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.show(WindowInsetsCompat.Type.systemBars())
    window.decorView.requestApplyInsets()
    window.decorView.post {
        controller.show(WindowInsetsCompat.Type.systemBars())
        window.decorView.requestApplyInsets()
    }
    window.decorView.postDelayed({
        controller.show(WindowInsetsCompat.Type.systemBars())
        window.decorView.requestApplyInsets()
    }, 250)
}

package com.mkbhdana.streamhive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mkbhdana.streamhive.navigation.AppNavigation
import com.mkbhdana.streamhive.tv.TvAppNavigation
import com.mkbhdana.streamhive.tv.theme.TvStreamHiveTheme
import com.mkbhdana.streamhive.ui.theme.StreamHiveTheme
import com.mkbhdana.streamhive.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Duplicate-launch guard: if the app is re-opened from the (TV) launcher while
        // an instance already exists, Android can stack a fresh activity on top of the
        // live one (e.g. the player), which shows a "newly opened app" and makes the new
        // instance's playback conflict with the still-alive player underneath. Finish the
        // duplicate so the existing task — and the player screen — resumes instead.
        if (!isTaskRoot &&
            intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_LAUNCHER) ||
                intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        ) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        val splashScreen = installSplashScreen()
        var keepSplash = true
        lifecycleScope.launch {
            delay(1500)
            keepSplash = false
        }
        splashScreen.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android TV devices get a dedicated 10-foot UI; phones keep the mobile UI.
        // The two trees share the same ViewModels/repositories — mobile is untouched.
        val isTv = DeviceUtils.isTvDevice(this)

        setContent {
            if (isTv) {
                TvStreamHiveTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TvAppNavigation()
                    }
                }
            } else {
                StreamHiveTheme() {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

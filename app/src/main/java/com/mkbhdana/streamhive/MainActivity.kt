package com.mkbhdana.streamhive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mkbhdana.streamhive.navigation.AppNavigation
import com.mkbhdana.streamhive.ui.theme.StreamHiveTheme
import com.mkbhdana.streamhive.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplash = true
        lifecycleScope.launch {
            delay(1500)
            keepSplash = false
        }
        splashScreen.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StreamHiveTheme() {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

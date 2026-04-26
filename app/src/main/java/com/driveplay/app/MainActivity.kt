package com.driveplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.driveplay.app.navigation.AppNavigation
import com.driveplay.app.ui.theme.DrivePlayTheme
import com.driveplay.app.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTv = DeviceUtils.isTvDevice(this)

        setContent {
            DrivePlayTheme(isTv = isTv) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(isTv = isTv)
                }
            }
        }
    }
}

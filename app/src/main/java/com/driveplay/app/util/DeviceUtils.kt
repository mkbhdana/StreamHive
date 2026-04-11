package com.driveplay.app.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

object DeviceUtils {
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

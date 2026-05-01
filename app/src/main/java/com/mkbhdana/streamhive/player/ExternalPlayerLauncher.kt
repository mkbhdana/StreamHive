package com.mkbhdana.streamhive.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Launches the system "Open with" app picker for a video file using the local proxy URL.
 * Because the proxy runs on 127.0.0.1, the Drive auth token is never exposed to external apps.
 */
object ExternalPlayerLauncher {

    fun launch(context: Context, proxyUrl: String, title: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(proxyUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            }
            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No video player app found", Toast.LENGTH_SHORT).show()
        }
    }
}

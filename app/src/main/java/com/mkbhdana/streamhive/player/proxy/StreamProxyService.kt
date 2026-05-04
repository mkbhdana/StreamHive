package com.mkbhdana.streamhive.player.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mkbhdana.streamhive.R

/**
 * Foreground service that keeps the StreamProxyServer alive even when
 * the app UI is in the background. Controlled by the "Keep server running"
 * toggle in Settings.
 */
class StreamProxyService : Service() {

    companion object {
        private const val TAG = "StreamProxyService"
        private const val CHANNEL_ID = "streamhive_proxy_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, StreamProxyService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { error ->
                Log.w(TAG, "Unable to start proxy foreground service", error)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamProxyService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start proxy foreground service", error)
            stopSelf()
            return
        }
        Log.d(TAG, "Proxy service started — server on port ${StreamProxyServer.instancePort}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Proxy service stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Streaming Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "StreamHive local streaming proxy"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StreamHive Server")
            .setContentText("Streaming proxy is running on port ${StreamProxyServer.instancePort}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

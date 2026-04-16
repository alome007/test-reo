package dev.uclip.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ClipboardSyncService : Service() {

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        // TODO(phase 5): encrypt and dispatch via Transport.
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboardManager.addPrimaryClipChangedListener(primaryClipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(primaryClipListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Clipboard sync", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Universal Clipboard")
            .setContentText("Syncing clipboard with paired devices")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "uclip.sync"
        private const val NOTIFICATION_ID = 1
    }
}

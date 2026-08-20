package ch.leftclick.piflash.domain.flash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ch.leftclick.piflash.MainActivity

class FlashForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Flashing", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = "Keeps USB alive while writing the SD card"
                }
            )
        }
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("PiFlash")
            .setContentText("Writing SD card — keep this app open")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(launch)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "piflash:flash")
                .apply {
                    setReferenceCounted(false)
                    acquire(45 * 60 * 1000L)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "piflash_flash"
        private const val NOTIF_ID = 41
        private const val ACTION_STOP = "ch.leftclick.piflash.STOP_FLASH_SERVICE"

        fun start(context: Context) {
            val app = context.applicationContext
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, FlashForegroundService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.startService(
                    Intent(app, FlashForegroundService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}

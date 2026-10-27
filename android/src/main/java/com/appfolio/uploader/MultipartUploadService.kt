package com.appfolio.uploader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MultipartUploadService : Service() {
    companion object {
        private const val TAG = "MultipartUploadService"
        private const val CHANNEL_ID = "BackgroundUploadChannel"
        private const val NOTIFICATION_ID = 1002

        @Volatile
        var instance: MultipartUploadService? = null

        const val ACTION_START = "com.appfolio.uploader.START_FOREGROUND_UPLOAD"
        const val ACTION_STOP = "com.appfolio.uploader.STOP_FOREGROUND_UPLOAD"
        const val ACTION_UPDATE = "com.appfolio.uploader.UPDATE_FOREGROUND_UPLOAD"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_PROGRESS = "extra_progress"

        fun start(context: Context, title: String, message: String) {
            try {
                val intent = Intent(context, MultipartUploadService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // If Android OS blocks startForegroundService (e.g. ForegroundServiceStartNotAllowedException),
                // catch it so the app process does not crash. The coroutine will still run in process.
                Log.w(TAG, "Could not start MultipartUploadService: ${e.message}")
            }
        }

        fun updateProgress(context: Context, title: String, progress: Int, message: String) {
            try {
                val intent = Intent(context, MultipartUploadService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_MESSAGE, message)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update progress: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                // Instantly dismiss foreground notification and stop service if running in-process
                instance?.stopForegroundAndSelf()

                // Also directly cancel the notification ID immediately
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)

                val intent = Intent(context, MultipartUploadService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop MultipartUploadService: ${e.message}")
            }
        }
    }

    private val binder = LocalBinder()
    private var isStarted = false
    private var notificationManager: NotificationManager? = null

    inner class LocalBinder : Binder() {
        fun getService(): MultipartUploadService = this@MultipartUploadService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Uploading..."
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Starting upload..."
                try {
                    startForegroundWithNotification(title, 0, message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed startForeground in onStartCommand: ${e.message}")
                    // Stop service if foreground transition was denied to prevent ANR / crash
                    stopSelf()
                }
            }
            ACTION_UPDATE -> {
                if (isStarted) {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Uploading..."
                    val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                    val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Uploading..."
                    updateNotification(title, progress, message)
                }
            }
            ACTION_STOP -> {
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(title: String, progress: Int, message: String) {
        val notification = buildNotification(title, progress, message, ongoing = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isStarted = true
    }

    private fun updateNotification(title: String, progress: Int, message: String) {
        val notification = buildNotification(title, progress, message, ongoing = true)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, progress: Int, message: String, ongoing: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun stopForegroundAndSelf() {
        isStarted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Error in stopForeground: ${e.message}")
        } finally {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Upload Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        stopForegroundAndSelf()
        super.onDestroy()
    }
}

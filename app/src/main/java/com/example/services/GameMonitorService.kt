package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.utils.RenameUtil
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Handler
import android.os.Looper

class GameMonitorService : Service() {

    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L
    private val TARGET_PKG = "com.dts.freefiremax"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("monitor_channel", "Game Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "monitor_channel")
            .setContentTitle("Game Monitor Active")
            .setContentText("Monitoring $TARGET_PKG")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(2001, notification)

        if (!isRunning) {
            isRunning = true
            handler.post(monitorRunnable)
        }
        return START_STICKY
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            Thread {
                val isForeground = isAppInForeground()
                if (!isForeground) {
                    // Turn OFF
                    if (RenameUtil.checkDirExists("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android/gameassetbundles-data")) {
                        RenameUtil.turnOff()
                    }
                    isRunning = false
                    stopSelf()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }.start()
        }
    }

    private fun isAppInForeground(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 10000, time) // checking last 10 seconds is more than enough
        var lastResumedPkg: String? = null
        var maxTime = 0L
        while (events.hasNextEvent()) {
            val ev = UsageEvents.Event()
            events.getNextEvent(ev)
            if (ev.timeStamp > maxTime) {
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumedPkg = ev.packageName
                    maxTime = ev.timeStamp
                } else if (ev.eventType == UsageEvents.Event.ACTIVITY_PAUSED || ev.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                    if (lastResumedPkg == ev.packageName) {
                        lastResumedPkg = null
                    }
                    maxTime = ev.timeStamp
                }
            }
        }
        return lastResumedPkg == TARGET_PKG
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

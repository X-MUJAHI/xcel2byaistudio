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

    private var lastQueryTime = System.currentTimeMillis() - 1000 * 60 * 60 * 2 // 2 hours ago
    private var currentForegroundPkg: String? = null
    private var lastSeenForegroundTime = System.currentTimeMillis()
    private val GRACE_PERIOD_MS = 3 * 60 * 1000L // 3 minutes

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("monitor_channel", "Game Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastSeenForegroundTime = System.currentTimeMillis() // Reset grace period on every start request
        val notification = NotificationCompat.Builder(this, "monitor_channel")
            .setContentTitle("xcel2 is working")
            .setContentText("Hologram ability is active while you play")
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
                updateForegroundApp()
                val isForeground = (currentForegroundPkg == TARGET_PKG)
                
                if (isForeground) {
                    lastSeenForegroundTime = System.currentTimeMillis()
                }

                if (!isForeground && (System.currentTimeMillis() - lastSeenForegroundTime > GRACE_PERIOD_MS)) {
                    // Turn OFF
                    if (RenameUtil.checkDirExists("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android/gameassetbundles-data")) {
                        RenameUtil.turnOff()
                    }
                    isRunning = false
                    stopForeground(true)
                    stopSelf()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }.start()
        }
    }

    private fun updateForegroundApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryTime, time)
        while (events.hasNextEvent()) {
            val ev = UsageEvents.Event()
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPkg = ev.packageName
            } else if (ev.eventType == UsageEvents.Event.ACTIVITY_PAUSED || ev.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                if (currentForegroundPkg == ev.packageName) {
                    currentForegroundPkg = null
                }
            }
        }
        lastQueryTime = time
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

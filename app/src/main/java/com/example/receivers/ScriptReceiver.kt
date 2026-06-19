package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.utils.RenameUtil

class ScriptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val script = intent?.getStringExtra("SCRIPT")
        if (!script.isNullOrBlank()) {
            Thread {
                RenameUtil.executeShizukuCommand(script)
            }.start()
        }
    }
}

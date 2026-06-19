package com.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class ShellTerminalFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_shell_terminal, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        val etCommands = view.findViewById<EditText>(R.id.et_commands)
        val tvOutput = view.findViewById<TextView>(R.id.tv_output)
        val btnRun = view.findViewById<ImageButton>(R.id.btn_run)
        
        val btnSave = view.findViewById<View>(R.id.btn_save)
        val btnSaved = view.findViewById<View>(R.id.btn_saved)
        val btnSchedule = view.findViewById<View>(R.id.btn_schedule)
        
        tvOutput.movementMethod = android.text.method.ScrollingMovementMethod()

        val prefs = requireActivity().getSharedPreferences("saved_scripts", android.content.Context.MODE_PRIVATE)

        btnSave.setOnClickListener {
            val script = etCommands.text.toString()
            if (script.isBlank()) return@setOnClickListener
            val savedSet = prefs.getStringSet("scripts_set", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            savedSet.add(script)
            prefs.edit().putStringSet("scripts_set", savedSet).apply()
            android.widget.Toast.makeText(context, "Script Saved", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnSaved.setOnClickListener {
            val savedSet = prefs.getStringSet("scripts_set", mutableSetOf())?.toList() ?: listOf()
            if (savedSet.isEmpty()) {
                android.widget.Toast.makeText(context, "No saved scripts", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val builder = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            builder.setTitle("Saved Scripts")
            val items = savedSet.map { it.take(20) + "..." }.toTypedArray()
            builder.setItems(items) { _, which ->
                etCommands.setText(savedSet[which])
            }
            builder.show()
        }

        btnSchedule.setOnClickListener {
            val script = etCommands.text.toString()
            if (script.isBlank()) return@setOnClickListener
            
            val calendar = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }

                val intent = android.content.Intent(context, com.example.receivers.ScriptReceiver::class.java)
                intent.putExtra("SCRIPT", script)
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, script.hashCode(), intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = requireContext().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                try {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    android.widget.Toast.makeText(context, "Scheduled for ${hourOfDay}:${minute}", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    android.widget.Toast.makeText(context, "Permission denied for exact alarm", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show()
        }

        btnRun.setOnClickListener {
            val script = etCommands.text.toString()
            if (script.isBlank()) return@setOnClickListener

            Thread {
                val outputResult = RenameUtil.executeShizukuCommandWithOutput(script)
                requireActivity().runOnUiThread {
                    tvOutput.text = outputResult
                }
            }.start()
        }

        return view
    }
}

package com.example.fragments

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.app.Activity
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.utils.RealPathUtil
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil
import java.io.File

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnActivate: Button
    private lateinit var btnTogglePower: Button
    private lateinit var btnOpenGame: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvPing: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvFps: TextView
    private lateinit var tvPlaytimeToday: TextView
    private lateinit var tvPlaytimeYesterday: TextView

    private var isOn = false
    private var isActivationMode = false
    private var countDownTimer: CountDownTimer? = null
    private val timerUpdateInterval = 1000L

    private val zipPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processZipActivation(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tv_status)
        tvTimer = view.findViewById(R.id.tv_timer)
        btnActivate = view.findViewById(R.id.btn_activate)
        btnTogglePower = view.findViewById(R.id.btn_toggle_power)
        btnOpenGame = view.findViewById(R.id.btn_open_game)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)
        tvVersion = view.findViewById(R.id.tv_version)
        tvPing = view.findViewById(R.id.tv_ping)
        tvBattery = view.findViewById(R.id.tv_battery)
        tvFps = view.findViewById(R.id.tv_fps)
        tvPlaytimeToday = view.findViewById(R.id.tv_playtime_today)
        tvPlaytimeYesterday = view.findViewById(R.id.tv_playtime_yesterday)

        try {
            val vers = resources.openRawResource(R.raw.version).bufferedReader().use { it.readText() }.trim()
            tvVersion.text = "v$vers"
        } catch (e: Exception) {
            tvVersion.text = "v1.0"
        }

        view.findViewById<Button>(R.id.btn_telegram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/xcel1_panel")))
        }
        view.findViewById<Button>(R.id.btn_instagram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials")))
        }
        view.findViewById<Button>(R.id.btn_not_working).setOnClickListener {
            android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("App Not Working?")
                .setMessage("If you are facing issues, you can follow the tutorial or contact admin for help.")
                .setPositiveButton("Tutorial") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://x-link.vercel.app/hologram-tutorial")))
                }
                .setNegativeButton("Contact Admin") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials")))
                }
                .setNeutralButton("Cancel", null)
                .show()
        }

        btnActivate.setOnClickListener { startFirstTimeActivation() }
        
        btnTogglePower.setOnClickListener {
            if (isOn) {
                handleTurnOff()
            } else {
                handleTurnOn()
            }
        }
        
        btnOpenGame.setOnClickListener {
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Free Fire MAX not found", Toast.LENGTH_SHORT).show()
            }
        }

        fetchSystemStats()

        return view
    }

    private val pingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -w 2 8.8.8.8")
                    val returnVal = process.waitFor()
                    if (returnVal == 0) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        var time = "--"
                        for (line in reader.lines()) {
                            if (line.contains("time=")) {
                                time = line.substringAfter("time=").substringBefore(" ")
                                break
                            }
                        }
                        requireActivity().runOnUiThread { tvPing.text = "Ping: ${time}ms" }
                    } else {
                        requireActivity().runOnUiThread { tvPing.text = "Ping: Error" }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread { tvPing.text = "Ping: Error" }
                }
            }.start()
            pingHandler.postDelayed(this, 3000)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
        startTimerUpdate()
        pingHandler.post(pingRunnable)
    }

    override fun onPause() {
        super.onPause()
        countDownTimer?.cancel()
        statusAnimator?.cancel()
        glowAnimator?.cancel()
        pingHandler.removeCallbacks(pingRunnable)
    }

    private var statusAnimator: android.animation.ObjectAnimator? = null
    private var glowAnimator: android.animation.ObjectAnimator? = null

    private fun updateUIState() {
        val fExists = RenameUtil.checkDirExists(MainActivity.APP_FOLDER.absolutePath)
        val pExists = RenameUtil.checkDirExists(MainActivity.PANEL_FOLDER.absolutePath)
        val dExists = RenameUtil.checkDirExists(MainActivity.DATA_FOLDER.absolutePath)
        val hExists = RenameUtil.checkDirExists("/storage/emulated/0/Android/data/com.mujahi.hologram")

        isOn = fExists && dExists && !pExists
        
        val isOffOriginal = fExists && pExists && !dExists
        val isOffHologram = !fExists && !pExists && hExists
        val isOff = isOffOriginal || isOffHologram
        
        isActivationMode = fExists && !pExists && !dExists

        statusAnimator?.cancel()
        glowAnimator?.cancel()
        tvStatus.scaleX = 1f
        tvStatus.scaleY = 1f
        tvStatus.alpha = 1f
        btnTogglePower.scaleX = 1f
        btnTogglePower.scaleY = 1f
        btnTogglePower.alpha = 1f

        if (isOn) {
            tvStatus.text = "Status: ON \uD83D\uDFE2" // Green circle
            tvStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
            
            btnTogglePower.text = "TURN OFF"
            btnTogglePower.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
            btnTogglePower.setTextColor(android.graphics.Color.WHITE)
            
            // Smooth pulse animation for status
            statusAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                tvStatus,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
            ).apply {
                duration = 800
                repeatCount = android.animation.ObjectAnimator.INFINITE
                repeatMode = android.animation.ObjectAnimator.REVERSE
                start()
            }
            
            // Premium Breathing glow for Turn Off button
            glowAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                btnTogglePower,
                android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1.0f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.98f, 1.02f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.98f, 1.02f)
            ).apply {
                duration = 1000
                repeatCount = android.animation.ObjectAnimator.INFINITE
                repeatMode = android.animation.ObjectAnimator.REVERSE
                // use fast out slow in interpolator
                interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
                start()
            }
            
        } else if (isOff) {
            tvStatus.text = "Status: OFF \uD83D\uDD34" // Red circle
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))

            btnTogglePower.text = "TURN ON"
            btnTogglePower.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
            btnTogglePower.setTextColor(android.graphics.Color.parseColor("#151A22"))

        } else if (isActivationMode) {
            tvStatus.text = "Status: NOT ACTIVATED \u26A0\uFE0F"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FFD740"))
        } else {
            tvStatus.text = "Status: UNKNOWN"
            tvStatus.setTextColor(android.graphics.Color.WHITE)
        }

        btnActivate.visibility = if (isActivationMode) View.VISIBLE else View.GONE
        btnTogglePower.visibility = if (isOn || isOff) View.VISIBLE else View.GONE
    }

    private fun startTimerUpdate() {
        countDownTimer?.cancel()
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        val userType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
        if (userType == "ADMIN") {
            tvTimer.text = "Timer: Unlimited"
            return
        }
        val autoOffTime = prefs.getLong(MainActivity.KEY_AUTO_OFF_TIME, 0L)
        if (autoOffTime == 0L) {
            tvTimer.text = "Timer: --"
            return
        }
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, timerUpdateInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val now = System.currentTimeMillis()
                val remaining = autoOffTime - now
                if (remaining <= 0) {
                    tvTimer.text = "Timer: Expired"
                    cancel()
                } else {
                    val hours = remaining / 3600000
                    val minutes = (remaining % 3600000) / 60000
                    val seconds = (remaining % 60000) / 1000
                    tvTimer.text = String.format("Timer: %02d:%02d:%02d", hours, minutes, seconds)
                }
            }
            override fun onFinish() {}
        }.start()
    }

    private fun startFirstTimeActivation() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
            return
        }
        zipPickerLauncher.launch("application/zip")
    }

    private fun processZipActivation(uri: android.net.Uri) {
        val ctx = context ?: return
        val realPath = RealPathUtil.getPath(ctx, uri)

        if (realPath == null || !File(realPath).exists()) {
            Toast.makeText(ctx, "Invalid file selected!", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Starting..."
        btnActivate.isEnabled = false

        RenameUtil.executeShizukuScriptAsync(
            script = """
                f="/storage/emulated/0/Android/data/com.dts.freefiremax"
                d="/storage/emulated/0/Android/data/com.mujahi.data"
                
                # Move F to D if D does not exist
                if [ -d "${'$'}f" ] && [ ! -d "${'$'}d" ]; then
                    mv "${'$'}f" "${'$'}d"
                fi
                
                # Need to copy but no progress available, just copy
                echo "STATUS:Copying backup... (Takes 1-3 mins)"
                cp -r "${'$'}d"/. "${'$'}f"/

                echo "STATUS:Extracting Game Data..."
                if unzip -o "$realPath" -d "/storage/emulated/0/Android/data" ; then
                    echo "STATUS:Done!"
                else
                    echo "STATUS:Error: Unzip failed"
                    # Return standard failure
                    exit 1
                fi
            """.trimIndent(),
            onProgress = { line ->
                requireActivity().runOnUiThread {
                    if (line.startsWith("STATUS:")) {
                        tvProgress.text = line.substring(7)
                        if (tvProgress.text == "Done!") {
                            progressBar.progress = 100
                        }
                    } else if (line.contains("inflating:") || line.contains("extracting:")) {
                        // Rough visual feedback
                        if (progressBar.progress < 95) progressBar.progress += 1
                        val percent = if (progressBar.progress > 99) 99 else progressBar.progress
                        tvProgress.text = "Extracting... $percent%"
                    }
                }
            },
            onComplete = { success ->
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    if (success) {
                        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
                        prefs.edit().putString("CURRENT_SCRIPT", File(realPath).name).apply()

                        Toast.makeText(ctx, "Activation successful!", Toast.LENGTH_SHORT).show()
                        updateUIState()
                    } else {
                        Toast.makeText(ctx, "Error during activation. Check Shizuku status.", Toast.LENGTH_LONG).show()
                        btnActivate.isEnabled = true
                        updateUIState()
                    }
                }
            }
        )
    }

    private fun handleTurnOn() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Shizuku not authorized or not running!", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = requireActivity() as MainActivity
        btnTogglePower.isEnabled = false
        activity.executeTurnOnGlobal {
            btnTogglePower.isEnabled = true
            Toast.makeText(context, "Mod turned on", Toast.LENGTH_SHORT).show()
            updateUIState()
            startTimerUpdate()
        }
    }

    private fun handleTurnOff() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Shizuku not authorized!", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = requireActivity() as MainActivity
        btnTogglePower.isEnabled = false
        activity.executeTurnOffGlobal {
            btnTogglePower.isEnabled = true
            Toast.makeText(context, "Mod turned off", Toast.LENGTH_SHORT).show()
            updateUIState()
            countDownTimer?.cancel()
            tvTimer.text = "Timer: --"
        }
    }

    private fun fetchSystemStats() {
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        
        // Battery
        try {
            val bm = requireContext().getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tvBattery.text = "Battery: $batLevel%"
        } catch (e: Exception) {
            tvBattery.text = "Battery: --%"
        }
        
        // FPS
        val windowManager = requireContext().getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val display = windowManager.defaultDisplay
        val refreshRate = display.refreshRate
        tvFps.text = "FPS: ${refreshRate.toInt()}"

        // Playtime
        try {
            val appOps = requireContext().getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().packageName)
            if (mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT) {
                val usageStatsManager = requireContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val cal = java.util.Calendar.getInstance()
                // today
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                val todayStart = cal.timeInMillis
                val todayEnd = System.currentTimeMillis()
                
                val statsToday = usageStatsManager.queryAndAggregateUsageStats(todayStart, todayEnd)
                val ffStatsToday = statsToday["com.dts.freefiremax"]
                val timeToday = ffStatsToday?.totalTimeInForeground ?: 0L
                val hoursToday = timeToday / 3600000
                val minsToday = (timeToday % 3600000) / 60000
                tvPlaytimeToday.text = "Today: ${hoursToday}h ${minsToday}m"

                // yesterday
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterdayStart = cal.timeInMillis
                val yesterdayEnd = todayStart
                
                val statsYesterday = usageStatsManager.queryAndAggregateUsageStats(yesterdayStart, yesterdayEnd)
                val ffStatsYesterday = statsYesterday["com.dts.freefiremax"]
                val timeYesterday = ffStatsYesterday?.totalTimeInForeground ?: 0L
                val hoursYest = timeYesterday / 3600000
                val minsYest = (timeYesterday % 3600000) / 60000
                tvPlaytimeYesterday.text = "Yesterday: ${hoursYest}h ${minsYest}m"

            } else {
                tvPlaytimeToday.text = "Tap to enable required permission"
                tvPlaytimeToday.setOnClickListener {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                tvPlaytimeYesterday.text = "Stats hidden"
            }
        } catch (e: Exception) {
            tvPlaytimeToday.text = "Permission not available"
        }
    }
}

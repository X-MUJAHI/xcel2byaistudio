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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tv_status)
        tvStatus.setOnClickListener {
            Thread {
                val fExists = RenameUtil.checkDirExists(MainActivity.APP_FOLDER.absolutePath)
                val dExists = RenameUtil.checkDirExists(MainActivity.DATA_FOLDER.absolutePath)
                val sCountStr = RenameUtil.executeShizukuCommandWithOutput("ls -1d /storage/emulated/0/Android/data/com.mujahi.script.* 2>/dev/null | wc -l").trim()
                val zipExists = RenameUtil.checkDirExists("/storage/emulated/0/Download/xcel1.zip")
                
                val msg = "Diagnostics:\n\$F exists: $fExists\n\$D exists: $dExists\n\$S count: $sCountStr\nDownload/xcel1.zip exists: $zipExists"
                requireActivity().runOnUiThread {
                    android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                        .setTitle("System Status")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.start()
        }
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
            val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
            val userType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
            
            if (launchIntent == null) {
                Toast.makeText(context, "Free Fire MAX not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (userType == "NORMAL") {
                // Check usage stats permission
                val appOps = requireContext().getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                        android.os.Process.myUid(), requireContext().packageName)
                if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                    Toast.makeText(context, "Please grant Usage Access permission to track the game!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    return@setOnClickListener
                }
                
                val progressDialog = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle("Opening Game")
                    .setMessage("Turning ON mod and launching...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                
                val activity = requireActivity() as MainActivity
                activity.executeTurnOnGlobal {
                    progressDialog.dismiss()
                    updateUIState()
                    Toast.makeText(context, "Mod turned on! Launching game...", Toast.LENGTH_SHORT).show()
                    
                    // Start GameMonitorService
                    val serviceIntent = Intent(requireContext(), com.example.services.GameMonitorService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent)
                    } else {
                        requireContext().startService(serviceIntent)
                    }
                    
                    startActivity(launchIntent)
                }
            } else {
                startActivity(launchIntent)
            }
        }
        
        view.findViewById<android.widget.ImageButton>(R.id.btn_refresh)?.setOnClickListener {
            updateUIState()
            fetchSystemStats(view)
            Toast.makeText(context, "Refreshed", Toast.LENGTH_SHORT).show()
        }

        fetchSystemStats(view)

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
        view?.let { fetchSystemStats(it) }
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
        Thread {
            val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
            val gDirExists = RenameUtil.checkDirExists("$baseDir/gameassetbundles")
            val gDataExists = RenameUtil.checkDirExists("$baseDir/gameassetbundles-data")
            val gMujahiExists = RenameUtil.checkDirExists("$baseDir/gameassetbundles-mujahi")

            val fileInfoExists = RenameUtil.checkFileExists("$baseDir/fileinfo")
            val fileInfoDataExists = RenameUtil.checkFileExists("$baseDir/fileinfo-data")
            val fileInfoMujahiExists = RenameUtil.checkFileExists("$baseDir/fileinfo-mujahi")

            isOn = gDataExists && !gMujahiExists && fileInfoDataExists && !fileInfoMujahiExists
            val isOff = gMujahiExists && fileInfoMujahiExists
            isActivationMode = gDirExists && !gDataExists && !gMujahiExists && fileInfoExists && !fileInfoDataExists && !fileInfoMujahiExists

            requireActivity().runOnUiThread {
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
                    
                    glowAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                        btnTogglePower,
                        android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1.0f),
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.98f, 1.02f),
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.98f, 1.02f)
                    ).apply {
                        duration = 1000
                        repeatCount = android.animation.ObjectAnimator.INFINITE
                        repeatMode = android.animation.ObjectAnimator.REVERSE
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
                
                val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
                val userType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
                if (userType == "NORMAL") {
                    btnTogglePower.visibility = View.GONE
                } else {
                    btnTogglePower.visibility = if (isOn || isOff) View.VISIBLE else View.GONE
                }
            }
        }.start()
    }

    private fun startTimerUpdate() {
        countDownTimer?.cancel()
        tvTimer.text = "Timer: Unlimited"
    }

    private fun startFirstTimeActivation() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
            return
        }
        val ctx = context ?: return
        
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Starting Setup..."
        btnActivate.isEnabled = false

        val notificationManager = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("extract_channel", "Extraction Progress", android.app.NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = androidx.core.app.NotificationCompat.Builder(ctx, "extract_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Extracting Data")
            .setContentText("Please wait while game data is being generated...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)

        notificationManager.notify(1005, builder.build())

        RenameUtil.executeShizukuScriptAsync(
            script = """
                #!/system/bin/sh
                ZIP_FILE="/storage/emulated/0/Download/xcel1.zip"
                DEST_DIR="/storage/emulated/0/Android/data"
                BASE_DIR="/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
                G_DIR="${'$'}BASE_DIR/gameassetbundles"
                G_DATA="${'$'}BASE_DIR/gameassetbundles-data"
                FILEINFO="${'$'}BASE_DIR/fileinfo"
                FILEINFO_DATA="${'$'}BASE_DIR/fileinfo-data"
                
                if [ ! -f "${'$'}ZIP_FILE" ]; then
                    echo "STATUS:Error: Zip file not found in Download folder" >&2
                    exit 1
                fi
                
                echo "STATUS:Copying gameassetbundles backup..."
                if [ ! -d "${'$'}G_DATA" ]; then
                    if ! cp -pr "${'$'}G_DIR" "${'$'}G_DATA"; then
                        cp -r "${'$'}G_DIR" "${'$'}G_DATA"
                    fi
                fi

                echo "STATUS:Copying fileinfo backup..."
                if [ ! -f "${'$'}FILEINFO_DATA" ]; then
                    if ! cp -p "${'$'}FILEINFO" "${'$'}FILEINFO_DATA"; then
                        cp "${'$'}FILEINFO" "${'$'}FILEINFO_DATA"
                    fi
                fi
                
                echo "STATUS:Extracting xcel1.zip (Takes 5-10 mins)..."
                if unzip -o -q "${'$'}ZIP_FILE" -d "${'$'}DEST_DIR" ; then
                    mv "${'$'}ZIP_FILE" "/storage/emulated/0/Download/xcel1-used-delete-it.zip"
                    echo "STATUS:Done!"
                else
                    echo "STATUS:Error: Unzip failed"
                    exit 1
                fi
            """.trimIndent(),
            onProgress = { line ->
                requireActivity().runOnUiThread {
                    if (line.startsWith("STATUS:")) {
                        tvProgress.text = line.substring(7)
                        
                        builder.setContentText(line.substring(7))
                        notificationManager.notify(1005, builder.build())
                        
                        if (tvProgress.text == "Done!") {
                            progressBar.isIndeterminate = false
                            progressBar.progress = 100
                        }
                    }
                }
            },
            onComplete = { success ->
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    
                    builder.setContentText(if (success) "Extraction completed successfully!" else "Extraction failed!")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                    notificationManager.notify(1005, builder.build())
                    
                    if (success) {
                        Toast.makeText(ctx, "Activation successful!", Toast.LENGTH_SHORT).show()
                        updateUIState()
                        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
                        val userType = prefs.getString(MainActivity.KEY_USER_TYPE, null)
                        if (userType == "NORMAL") {
                            Toast.makeText(ctx, "Will automatically turn off in 5 seconds...", Toast.LENGTH_SHORT).show()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                handleTurnOff()
                            }, 5000)
                        }
                    } else {
                        Toast.makeText(ctx, "Error during activation.", Toast.LENGTH_LONG).show()
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
        activity.executeTurnOffGlobal { result ->
            btnTogglePower.isEnabled = true
            if (result == "SUCCESS") {
                Toast.makeText(context, "Mod turned off", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error turning off. Check folders.", Toast.LENGTH_SHORT).show()
            }
            updateUIState()
            countDownTimer?.cancel()
            tvTimer.text = "Timer: --"
        }
    }

    private fun fetchSystemStats(view: View) {
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
            val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), requireContext().packageName)
            if (mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT) {
                val usageStatsManager = requireContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val cal = java.util.Calendar.getInstance()
                // today
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                val todayStart = cal.timeInMillis
                val todayEnd = System.currentTimeMillis()
                
                var timeToday = 0L
                val eventsToday = usageStatsManager.queryEvents(todayStart, todayEnd)
                var lastTime = todayStart
                val event = android.app.usage.UsageEvents.Event()
                var isForeground = false
                var currentForegroundPkg: String? = null
                
                while (eventsToday.hasNextEvent()) {
                    eventsToday.getNextEvent(event)
                    
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) {
                        currentForegroundPkg = event.packageName
                    } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2 || event.eventType == 23) {
                        if (currentForegroundPkg == event.packageName) {
                            currentForegroundPkg = null
                        }
                    }

                    if (event.packageName == "com.dts.freefiremax") {
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) { // 1 is MOVE_TO_FOREGROUND
                            if (!isForeground) {
                                isForeground = true
                                lastTime = event.timeStamp
                            }
                        } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2 || event.eventType == 23) { // 2 is MOVE_TO_BACKGROUND
                            if (isForeground) {
                                isForeground = false
                                timeToday += (event.timeStamp - lastTime)
                            }
                        }
                    }
                }
                
                val isActuallyForeground = (currentForegroundPkg == "com.dts.freefiremax")
                
                if (isForeground) {
                    timeToday += (todayEnd - lastTime)
                }

                val hoursToday = timeToday / 3600000
                val minsToday = (timeToday % 3600000) / 60000
                tvPlaytimeToday.text = "Today: ${hoursToday}h ${minsToday}m"

                val tvGameStatus = view.findViewById<TextView>(R.id.tv_game_status)
                if (isActuallyForeground) {
                    tvGameStatus.text = "Active"
                    tvGameStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green
                } else {
                    tvGameStatus.text = "Inactive"
                    tvGameStatus.setTextColor(android.graphics.Color.parseColor("#EF4444")) // Red
                }

                // yesterday
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterdayStart = cal.timeInMillis
                val yesterdayEnd = todayStart
                
                var timeYesterday = 0L
                val eventsYesterday = usageStatsManager.queryEvents(yesterdayStart, yesterdayEnd)
                var lastTimeY = yesterdayStart
                isForeground = false
                while (eventsYesterday.hasNextEvent()) {
                    eventsYesterday.getNextEvent(event)
                    if (event.packageName == "com.dts.freefiremax") {
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) { 
                            if (!isForeground) {
                                isForeground = true
                                lastTimeY = event.timeStamp
                            }
                        } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == 2) { 
                            if (isForeground) {
                                isForeground = false
                                timeYesterday += (event.timeStamp - lastTimeY)
                            }
                        }
                    }
                }
                if (isForeground) {
                    timeYesterday += (yesterdayEnd - lastTimeY)
                }

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
            tvPlaytimeToday.text = "Permission missing or feature not available"
        }
    }
}

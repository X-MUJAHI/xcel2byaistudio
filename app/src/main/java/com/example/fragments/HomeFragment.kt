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
    private lateinit var btnTurnOn: Button
    private lateinit var btnTurnOff: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private var isOn = false
    private var isActivationMode = false
    private var countDownTimer: CountDownTimer? = null
    private val timerUpdateInterval = 1000L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tv_status)
        tvTimer = view.findViewById(R.id.tv_timer)
        btnActivate = view.findViewById(R.id.btn_activate)
        btnTurnOn = view.findViewById(R.id.btn_turn_on)
        btnTurnOff = view.findViewById(R.id.btn_turn_off)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)

        view.findViewById<Button>(R.id.btn_telegram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/xcel1_panel")))
        }
        view.findViewById<Button>(R.id.btn_instagram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials")))
        }

        btnActivate.setOnClickListener { startFirstTimeActivation() }
        btnTurnOn.setOnClickListener { handleTurnOn() }
        btnTurnOff.setOnClickListener { handleTurnOff() }

        return view
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
        startTimerUpdate()
    }

    override fun onPause() {
        super.onPause()
        countDownTimer?.cancel()
    }

    private fun updateUIState() {
        val fExists = RenameUtil.checkDirExists(MainActivity.APP_FOLDER.absolutePath)
        val pExists = RenameUtil.checkDirExists(MainActivity.PANEL_FOLDER.absolutePath)
        val dExists = RenameUtil.checkDirExists(MainActivity.DATA_FOLDER.absolutePath)

        isOn = fExists && dExists && !pExists
        val isOff = fExists && pExists && !dExists
        isActivationMode = fExists && !pExists && !dExists

        if (isOn) {
            tvStatus.text = "Status: ON"
        } else if (isOff) {
            tvStatus.text = "Status: OFF"
        } else if (isActivationMode) {
            tvStatus.text = "Status: NOT ACTIVATED"
        } else {
            tvStatus.text = "Status: UNKNOWN"
        }

        btnActivate.visibility = if (isActivationMode) View.VISIBLE else View.GONE
        btnTurnOn.visibility = if (isOff) View.VISIBLE else View.GONE
        btnTurnOff.visibility = if (isOn) View.VISIBLE else View.GONE
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
        val ctx = context ?: return
        
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(ctx, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Starting..."
        btnActivate.isEnabled = false

        var isExecuting = true
        Thread {
            var p = 0
            while (isExecuting && p < 95) {
                Thread.sleep(1500)
                p += 1
                requireActivity().runOnUiThread {
                    if (isExecuting) {
                        progressBar.progress = p
                        tvProgress.text = "Copying files... $p%"
                    }
                }
            }
        }.start()

        Thread {
            try {
                val f = MainActivity.APP_FOLDER
                val d = MainActivity.DATA_FOLDER
                
                val success1 = RenameUtil.executeShizukuCommand("mv \"${f.absolutePath}\" \"${d.absolutePath}\"")
                if (!success1) throw Exception("Failed to backup game folder. Check Shizuku status.")

                RenameUtil.copyDirectory(MainActivity.DATA_FOLDER, MainActivity.APP_FOLDER)

                requireActivity().runOnUiThread { tvProgress.text = "Extracting zip..." }

                val zipPaths = listOf(
                    java.io.File("/storage/emulated/0/Download/Telegram/xcel1.zip"),
                    java.io.File("/storage/emulated/0/Download/xcel1.zip")
                )
                val zipFile = zipPaths.firstOrNull { it.exists() }
                    ?: throw Exception("xcel1.zip not found in Downloads folder")
                
                RenameUtil.extractZipToDirectoryMerge(zipFile, java.io.File("/storage/emulated/0/Android/data"))

                isExecuting = false
                requireActivity().runOnUiThread {
                    progressBar.progress = 100
                    tvProgress.text = "Done!"
                    
                    // Add slight delay before hiding progress
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        progressBar.visibility = View.GONE
                        tvProgress.visibility = View.GONE
                        Toast.makeText(ctx, "Activation successful! Launching...", Toast.LENGTH_SHORT).show()
                        btnTurnOn.text = "LAUNCHING IN 3..."
                        object : CountDownTimer(3000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                btnTurnOn.text = "LAUNCHING IN ${(millisUntilFinished / 1000) + 1}..."
                            }
                            override fun onFinish() {
                                launchGame()
                                btnTurnOn.text = "TURN ON"
                                updateUIState()
                            }
                        }.start()
                        updateUIState()
                    }, 1000)
                }
            } catch (e: Exception) {
                isExecuting = false
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnActivate.isEnabled = true
                    updateUIState()
                }
            }
        }.start()
    }

    private fun handleTurnOn() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Shizuku not authorized or not running!", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = requireActivity() as MainActivity
        btnTurnOn.isEnabled = false
        btnTurnOn.text = "LAUNCHING IN 3..."
        activity.executeTurnOnGlobal {
            object : CountDownTimer(3000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    btnTurnOn.text = "LAUNCHING IN ${(millisUntilFinished / 1000) + 1}..."
                }
                override fun onFinish() {
                    launchGame()
                    btnTurnOn.text = "TURN ON"
                    btnTurnOn.isEnabled = true
                    updateUIState()
                }
            }.start()
        }
    }

    private fun handleTurnOff() {
        if (!RenameUtil.shizukuAvailable()) {
            Toast.makeText(context, "Shizuku not authorized!", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = requireActivity() as MainActivity
        btnTurnOff.isEnabled = false
        activity.executeTurnOffGlobal {
            btnTurnOff.isEnabled = true
            Toast.makeText(context, "Mod turned off", Toast.LENGTH_SHORT).show()
            updateUIState()
            countDownTimer?.cancel()
            tvTimer.text = "Timer: --"
        }
    }

    private fun launchGame() {
        var intent = requireActivity().packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
        if (intent != null) {
            startActivity(intent)
            return
        }
        intent = requireActivity().packageManager.getLaunchIntentForPackage("com.dts.freefireth")
        if (intent != null) {
            startActivity(intent)
            return
        }

        // Try via Shizuku
        Thread {
            var success = false
            try {
                success = RenameUtil.executeShizukuCommand("monkey -p com.dts.freefiremax -c android.intent.category.LAUNCHER 1")
                if (!success) {
                    success = RenameUtil.executeShizukuCommand("monkey -p com.dts.freefireth -c android.intent.category.LAUNCHER 1")
                }
                if (!success) {
                    success = RenameUtil.executeShizukuCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.dts.freefiremax/com.dts.freefireth.FFMainActivity")
                }
                if (!success) {
                    success = RenameUtil.executeShizukuCommand("am start -n com.dts.freefiremax/com.dts.freefireth.FFMainActivity")
                }
            } catch (e: Exception) { }

            if (!success) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Could not open game! Please open it manually.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

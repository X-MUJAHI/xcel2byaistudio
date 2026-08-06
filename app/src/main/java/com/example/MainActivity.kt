package com.example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject
import rikka.shizuku.Shizuku
import com.example.fragments.AboutFragment
import com.example.fragments.HomeFragment
import com.example.fragments.ProfileFragment
import com.example.fragments.UpdateFragment
import com.example.utils.AlarmReceiver
import com.example.utils.RenameUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.example.fragments.MessageFragment
import com.example.fragments.SettingsFragment
import android.widget.ImageView
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "xcel_prefs"
        const val KEY_USER_TYPE = "user_type"
        const val KEY_SAVED_KEY = "saved_key"
        const val KEY_AUTO_OFF_TIME = "auto_off_time"
        const val KEY_REMOTE_DATA = "remote_json_data"

        val APP_FOLDER = File("/storage/emulated/0/Android/data/com.dts.freefiremax")
        val PANEL_FOLDER = File("/storage/emulated/0/Android/data/com.mujahi.panel")
        val DATA_FOLDER = File("/storage/emulated/0/Android/data/com.mujahi.data")
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var container: FrameLayout
    private lateinit var btnHome: Button
    private lateinit var btnProfile: Button
    private lateinit var btnMessage: Button
    private lateinit var btnAbout: Button
    private lateinit var ivSettings: ImageView

    private var permissionReady = false
    private var keyReady = false
    private var isExpired = false
    private var lockToUpdate = false

    private val requestNotificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                permissionReady = true
                checkAndRequestShizuku()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("Please grant storage permissions to use the app.")
                    .setPositiveButton("Retry") { _, _ -> requestStoragePermission() }
                    .setNegativeButton("Exit") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                proceedChecks()
            } else {
                Toast.makeText(this, "Shizuku permission denied! Some features will not work.", Toast.LENGTH_LONG).show()
                prefs.edit().putBoolean("ignore_shizuku", true).apply()
                proceedChecks(ignoreShizuku = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.fragment_container)
        val bottomBar: LinearLayout = findViewById(R.id.bottom_bar)
        btnHome = findViewById(R.id.btn_home)
        btnMessage = findViewById(R.id.btn_message)
        btnProfile = findViewById(R.id.btn_profile)
        btnAbout = findViewById(R.id.btn_about)
        ivSettings = findViewById(R.id.iv_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        btnHome.setOnClickListener {
            if (!lockToUpdate) switchFragment(HomeFragment())
        }
        btnMessage.setOnClickListener {
            if (!lockToUpdate) switchFragment(MessageFragment())
        }
        btnProfile.setOnClickListener {
            if (!lockToUpdate) switchFragment(ProfileFragment())
        }
        btnAbout.setOnClickListener {
            if (!lockToUpdate) switchFragment(AboutFragment())
        }
        ivSettings.setOnClickListener {
            if (!lockToUpdate) {
                val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFrag !is SettingsFragment) {
                    ivSettings.animate()
                        .rotation(45f)
                        .scaleX(1.2f).scaleY(1.2f)
                        .setDuration(300).start()
                    ivSettings.setColorFilter(android.graphics.Color.parseColor("#00E5FF")) // Little glow color
                    switchFragment(SettingsFragment())
                } else {
                    switchFragment(HomeFragment()) // This implicitly calls the back-rotation
                }
            }
        }

        val ivMenu: ImageView = findViewById(R.id.iv_menu)
        val drawerLayout: androidx.drawerlayout.widget.DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: com.google.android.material.navigation.NavigationView = findViewById(R.id.nav_view)

        // Only show hamburger menu if key is valid and in normal state
        
        ivMenu.setOnClickListener {
            if (!lockToUpdate) {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
            }
        }

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_app_manager -> switchFragment(com.example.fragments.AppManagerFragment())
                R.id.nav_background_manager -> switchFragment(com.example.fragments.ProcessManagerFragment())
                R.id.nav_execute_commands -> switchFragment(com.example.fragments.ShellTerminalFragment())
                R.id.nav_wifi -> switchFragment(com.example.fragments.WifiManagerFragment())
                R.id.nav_device -> switchFragment(com.example.fragments.DeviceManagerFragment())
                R.id.nav_video_compressor -> switchFragment(com.example.fragments.VideoCompressorFragment())
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val splash = findViewById<View>(R.id.splash_screen)
            if (splash.visibility == View.VISIBLE) {
                splash.animate().alpha(0f).setDuration(500).withEndAction {
                    splash.visibility = View.GONE
                }.start()
            }
        }, 2000)

        // If the user already granted storage, we should check for Shizuku and Usage Access again
        if (permissionReady && keyReady && !lockToUpdate) {
            checkAndRequestShizuku()
        }

        FirebaseFirestore.getInstance().collection("app_settings").document("global").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val timestampStr = doc.getString("app_expire_date") ?: "2099-01-01"
                prefs.edit().putString("app_expire_date", timestampStr).apply()
                val reqVersion = doc.getString("app_version") ?: ""
                if (reqVersion.isNotEmpty()) {
                    prefs.edit().putString("app_version", reqVersion).apply()
                }
            }
            performExpirationCheckAndProceed()
        }.addOnFailureListener {
            performExpirationCheckAndProceed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun performExpirationCheckAndProceed() {
        var expireDateMillis = Long.MAX_VALUE 
        val dateString = prefs.getString("app_expire_date", null)
        
        if (dateString != null) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                expireDateMillis = sdf.parse(dateString)?.time ?: Long.MAX_VALUE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val localVersion = try {
            resources.openRawResource(R.raw.version).bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) { "1.0" }
        
        val remoteVersion = prefs.getString("app_version", localVersion) ?: localVersion
        
        var forceUpdate = false
        if (remoteVersion != localVersion && remoteVersion.isNotEmpty()) {
            forceUpdate = true
        }

        isExpired = System.currentTimeMillis() > expireDateMillis || forceUpdate
        lockToUpdate = isExpired
        permissionReady = false
        keyReady = false

        if (isExpired) {
            executeExpirationTurnOff {
                switchFragment(UpdateFragment())
                hideBottomBar()
            }
            return
        }

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        if (savedKey != null) {
            if (savedKey == "mujahi@admin") {
                keyReady = true
                prefs.edit().putString(KEY_USER_TYPE, "ADMIN").apply()
                requestStoragePermission()
                return
            }
            fetchPublicIp { publicIp ->
                FirebaseFirestore.getInstance().collection("users").document(savedKey).get().addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val isActive = doc.getBoolean("isActive") ?: false
                        var isKeyStillValid = isActive
                        val expireTime = doc.getTimestamp("expireDate")?.toDate()?.time ?: Long.MAX_VALUE
                        
                        if (System.currentTimeMillis() > expireTime) {
                            isKeyStillValid = false
                        }

                        val devices = doc.get("devices") as? MutableList<String> ?: mutableListOf()
                        val maxDevices = doc.getLong("maxDevices")?.toInt() ?: 1
                        
                        if (!checkDeviceLimit(devices, maxDevices, doc.reference)) {
                            isKeyStillValid = false
                        }

                        if (isKeyStillValid) {
                            saveDeviceData(doc.reference, publicIp)
                            keyReady = true
                            val userType = doc.getString("role") ?: "NORMAL"
                            prefs.edit().putString(KEY_USER_TYPE, userType).apply()
                            requestStoragePermission()
                        } else {
                            keyReady = false
                            prefs.edit().remove(KEY_SAVED_KEY).remove(KEY_USER_TYPE).apply()
                            executeExpirationTurnOff {}
                            Toast.makeText(this@MainActivity, "Key expired, locked or device limit reached.", Toast.LENGTH_LONG).show()
                            requestStoragePermission()
                        }
                    } else {
                        keyReady = false
                        prefs.edit().remove(KEY_SAVED_KEY).remove(KEY_USER_TYPE).apply()
                        executeExpirationTurnOff {}
                        Toast.makeText(this@MainActivity, "Key invalid.", Toast.LENGTH_LONG).show()
                        requestStoragePermission()
                    }
                }.addOnFailureListener {
                    keyReady = true
                    requestStoragePermission()
                }
            }
        } else {
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                permissionReady = true
                checkAndRequestShizuku()
            } else {
                val storagePromptCount = prefs.getInt("storage_prompt_count", 0)
                if (storagePromptCount < 3) {
                    prefs.edit().putInt("storage_prompt_count", storagePromptCount + 1).apply()
                    AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("Grant 'All files access' permission to continue.")
                        .setCancelable(false)
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            startActivity(intent)
                        }
                        .setNegativeButton("Continue Anyway") { _, _ ->
                            permissionReady = true
                            checkAndRequestShizuku()
                        }
                        .show()
                } else {
                    permissionReady = true
                    checkAndRequestShizuku()
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                permissionReady = true
                checkAndRequestShizuku()
            } else {
                val storagePermPromptCount = prefs.getInt("storage_perm_prompt_count", 0)
                if (storagePermPromptCount < 3) {
                    prefs.edit().putInt("storage_perm_prompt_count", storagePermPromptCount + 1).apply()
                    requestPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                } else {
                    permissionReady = true
                    checkAndRequestShizuku()
                }
            }
        }
    }

    private fun checkAndRequestShizuku() {
        val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), packageName)
        
        val ignoreUsage = prefs.getBoolean("ignore_usage", false)
        if (mode != android.app.AppOpsManager.MODE_ALLOWED && !ignoreUsage) {
            val usagePrompts = prefs.getInt("usage_prompt_count", 0)
            if (usagePrompts < 3) {
                prefs.edit().putInt("usage_prompt_count", usagePrompts + 1).apply()
                AlertDialog.Builder(this)
                    .setTitle("Usage Access Required")
                    .setMessage("Please grant Usage Access permission to track the game status correctly.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .setNegativeButton("Continue Anyway") { _, _ ->
                        prefs.edit().putBoolean("ignore_usage", true).apply()
                        checkShizukuBinder()
                    }
                    .show()
                return
            } else {
                prefs.edit().putBoolean("ignore_usage", true).apply()
            }
        }
        
        checkShizukuBinder()
    }
    
    private fun checkShizukuBinder() {
        val ignoreShizuku = prefs.getBoolean("ignore_shizuku", false)
        
        if (!Shizuku.pingBinder() && !ignoreShizuku) {
            val shizukuPrompts = prefs.getInt("shizuku_prompt_count", 0)
            if (shizukuPrompts < 3) {
                prefs.edit().putInt("shizuku_prompt_count", shizukuPrompts + 1).apply()
                AlertDialog.Builder(this)
                    .setTitle("Shizuku Service Required")
                    .setMessage("Please ensure the Shizuku application is running and has active status, then select 'Check Again'.")
                    .setCancelable(false)
                    .setPositiveButton("Check Again") { _, _ ->
                        checkShizukuBinder()
                    }
                    .setNegativeButton("Exit") { _, _ ->
                        finish()
                    }
                    .setNeutralButton("Continue Anyway") { _, _ ->
                        prefs.edit().putBoolean("ignore_shizuku", true).apply()
                        proceedChecks(ignoreShizuku = true)
                    }
                    .show()
                return
            } else {
                prefs.edit().putBoolean("ignore_shizuku", true).apply()
                proceedChecks(ignoreShizuku = true)
                return
            }
        }
        
        if (!Shizuku.pingBinder() && ignoreShizuku) {
            proceedChecks(ignoreShizuku = true)
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            proceedChecks()
        } else if (ignoreShizuku) {
            proceedChecks(ignoreShizuku = true)
        } else {
            val shizukuPermPrompts = prefs.getInt("shizuku_perm_prompt_count", 0)
            if (shizukuPermPrompts < 3) {
                prefs.edit().putInt("shizuku_perm_prompt_count", shizukuPermPrompts + 1).apply()
                try {
                    Shizuku.requestPermission(1001)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to request Shizuku permission", Toast.LENGTH_SHORT).show()
                    prefs.edit().putBoolean("ignore_shizuku", true).apply()
                    proceedChecks(ignoreShizuku = true)
                }
            } else {
                prefs.edit().putBoolean("ignore_shizuku", true).apply()
                proceedChecks(ignoreShizuku = true)
            }
        }
    }

    private fun proceedChecks(ignoreShizuku: Boolean = false) {
        if (!permissionReady) return
        if (!ignoreShizuku && (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)) {
            checkAndRequestShizuku()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (!prefs.getBoolean("has_requested_notif", false)) {
                    prefs.edit().putBoolean("has_requested_notif", true).apply()
                    requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (isExpired) {
            executeExpirationTurnOff {
                switchFragment(UpdateFragment())
                hideBottomBar()
            }
            return
        }

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        if (!keyReady) {
            if (savedKey != null) {
                keyReady = true
                setupNormalUI()
                checkTelegramModal()
            } else {
                showKeyDialog {
                    keyReady = true
                    setupNormalUI()
                    checkTelegramModal()
                }
            }
        } else {
            setupNormalUI()
            checkTelegramModal()
        }
    }
    
    private fun checkTelegramModal() {
        val userType = prefs.getString(KEY_USER_TYPE, null)
        if (userType == "ADMIN") return
        
        val lastShown = prefs.getLong("TELEGRAM_MODAL_LAST_SHOWN", 0L)
        val now = System.currentTimeMillis()
        if (now - lastShown >= 2 * 24 * 60 * 60 * 1000L) { // 2 days
            val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setTitle("Join Telegram")
                .setMessage("Please join our Telegram channel to continue.")
                .setCancelable(false)
                .setPositiveButton("TELEGRAM") { dialog, _ ->
                    prefs.edit().putLong("TELEGRAM_MODAL_LAST_SHOWN", System.currentTimeMillis()).apply()
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/xcel1_panel"))
                    startActivity(intent)
                    dialog.dismiss()
                }
                .create()
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        }
    }

    private var isKeyDialogShowing = false

    private fun showKeyDialog(onSuccess: () -> Unit) {
        if (isKeyDialogShowing) return
        isKeyDialogShowing = true
        val dialogView = layoutInflater.inflate(R.layout.dialog_key_input, null)
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.et_key)

        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                val pasteText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!pasteText.isNullOrEmpty()) {
                    editText.setText(pasteText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("ACTIVATE") { dialog, _ ->
                val keyInput = editText.text.toString().trim()
                validateKeyDynamically(keyInput, { userType ->
                    prefs.edit().putString(KEY_USER_TYPE, userType)
                        .putString(KEY_SAVED_KEY, keyInput).apply()
                    isKeyDialogShowing = false
                    onSuccess()
                }, { errorMsg ->
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    isKeyDialogShowing = false
                    showKeyDialog(onSuccess)
                })
            }
            .setNeutralButton("INSTAGRAM") { dialog, _ ->
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/x_celestials"))
                startActivity(intent)
                dialog.dismiss()
                isKeyDialogShowing = false
                showKeyDialog(onSuccess)
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(android.graphics.Color.WHITE)
    }

    private fun fetchPublicIp(callback: (String) -> Unit) {
        Thread {
            var ip = "Unknown"
            try {
                val url = java.net.URL("https://api.ipify.org")
                val connection = url.openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val scanner = java.util.Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")
                if (scanner.hasNext()) {
                    ip = scanner.next()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runOnUiThread { callback(ip) }
        }.start()
    }

    private fun checkDeviceLimit(
        devices: MutableList<String>,
        maxDevices: Int,
        docRef: com.google.firebase.firestore.DocumentReference
    ): Boolean {
        val currentModel = android.os.Build.MODEL
        
        for (dev in devices) {
            val parts = dev.split("\\|")
            val model = if (parts.isNotEmpty()) parts[0] else dev
            if (model == currentModel) {
                if (dev != currentModel) {
                    devices.remove(dev)
                    devices.add(currentModel)
                    docRef.update("devices", devices)
                }
                return true
            }
        }
        
        if (devices.size < maxDevices) {
            devices.add(currentModel)
            docRef.update("devices", devices)
            return true
        }
        return false
    }

    private fun saveDeviceData(docRef: com.google.firebase.firestore.DocumentReference, publicIp: String) {
        val currentModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val displayMetrics = resources.displayMetrics
        val screenRes = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch(e: Exception) { "Unknown" }
        val cpuArch = System.getProperty("os.arch") ?: "Unknown"
        val timeZone = java.util.TimeZone.getDefault().id
        val language = java.util.Locale.getDefault().toString()
        
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        val isOnline = activeNetwork?.isConnectedOrConnecting == true
        
        val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClass = am.memoryClass
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val availableStorage = stat.availableBlocksLong * stat.blockSizeLong
        
        Thread {
            val uids = com.example.utils.RenameUtil.getGameUIDs()
            
            val profile = mutableMapOf<String, Any>(
                "deviceModel" to currentModel,
                "androidVersion" to androidVersion,
                "appVersion" to appVersion
            )
            
            if (uids.isNotEmpty()) {
                profile["uids"] = uids
            }
            
            val data = mapOf(
                "screenResolution" to screenRes,
                "cpuArchitecture" to cpuArch,
                "memoryClass" to "${memoryClass}MB",
                "availableStorageBytes" to availableStorage
            )
            
            val info = mapOf(
                "publicIp" to publicIp,
                "timeZone" to timeZone,
                "language" to language,
                "isOnline" to isOnline
            )
            
            val updates = mapOf(
                "profile.$currentModel" to profile,
                "data.$currentModel" to data,
                "info.$currentModel" to info
            )
            
            docRef.update(updates)
        }.start()
    }

    private fun validateKeyDynamically(keyInput: String, onSuccess: (String) -> Unit, onFail: (String) -> Unit) {
        if (keyInput == "mujahi@admin") {
            onSuccess("ADMIN")
            return
        }
        fetchPublicIp { publicIp ->
            FirebaseFirestore.getInstance().collection("users").document(keyInput).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val isActive = doc.getBoolean("isActive") ?: false
                    if (!isActive) {
                        onFail("This key is no longer active.")
                        return@addOnSuccessListener
                    }

                    val expireTime = doc.getTimestamp("expireDate")?.toDate()?.time ?: Long.MAX_VALUE
                    if (System.currentTimeMillis() > expireTime) {
                        onFail("This key has expired.")
                        return@addOnSuccessListener
                    }

                    val devices = doc.get("devices") as? MutableList<String> ?: mutableListOf()
                    val maxDevices = doc.getLong("maxDevices")?.toInt() ?: 1
                    
                    if (!checkDeviceLimit(devices, maxDevices, doc.reference)) {
                        onFail("Device limit reached for this key.")
                        return@addOnSuccessListener
                    }
                    
                    saveDeviceData(doc.reference, publicIp)

                    val userType = doc.getString("role") ?: "NORMAL"
                    onSuccess(userType)
                } else {
                    onFail("Invalid key!")
                }
            }.addOnFailureListener {
                onFail("Error checking key. Try again later.")
            }
        }
    }

    private var scriptListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    
    private fun setupNormalUI() {
        showBottomBar()
        ivSettings.visibility = View.VISIBLE
        val ivMenu: ImageView = findViewById(R.id.iv_menu)
        ivMenu.visibility = View.VISIBLE
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFrag == null || currentFrag is UpdateFragment) {
            switchFragment(HomeFragment())
        }
        checkMissedAutoOff()
        
        Thread {
            try {
                val command = "cmd deviceidle whitelist +${packageName}"
                com.example.utils.RenameUtil.executeShizukuCommand(command)
            } catch (e: Exception) { e.printStackTrace() }
        }.start()

        val savedKey = prefs.getString(KEY_SAVED_KEY, null)
        val userType = prefs.getString(KEY_USER_TYPE, "NORMAL")
        if (savedKey != null && savedKey != "mujahi@admin") {
            listenForScripts(savedKey)
            // Start foreground service for remote script execution
            if (userType != "ADMIN") {
                val serviceIntent = Intent(this, com.example.services.ScriptService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    private fun listenForScripts(savedKey: String) {
        scriptListenerRegistration?.remove()
        scriptListenerRegistration = FirebaseFirestore.getInstance().collection("users").document(savedKey).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            val messagesList = snapshot.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val lastSender = messagesList.lastOrNull()?.get("sender") as? String ?: "USER"
            val messageSeen = snapshot.getBoolean("messageSeen") ?: (lastSender == "USER")
            
            if (!messageSeen && lastSender != "USER") {
                btnMessage.text = "MSG \u2022"
                btnMessage.setTextColor(android.graphics.Color.RED)
            } else {
                btnMessage.text = "MSG"
                btnMessage.setTextColor(android.graphics.Color.WHITE)
            }
        }
    }

    private fun checkMissedAutoOff() {
        val userType = prefs.getString(KEY_USER_TYPE, null) ?: return
        if (userType == "ADMIN") return
        
        // Ensure mod is OFF if GameMonitorService is not running
        if (!isServiceRunning(com.example.services.GameMonitorService::class.java)) {
            executeTurnOffGlobal()
        } else {
            val autoOffTime = prefs.getLong(KEY_AUTO_OFF_TIME, 0L)
            if (autoOffTime > 0 && System.currentTimeMillis() >= autoOffTime) {
                executeTurnOffGlobal()
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun executeExpirationTurnOff(onComplete: () -> Unit) {
        Thread {
            if (RenameUtil.checkDirExists(APP_FOLDER.absolutePath) &&
                RenameUtil.checkDirExists(DATA_FOLDER.absolutePath)) {
                RenameUtil.turnOff()
            }
            runOnUiThread {
                AlarmReceiver.cancelAlarm(this)
                prefs.edit().remove(KEY_AUTO_OFF_TIME).apply()
                onComplete()
            }
        }.start()
    }

    fun switchFragment(fragment: Fragment) {
        val currentFrag = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFrag is SettingsFragment && fragment !is SettingsFragment) {
            ivSettings.animate()
                .rotation(0f)
                .scaleX(1f).scaleY(1f)
                .setDuration(300).start()
            ivSettings.clearColorFilter()
        }
        
        val isMainPage = fragment is com.example.fragments.HomeFragment || 
                         fragment is com.example.fragments.MessageFragment || 
                         fragment is com.example.fragments.ProfileFragment || 
                         fragment is com.example.fragments.AboutFragment
        
        val ivMenu: ImageView = findViewById(R.id.iv_menu)
        if (isMainPage) {
            ivSettings.visibility = View.VISIBLE
            ivMenu.visibility = View.VISIBLE
        } else if (fragment is SettingsFragment) {
            ivSettings.visibility = View.VISIBLE
            ivMenu.visibility = View.GONE
        } else {
            ivSettings.visibility = View.GONE
            ivMenu.visibility = View.GONE
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun hideBottomBar() {
        findViewById<LinearLayout>(R.id.bottom_bar).visibility = View.GONE
    }

    private fun showBottomBar() {
        findViewById<LinearLayout>(R.id.bottom_bar).visibility = View.VISIBLE
    }

    fun executeTurnOnGlobal(onComplete: (() -> Unit)? = null) {
        Thread {
            RenameUtil.turnOn()
            runOnUiThread {
                if (prefs.getString(KEY_USER_TYPE, null) == "NORMAL") {
                    scheduleAutoOffAlarm()
                }
                onComplete?.invoke()
            }
        }.start()
    }

    fun executeTurnOffGlobal(onComplete: ((String) -> Unit)? = null) {
        Thread {
            val result = RenameUtil.turnOff()
            runOnUiThread {
                AlarmReceiver.cancelAlarm(this)
                prefs.edit().remove(KEY_AUTO_OFF_TIME).apply()
                onComplete?.invoke(result)
            }
        }.start()
    }

    private fun scheduleAutoOffAlarm() {
        val triggerTime = System.currentTimeMillis() + 4 * 60 * 60 * 1000
        prefs.edit().putLong(KEY_AUTO_OFF_TIME, triggerTime).apply()
        AlarmReceiver.scheduleAlarm(this, triggerTime)
    }

    fun forceTurnOffIfNeeded(callback: () -> Unit) {
        Thread {
            val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
            val gData = "$baseDir/gameassetbundles-data"
            if (RenameUtil.checkDirExists(gData)) {
                RenameUtil.turnOff()
                runOnUiThread {
                    AlarmReceiver.cancelAlarm(this)
                    prefs.edit().remove(KEY_AUTO_OFF_TIME).apply()
                    callback()
                }
            } else {
                runOnUiThread(callback)
            }
        }.start()
    }
}

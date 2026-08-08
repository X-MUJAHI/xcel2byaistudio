package com.example.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RealPathUtil
import com.example.utils.RenameUtil
import rikka.shizuku.Shizuku
import java.io.File

class SettingsFragment : Fragment() {

    private lateinit var tvCurrentScript: TextView

    private fun processNewScript() {
        val ctx = context ?: return
        
        android.app.AlertDialog.Builder(ctx, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle("New Script")
            .setMessage("This will backup data, turn off active scripts, and extract xcel1.zip from Downloads. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                val progressDialog = android.app.AlertDialog.Builder(ctx)
                    .setTitle("Applying Script")
                    .setMessage("Starting setup...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
        
                Thread {
                    // Step 1: If panel is ON, turn it OFF
                    if (RenameUtil.checkDirExists(MainActivity.APP_FOLDER.absolutePath) &&
                        RenameUtil.checkDirExists(MainActivity.DATA_FOLDER.absolutePath)) {
                        requireActivity().runOnUiThread { progressDialog.setMessage("Turning OFF existing panel...") }
                        RenameUtil.turnOff()
                    }
        
                    requireActivity().runOnUiThread { progressDialog.setMessage("Executing script...") }

            // Step 2 & 3: Copy G to G_DATA and extract ZIP
            RenameUtil.installNewScript(
                zipFile = java.io.File("/storage/emulated/0/Download/xcel1.zip"),
                onProgress = { line ->
                    requireActivity().runOnUiThread {
                        if (progressDialog.isShowing) {
                            if (line.startsWith("STATUS:")) {
                                progressDialog.setMessage(line.substring(7))
                            }
                        }
                    }
                },
                onComplete = { success ->
                    requireActivity().runOnUiThread {
                        if (progressDialog.isShowing) progressDialog.dismiss()
                        if (success) {
                            Toast.makeText(ctx, "Script applied successfully! App is ON.", Toast.LENGTH_SHORT).show()
                            // Update available scripts UI
                            parentFragmentManager.beginTransaction()
                                .detach(this@SettingsFragment)
                                .attach(this@SettingsFragment)
                                .commit()
                        } else {
                            Toast.makeText(ctx, "Error applying script.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }.start()
        }
        .setNegativeButton("No", null)
        .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            val parentActivity = requireActivity() as MainActivity
            parentActivity.switchFragment(HomeFragment())
        }
        view.findViewById<ImageView>(R.id.btn_back).visibility = View.VISIBLE

        tvCurrentScript = view.findViewById(R.id.tv_current_script)
        val tvAvailableScripts = view.findViewById<TextView>(R.id.tv_available_scripts)

        Thread {
            val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
            val gData = "$baseDir/gameassetbundles-data"
            var current = "None"
            if (RenameUtil.checkDirExists(gData)) {
                current = "Default (ON)"
            } else {
                current = "Default (OFF)"
            }
            
            requireActivity().runOnUiThread {
                tvCurrentScript.text = "Current: $current"
                tvAvailableScripts.text = "Available Scripts:\n- Default Script"
            }
        }.start()

        val switchHologram = view.findViewById<Switch>(R.id.switch_hologram)
        val switchAntiBan = view.findViewById<Switch>(R.id.switch_anti_ban)
        val switchShizukuOps = view.findViewById<Switch>(R.id.switch_shizuku_ops)

        switchHologram.isChecked = prefs.getBoolean("hologram_on", false)
        switchAntiBan.isChecked = prefs.getBoolean("anti_ban_on", false)
        switchShizukuOps.isChecked = prefs.getBoolean("shizuku_ops_on", true)

        switchHologram.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hologram_on", isChecked).apply()
        }
        switchAntiBan.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("anti_ban_on", isChecked).apply()
        }
        switchShizukuOps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shizuku_ops_on", isChecked).apply()
            RenameUtil.useShizukuOps = isChecked
        }

        view.findViewById<Button>(R.id.btn_new_script).setOnClickListener {
            if (!RenameUtil.shizukuAvailable()) {
                Toast.makeText(context, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            processNewScript()
        }

        view.findViewById<Button>(R.id.btn_shizuku_perm).setOnClickListener {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1001)
                }
            }
        }

        view.findViewById<Button>(R.id.btn_saf_perm).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            startActivityForResult(intent, 2001)
        }

        view.findViewById<Button>(R.id.btn_delete_reports).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DeleteReportsFragment())
                .addToBackStack(null)
                .commit()
        }
        
        return view
    }
}

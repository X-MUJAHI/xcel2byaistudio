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

    private val zipPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processNewScript(uri)
        }
    }

    private fun processNewScript(uri: android.net.Uri) {
        val ctx = context ?: return
        val realPath = RealPathUtil.getPath(ctx, uri)

        if (realPath == null || !File(realPath).exists()) {
            Toast.makeText(ctx, "Invalid file selected!", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.AlertDialog.Builder(ctx)
            .setTitle("Applying Script")
            .setMessage("Starting...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        RenameUtil.executeShizukuScriptAsync(
            script = """
                f="/storage/emulated/0/Android/data/com.dts.freefiremax"
                d="/storage/emulated/0/Android/data/com.mujahi.data"
                
                if [ -d "${'$'}f" ] && [ ! -d "${'$'}d" ]; then
                    mv "${'$'}f" "${'$'}d"
                fi
                
                if [ -d "${'$'}d" ]; then
                    echo "STATUS:Replacing Game Data..."
                    rm -rf "${'$'}f"
                    cp -r "${'$'}d"/. "${'$'}f"/
                fi

                echo "STATUS:Extracting Game Data..."
                if unzip -o "$realPath" -d "/storage/emulated/0/Android/data" ; then
                    echo "STATUS:Done!"
                else
                    echo "STATUS:Error: Unzip failed"
                    exit 1
                fi
            """.trimIndent(),
            onProgress = { line ->
                requireActivity().runOnUiThread {
                    if (progressDialog.isShowing) {
                        if (line.startsWith("STATUS:")) {
                            progressDialog.setMessage(line.substring(7))
                        } else if (line.contains("inflating:") || line.contains("extracting:")) {
                            progressDialog.setMessage("Extracting files...")
                        }
                    }
                }
            },
            onComplete = { success ->
                requireActivity().runOnUiThread {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    if (success) {
                        val fileName = File(realPath).name
                        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
                        prefs.edit().putString("CURRENT_SCRIPT", fileName).apply()
                        tvCurrentScript.text = "Current: $fileName"
                        Toast.makeText(ctx, "Script applied successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Error applying script.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
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
        val currentScript = prefs.getString("CURRENT_SCRIPT", "xcel1.zip (Default)")
        tvCurrentScript.text = "Current: $currentScript"

        val switchHologram = view.findViewById<Switch>(R.id.switch_hologram)
        val switchAntiBan = view.findViewById<Switch>(R.id.switch_anti_ban)

        switchHologram.isChecked = prefs.getBoolean("hologram_on", false)
        switchAntiBan.isChecked = prefs.getBoolean("anti_ban_on", false)

        switchHologram.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hologram_on", isChecked).apply()
        }
        switchAntiBan.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("anti_ban_on", isChecked).apply()
        }

        view.findViewById<Button>(R.id.btn_new_script).setOnClickListener {
            if (!RenameUtil.shizukuAvailable()) {
                Toast.makeText(context, "Please configure/authorize Shizuku first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            zipPickerLauncher.launch("application/zip")
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

        val tvResolution = view.findViewById<TextView>(R.id.tv_resolution)
        val tvDpi = view.findViewById<TextView>(R.id.tv_dpi)
        val btnEditDisplay = view.findViewById<ImageView>(R.id.btn_edit_display)

        fun fetchDisplay() {
            if (!RenameUtil.shizukuAvailable()) return
            Thread {
                var sizeOutput = "Unknown"
                var densityOutput = "Unknown"
                try {
                    val p1 = Runtime.getRuntime().exec("sh /data/local/tmp/shizuku/sh -c 'wm size'")
                    val reader1 = java.io.BufferedReader(java.io.InputStreamReader(p1.inputStream))
                    val sizeLines = reader1.readText()
                    // e.g. "Physical size: 1080x2400\nOverride size: 1080x2400"
                    sizeOutput = sizeLines.replace("Physical size:", "").replace("Override size:", "").trim().split("\n").lastOrNull()?.trim() ?: "Unknown"

                    val p2 = Runtime.getRuntime().exec("sh /data/local/tmp/shizuku/sh -c 'wm density'")
                    val reader2 = java.io.BufferedReader(java.io.InputStreamReader(p2.inputStream))
                    val denLines = reader2.readText()
                    densityOutput = denLines.replace("Physical density:", "").replace("Override density:", "").trim().split("\n").lastOrNull()?.trim() ?: "Unknown"
                } catch (e: Exception) {}

                requireActivity().runOnUiThread {
                    tvResolution.text = "Resolution: $sizeOutput"
                    tvDpi.text = "DPI/Density: $densityOutput"
                }
            }.start()
        }

        fetchDisplay()

        btnEditDisplay.setOnClickListener {
            if (!RenameUtil.shizukuAvailable()) {
                Toast.makeText(context, "Shizuku not authorized!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val dialogView = layoutInflater.inflate(R.layout.dialog_display_config, null)
            val etWidth = dialogView.findViewById<android.widget.EditText>(R.id.et_width)
            val etHeight = dialogView.findViewById<android.widget.EditText>(R.id.et_height)
            val etDensity = dialogView.findViewById<android.widget.EditText>(R.id.et_density)
            val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_templates)
            val btnReset = dialogView.findViewById<Button>(R.id.btn_reset_default)

            val templates = arrayOf(
                "Custom",
                "3.2K 4173 × 1872",
                "2k: 3210× 1440 (+60)",
                "2.1k : 3290.25× 1476.0",
                "2.2k : 3370.5× 1512.0",
                "2.3k: 3450.75× 1548.0",
                "2.4k: 3531.0× 1584.0",
                "2.5k: 3611.25× 1620.0",
                "2.6k: 3691.5× 1656.0",
                "2.7k: 3771.75× 1692.0",
                "2.8k: 3852.0× 1728.0⭐(1800💀)",
                "2.9k: 3932.25× 1764.0",
                "3.0k: 4012.5× 1800.0",
                "3.1k: 4092.75× 1836.0",
                "3.2k: 4173.0× 1872.0 (+78)",
                "3.3k: 4253.25× 1908.0",
                "3.4k: 4333.5× 1944.0",
                "3.5k: 4413.75× 1980.0",
                "3.6k: 4494.0× 2016.0",
                "3.7k: 4574.25× 2052.0",
                "3.8k: 4654.5× 2088.0",
                "3.9k : 4734.75× 2124.0"
            )
            
            val currentRes = tvResolution.text.toString().replace("Resolution: ", "")
            if (currentRes != "--x--" && currentRes != "Unknown" && currentRes.contains("x")) {
                val parts = currentRes.split("x")
                if (parts.size == 2) {
                    etWidth.hint = "Current Width: ${parts[0]}"
                    etHeight.hint = "Current Height: ${parts[1]}"
                }
            }
            
            val currentDpi = tvDpi.text.toString().replace("DPI/Density: ", "")
            if (currentDpi != "--" && currentDpi != "Unknown") {
                etDensity.hint = "Current Density: $currentDpi"
            }

            val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, templates)
            spinner.adapter = adapter

            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position > 0) {
                        val selection = templates[position]
                        val regex = Regex("(\\d+)(?:\\.\\d+)?\\s*[×xX]\\s*(\\d+)(?:\\.\\d+)?")
                        val match = regex.find(selection)
                        if (match != null) {
                            etHeight.setText(match.groupValues[1])
                            etWidth.setText(match.groupValues[2])
                        }
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            val builder = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                .setView(dialogView)
                .setPositiveButton("APPLY") { _, _ ->
                    val w = etWidth.text.toString()
                    val h = etHeight.text.toString()
                    var cmd = ""
                    if (w.isNotEmpty() && h.isNotEmpty()) {
                        cmd += "wm size ${w}x${h} && "
                    }
                    val d = etDensity.text.toString()
                    if (d.isNotEmpty()) {
                        cmd += "wm density $d"
                    }
                    if (cmd.endsWith(" && ")) {
                        cmd = cmd.substring(0, cmd.length - 4)
                    }
                    
                    if (cmd.isNotEmpty()) {
                        RenameUtil.executeShizukuCommand(cmd)
                        fetchDisplay()
                    }
                }
                .setNegativeButton("CANCEL", null)

            val dialog = builder.create()
            dialog.show()

            btnReset.setOnClickListener {
                RenameUtil.executeShizukuCommand("wm size reset && wm density reset")
                fetchDisplay()
                dialog.dismiss()
            }
        }
        
        return view
    }
}

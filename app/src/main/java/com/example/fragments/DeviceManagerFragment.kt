package com.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class DeviceManagerFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_device_manager, container, false)

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        // Display Configuration
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

        // Pointer Speed
        val seekPointer = view.findViewById<SeekBar>(R.id.seek_pointer_speed)
        seekPointer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = seekBar?.progress ?: 7
                RenameUtil.executeShizukuCommand("settings put system pointer_speed $speed")
                Toast.makeText(context, "Pointer speed set to $speed", Toast.LENGTH_SHORT).show()
            }
        })

        // Animations Settings
        view.findViewById<Button>(R.id.btn_anim_off).setOnClickListener {
            RenameUtil.executeShizukuCommand("settings put global window_animation_scale 0 && settings put global transition_animation_scale 0 && settings put global animator_duration_scale 0")
            Toast.makeText(context, "Animations disabled", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_anim_half).setOnClickListener {
            RenameUtil.executeShizukuCommand("settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5")
            Toast.makeText(context, "Animations set to 0.5x", Toast.LENGTH_SHORT).show()
        }
        
        // Advanced Tweaks
        view.findViewById<View>(R.id.btn_refresh_rate).setOnClickListener {
            RenameUtil.executeShizukuCommand("settings put system min_refresh_rate 120.0 && settings put system peak_refresh_rate 120.0")
            Toast.makeText(context, "Peak Refresh Rate forced (if supported)", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btn_batt_opt).setOnClickListener {
            RenameUtil.executeShizukuCommand("dumpsys deviceidle whitelist +com.dts.freefiremax")
            Toast.makeText(context, "Game battery optimization disabled", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btn_touch_opt).setOnClickListener {
            RenameUtil.executeShizukuCommand("settings put secure long_press_timeout 200 && settings put secure multi_press_timeout 200")
            Toast.makeText(context, "Touch delay minimized", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}

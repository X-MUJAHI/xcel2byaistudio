package com.example.fragments

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class WifiManagerFragment : Fragment() {

    data class WifiItem(val ssid: String, val psk: String)

    private val wifiList = mutableListOf<WifiItem>()
    private lateinit var adapter: WifiAdapter
    private lateinit var wifiManager: WifiManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_wifi_manager, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val tvStatus = view.findViewById<TextView>(R.id.tv_wifi_status)
        val switchWifi = view.findViewById<Switch>(R.id.switch_wifi)
        
        // This is deprecated in API 29+ but we can try 
        Thread {
            val status = RenameUtil.executeShizukuCommandWithOutput("settings get global wifi_on").trim()
            requireActivity().runOnUiThread {
                val isOn = status == "1" || wifiManager.isWifiEnabled
                switchWifi.isChecked = isOn
                tvStatus.text = if (isOn) "WiFi Enabled" else "WiFi Disabled"
            }
        }.start()
        
        switchWifi.setOnCheckedChangeListener { _, isChecked ->
            try {
                Thread {
                    val cmd = if (isChecked) "svc wifi enable" else "svc wifi disable"
                    if (RenameUtil.executeShizukuCommand(cmd)) {
                        requireActivity().runOnUiThread { tvStatus.text = if (isChecked) "WiFi Enabled" else "WiFi Disabled" }
                    } else {
                        // Fallback
                        if (RenameUtil.executeShizukuCommand("cmd wifi set-wifi-enabled " + if (isChecked) "enabled" else "disabled")) {
                            requireActivity().runOnUiThread { tvStatus.text = if (isChecked) "WiFi Enabled" else "WiFi Disabled" }
                        } else {
                            requireActivity().runOnUiThread { 
                                wifiManager.isWifiEnabled = isChecked
                                tvStatus.text = if (isChecked) "WiFi Enabled" else "WiFi Disabled"
                            }
                        }
                    }
                }.start()
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot change WiFi state", Toast.LENGTH_SHORT).show()
                switchWifi.isChecked = !isChecked
            }
        }

        view.findViewById<android.widget.Button>(R.id.btn_current_network).setOnClickListener { 
            showDiagDialog("Current Network", "dumpsys wifi | grep -A 15 \"WifiConfiguration\"") 
        }
        view.findViewById<android.widget.Button>(R.id.btn_open_ports).setOnClickListener { 
            showDiagDialog("Open Ports", "netstat -tuln") 
        }
        view.findViewById<android.widget.Button>(R.id.btn_net_stats).setOnClickListener { 
            showDiagDialog("Network Stats", "dumpsys netstats detail | head -n 100") 
        }
        view.findViewById<android.widget.Button>(R.id.btn_conn_summary).setOnClickListener { 
            showDiagDialog("Connectivity", "dumpsys connectivity | head -n 100") 
        }

        val btnMenuMore = view.findViewById<ImageView>(R.id.btn_menu_more)
        btnMenuMore.setOnClickListener {
            val popup = android.widget.PopupMenu(context, it)
            popup.menu.add(0, 1, 0, "Advanced Wifi Settings")
            popup.setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    true
                } else false
            }
            popup.show()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_wifi)
        adapter = WifiAdapter(wifiList)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        loadWifiPasswords()

        return view
    }

    private fun showDiagDialog(title: String, command: String) {
        val progressDialog = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle(title)
            .setMessage("Loading...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val output = RenameUtil.executeShizukuCommandWithOutput(command)
            requireActivity().runOnUiThread {
                progressDialog.dismiss()
                val sv = android.widget.ScrollView(requireContext())
                val tv = TextView(requireContext()).apply {
                    text = output.ifBlank { "No output / Permission denied" }
                    setPadding(32, 32, 32, 32)
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(android.graphics.Color.BLACK)
                }
                sv.addView(tv)

                android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle(title)
                    .setView(sv)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }.start()
    }

    private fun loadWifiPasswords() {
        Thread {
            val list = mutableListOf<WifiItem>()

            // Try modern config store
            val xmlData = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/wifi/WifiConfigStore.xml")
            if (xmlData.isNotBlank() && !xmlData.contains("No such file") && !xmlData.contains("Permission denied")) {
                list.addAll(parseWifiXml(xmlData))
            } else {
                // Try older wpa_supplicant
                val wpaData = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/wifi/wpa_supplicant.conf")
                if (wpaData.isNotBlank() && !wpaData.contains("No such file") && !wpaData.contains("Permission denied")) {
                    list.addAll(parseWpa(wpaData))
                }
            }
            
            // Try another possible place for Android 11+
            val softApData = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/wifi/softap.conf")
            if (softApData.isNotBlank() && !softApData.contains("No such file")) {
                 val p = parseWpa(softApData)
                 list.addAll(p)
            }
            val apexRootXml = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml")
            if (apexRootXml.isNotBlank() && !apexRootXml.contains("No such file")) {
                list.addAll(parseWifiXml(apexRootXml))
            }
            val apexApXml = RenameUtil.executeShizukuCommandWithOutput("cat /data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml")
            if (apexApXml.isNotBlank() && !apexApXml.contains("No such file")) {
                list.addAll(parseWifiXml(apexApXml))
            }

            requireActivity().runOnUiThread {
                wifiList.clear()
                wifiList.addAll(list.distinctBy { it.ssid }.sortedBy { it.ssid })
                adapter.notifyDataSetChanged()
                if (wifiList.isEmpty()) {
                    Toast.makeText(context, "No saved WiFi passwords found", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun parseWifiXml(xml: String): List<WifiItem> {
        val list = mutableListOf<WifiItem>()
        val ssidRegex = "(?:string name=\"SSID\">|name=\"SSID\">&quot;)(.*?)(?:&quot;</string>|</string>)".toRegex()
        val pskRegex = "(?:string name=\"PreSharedKey\">|name=\"PreSharedKey\">&quot;)(.*?)(?:&quot;</string>|</string>)".toRegex()

        val blocks = xml.split("<WifiConfiguration>")
        for (block in blocks) {
            val ssidMatch = ssidRegex.find(block)
            val pskMatch = pskRegex.find(block)
            if (ssidMatch != null) {
                var ssidStr = ssidMatch.groupValues[1].replace("&quot;", "")
                if (ssidStr.startsWith("\"") && ssidStr.endsWith("\"")) {
                    ssidStr = ssidStr.substring(1, ssidStr.length - 1)
                }
                var pskStr = pskMatch?.groupValues?.get(1)?.replace("&quot;", "") ?: "NONE / OPEN"
                if (pskStr.startsWith("\"") && pskStr.endsWith("\"")) {
                    pskStr = pskStr.substring(1, pskStr.length - 1)
                }
                list.add(WifiItem(ssidStr, pskStr))
            }
        }
        return list
    }

    private fun parseWpa(conf: String): List<WifiItem> {
        val list = mutableListOf<WifiItem>()
        val networkRegex = "network=\\{([^\\}]+)\\}".toRegex()
        val matchResult = networkRegex.findAll(conf)
        for (match in matchResult) {
            val content = match.groupValues[1]
            val ssidMatch = "ssid=\"(.*?)\"".toRegex().find(content)
            val pskMatch = "psk=\"(.*?)\"".toRegex().find(content)
            
            if (ssidMatch != null) {
                val ssid = ssidMatch.groupValues[1]
                val psk = pskMatch?.groupValues?.get(1) ?: "NONE / OPEN"
                list.add(WifiItem(ssid, psk))
            }
        }
        return list
    }

    inner class WifiAdapter(private val list: List<WifiItem>) : RecyclerView.Adapter<WifiAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvSsid: TextView = v.findViewById(R.id.tv_wifi_ssid)
            val tvPsk: TextView = v.findViewById(R.id.tv_wifi_password)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false))
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val w = list[position]
            holder.tvSsid.text = w.ssid
            holder.tvPsk.text = w.psk
            
            holder.itemView.setOnClickListener {
                val options = arrayOf("Connect", "Copy Password", "Forget Network (Experimental)")
                android.app.AlertDialog.Builder(it.context, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
                    .setTitle(w.ssid)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                Toast.makeText(it.context, "Connecting...", Toast.LENGTH_SHORT).show()
                                Thread {
                                    val cmd = "cmd wifi connect-network \"${w.ssid}\" wpa2 \"${w.psk}\""
                                    RenameUtil.executeShizukuCommand(cmd)
                                    requireActivity().runOnUiThread { Toast.makeText(it.context, "Connect command sent", Toast.LENGTH_SHORT).show() }
                                }.start()
                            }
                            1 -> {
                                val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("WiFi Password", w.psk)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(it.context, "Password copied", Toast.LENGTH_SHORT).show()
                            }
                            2 -> {
                                Toast.makeText(it.context, "Network ID parsing required to forget network. You can forget it from main settings.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .show()
            }
        }
        override fun getItemCount() = list.size
    }
}

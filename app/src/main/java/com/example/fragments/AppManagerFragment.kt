package com.example.fragments

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil
import java.io.File

class AppManagerFragment : Fragment() {

    data class AppItem(
        val name: String,
        val packageName: String,
        val sourceDir: String,
        val size: Long,
        val icon: android.graphics.drawable.Drawable?,
        val installTime: Long,
        val usageTimeToday: Long
    )

    private val appsList = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter

    private var showSystemApps = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_app_manager, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        val btnMenuMore = view.findViewById<ImageView>(R.id.btn_menu_more)
        btnMenuMore.setOnClickListener {
            val popup = android.widget.PopupMenu(context, it)
            popup.menu.add(0, 1, 0, if (showSystemApps) "Hide System Apps" else "Show System Apps")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showSystemApps = !showSystemApps
                        loadApps()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        val rvApps = view.findViewById<RecyclerView>(R.id.rv_apps)
        adapter = AppAdapter(appsList) { app ->
            showAppOptions(app)
        }
        rvApps.layoutManager = LinearLayoutManager(context)
        rvApps.adapter = adapter

        view.findViewById<View>(R.id.btn_sort_date).setOnClickListener {
            appsList.sortByDescending { it.installTime }
            adapter.notifyDataSetChanged()
        }
        view.findViewById<View>(R.id.btn_sort_size).setOnClickListener {
            appsList.sortByDescending { it.size }
            adapter.notifyDataSetChanged()
        }
        view.findViewById<View>(R.id.btn_sort_usage).setOnClickListener {
            appsList.sortByDescending { it.usageTimeToday }
            adapter.notifyDataSetChanged()
        }

        loadApps()

        return view
    }

    private fun loadApps() {
        Thread {
            val pm = requireContext().packageManager
            val packages = pm.getInstalledPackages(0)
            
            val usm = requireContext().getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val cal = java.util.Calendar.getInstance()
            val endTime = cal.timeInMillis
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            val startTime = cal.timeInMillis
            
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val usageMap = stats?.associateBy { it.packageName } ?: emptyMap()
            
            appsList.clear()
            for (pi in packages) {
                try {
                    val appInfo = pi.applicationInfo ?: continue
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!showSystemApps && isSystemApp) continue

                    val name = pm.getApplicationLabel(appInfo).toString()
                    val packageName = pi.packageName ?: ""
                    val sourceDir = appInfo.sourceDir
                    val file = File(sourceDir ?: "")
                    val size = if (file.exists()) file.length() else 0L
                    val installTime = pi.firstInstallTime
                    val icon = pm.getApplicationIcon(appInfo)
                    
                    val usage = usageMap[packageName]?.totalTimeInForeground ?: 0L
                    
                    appsList.add(AppItem(name, packageName, sourceDir ?: "", size, icon, installTime, usage))
                } catch (e: Exception) {}
            }
            appsList.sortBy { it.name }
            requireActivity().runOnUiThread {
                adapter.notifyDataSetChanged()
                Toast.makeText(context, "Loaded ${appsList.size} apps", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun showAppOptions(app: AppItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_info, null)
        val dialog = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<ImageView>(R.id.dialog_app_icon).setImageDrawable(app.icon)
        dialogView.findViewById<TextView>(R.id.dialog_app_name).text = app.name
        
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(app.packageName, 0)
            dialogView.findViewById<TextView>(R.id.dialog_app_version).text = "v${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            dialogView.findViewById<TextView>(R.id.dialog_app_version).text = "vUnknown"
        }

        dialogView.findViewById<TextView>(R.id.dialog_app_package).text = "Package: ${app.packageName}"
        dialogView.findViewById<TextView>(R.id.dialog_app_dir).text = "Dir: ${app.sourceDir}"
        dialogView.findViewById<TextView>(R.id.dialog_app_size).text = "Size: ${app.size / (1024*1024)} MB"

        dialogView.findViewById<View>(R.id.btn_dialog_uninstall).setOnClickListener {
            Thread {
                val success = RenameUtil.executeShizukuCommand("pm uninstall ${app.packageName}")
                requireActivity().runOnUiThread {
                    if (success) {
                        Toast.makeText(context, "Uninstalled ${app.name}", Toast.LENGTH_SHORT).show()
                        loadApps()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, "Failed to uninstall", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        dialogView.findViewById<View>(R.id.btn_dialog_extract).setOnClickListener {
            Toast.makeText(context, "Extracting ${app.name}...", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    inner class AppAdapter(private val list: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.iv_app_icon)
            val tvName: TextView = v.findViewById(R.id.tv_app_name)
            val tvPackage: TextView = v.findViewById(R.id.tv_app_package)
            val tvInfo: TextView = v.findViewById(R.id.tv_app_info)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = list[position]
            holder.ivIcon.setImageDrawable(app.icon)
            holder.tvName.text = app.name
            holder.tvPackage.text = app.packageName
            val minutes = app.usageTimeToday / 60000
            if (minutes > 0) {
                holder.tvInfo.text = "${app.size / (1024*1024)} MB | $minutes min"
            } else {
                holder.tvInfo.text = "${app.size / (1024*1024)} MB"
            }
            holder.itemView.setOnClickListener { onClick(app) }
        }
        override fun getItemCount() = list.size
    }
}

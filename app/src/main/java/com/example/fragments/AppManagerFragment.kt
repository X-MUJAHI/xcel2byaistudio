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
        val installTime: Long
    )

    private val appsList = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_app_manager, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
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
            // Placeholder: sorting by name instead
            appsList.sortBy { it.name }
            adapter.notifyDataSetChanged()
        }

        loadApps()

        return view
    }

    private fun loadApps() {
        Thread {
            val pm = requireContext().packageManager
            val packages = pm.getInstalledPackages(0)
            appsList.clear()
            for (pi in packages) {
                // Ignore our own app or completely system if you want, but user said all installed apps
                try {
                    val appInfo = pi.applicationInfo ?: continue
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val packageName = pi.packageName
                    val sourceDir = appInfo.sourceDir
                    val file = File(sourceDir ?: "")
                    val size = if (file.exists()) file.length() else 0L
                    val installTime = pi.firstInstallTime
                    val icon = pm.getApplicationIcon(appInfo)
                    
                    appsList.add(AppItem(name, packageName ?: "", sourceDir ?: "", size, icon, installTime))
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
        val builder = android.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
        builder.setTitle(app.name)
        builder.setMessage("Package: ${app.packageName}\nDir: ${app.sourceDir}\nSize: ${app.size / (1024*1024)} MB")
        
        builder.setPositiveButton("UNINSTALL") { _, _ ->
            Thread {
                val success = RenameUtil.executeShizukuCommand("pm uninstall ${app.packageName}")
                requireActivity().runOnUiThread {
                    if (success) {
                        Toast.makeText(context, "Uninstalled ${app.name}", Toast.LENGTH_SHORT).show()
                        loadApps()
                    } else {
                        Toast.makeText(context, "Failed to uninstall", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
        builder.setNegativeButton("EXTRACT") { _, _ ->
            // Simulating extraction
            Toast.makeText(context, "Extracting ${app.name}...", Toast.LENGTH_SHORT).show()
        }
        builder.setNeutralButton("CANCEL", null)
        builder.show()
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
            holder.tvInfo.text = "${app.size / (1024*1024)} MB"
            holder.itemView.setOnClickListener { onClick(app) }
        }
        override fun getItemCount() = list.size
    }
}

package com.example.fragments

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

class ProcessManagerFragment : Fragment() {

    data class ProcessItem(
        val pkgName: String,
        val pid: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    private val processList = mutableListOf<ProcessItem>()
    private lateinit var adapter: ProcessAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_process_manager, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_processes)
        adapter = ProcessAdapter(processList) { proc ->
            freezeProcess(proc)
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        loadProcesses()

        return view
    }

    private fun loadProcesses() {
        Thread {
            val psOutput = RenameUtil.executeShizukuCommandWithOutput("ps -A | grep u0_")
            val lines = psOutput.split("\n")
            
            val pm = requireContext().packageManager
            val newProcs = mutableListOf<ProcessItem>()
            val seenPkg = mutableSetOf<String>()

            for (line in lines) {
                if (line.isBlank()) continue
                // USER PID PPID VZ RSS WCHAN ADDR S NAME
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 9) {
                    val pid = parts[1]
                    val pkgName = parts.last().substringBefore(":") // some are com.pkg:service
                    if (!pkgName.contains(".")) continue // Only interested in app packages
                    if (seenPkg.contains(pkgName)) continue // Only add each package once
                    
                    try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        
                        seenPkg.add(pkgName)
                        newProcs.add(ProcessItem(pkgName, pid, appName, icon))
                    } catch (e: Exception) {}
                }
            }

            requireActivity().runOnUiThread {
                processList.clear()
                processList.addAll(newProcs)
                processList.sortBy { it.appName }
                adapter.notifyDataSetChanged()
                Toast.makeText(context, "Loaded ${processList.size} running processes", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun freezeProcess(proc: ProcessItem) {
        Thread {
            val success = RenameUtil.executeShizukuCommand("am force-stop ${proc.pkgName}")
            requireActivity().runOnUiThread {
                if (success) {
                    Toast.makeText(context, "Stopped ${proc.appName}", Toast.LENGTH_SHORT).show()
                    loadProcesses()
                } else {
                    Toast.makeText(context, "Failed to stop", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    inner class ProcessAdapter(private val list: List<ProcessItem>, private val onFreeze: (ProcessItem) -> Unit) : RecyclerView.Adapter<ProcessAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.iv_process_icon)
            val tvName: TextView = v.findViewById(R.id.tv_process_name)
            val tvPid: TextView = v.findViewById(R.id.tv_process_pid)
            val btnFreeze: ImageView = v.findViewById(R.id.btn_freeze)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_process, parent, false))
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = list[position]
            holder.ivIcon.setImageDrawable(p.icon)
            holder.tvName.text = p.appName
            holder.tvPid.text = "PID: ${p.pid} | Pkg: ${p.pkgName}"
            holder.btnFreeze.setOnClickListener { onFreeze(p) }
        }
        override fun getItemCount() = list.size
    }
}

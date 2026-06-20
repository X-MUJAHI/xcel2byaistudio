package com.example.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.R

class DeleteReportsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_delete_reports, container, false)
        
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        view.findViewById<Button>(R.id.btn_delete_reports_action).setOnClickListener {
            showDeleteDialog()
        }
        
        return view
    }

    private fun showDeleteDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_loading, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_message)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()
        
        val handler = Handler(Looper.getMainLooper())
        
        val prefs = requireActivity().getSharedPreferences("reports_prefs", android.content.Context.MODE_PRIVATE)
        val lastTime = prefs.getLong("last_delete_time", 0L)
        val currentTime = System.currentTimeMillis()
        val difference = currentTime - lastTime
        
        tvMessage.text = "Checking..."
        progressBar.progress = 25
        
        if (difference < 5 * 60 * 60 * 1000) { // less than 5 hours
            handler.postDelayed({
                tvMessage.text = "0 reports found"
                progressBar.progress = 100
            }, 1500)
            handler.postDelayed({
                dialog.dismiss()
            }, 3000)
        } else {
            val randomReports = (5..15).random()
            handler.postDelayed({
                tvMessage.text = "Found $randomReports reports ..."
                progressBar.progress = 50
            }, 1000)
            
            handler.postDelayed({
                tvMessage.text = "Deleting ..."
                progressBar.progress = 75
                
                // Show some fake IDs during deletion
                for (i in 0 until randomReports) {
                    handler.postDelayed({
                        if (dialog.isShowing) {
                            val fakeId = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()
                            tvMessage.text = "Deleting [ID: $fakeId]..."
                        }
                    }, i * 200L)
                }
            }, 2000)
            
            handler.postDelayed({
                tvMessage.text = "Reports Deleted ✓"
                progressBar.progress = 100
                prefs.edit().putLong("last_delete_time", currentTime).apply()
            }, 2000 + (randomReports * 200L) + 500)
            
            handler.postDelayed({
                dialog.dismiss()
            }, 2000 + (randomReports * 200L) + 2000)
        }
    }
}

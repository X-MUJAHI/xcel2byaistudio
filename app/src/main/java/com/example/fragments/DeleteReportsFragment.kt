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
        
        tvMessage.text = "Checking..."
        progressBar.progress = 25
        
        handler.postDelayed({
            tvMessage.text = "Found some reports ..."
            progressBar.progress = 50
        }, 1500)
        
        handler.postDelayed({
            tvMessage.text = "Deleting ..."
            progressBar.progress = 75
        }, 2000)
        
        handler.postDelayed({
            tvMessage.text = "Reports Deleted ✓"
            progressBar.progress = 100
        }, 4000)
        
        handler.postDelayed({
            dialog.dismiss()
        }, 5000)
    }
}

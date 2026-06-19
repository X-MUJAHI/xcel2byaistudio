package com.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R

class ProcessManagerFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_process_manager, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        Toast.makeText(context, "Background Task Manager...", Toast.LENGTH_SHORT).show()

        return view
    }
}

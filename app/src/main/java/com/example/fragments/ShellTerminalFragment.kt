package com.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.example.utils.RenameUtil

class ShellTerminalFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_shell_terminal, container, false)
        
        view.findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            (requireActivity() as MainActivity).switchFragment(HomeFragment())
        }

        val etCommands = view.findViewById<EditText>(R.id.et_commands)
        val tvOutput = view.findViewById<TextView>(R.id.tv_output)
        val btnRun = view.findViewById<ImageButton>(R.id.btn_run)
        
        tvOutput.movementMethod = android.text.method.ScrollingMovementMethod()

        btnRun.setOnClickListener {
            val script = etCommands.text.toString()
            if (script.isBlank()) return@setOnClickListener

            Thread {
                val outputResult = RenameUtil.executeShizukuCommandWithOutput(script)
                requireActivity().runOnUiThread {
                    tvOutput.text = outputResult
                }
            }.start()
        }

        return view
    }
}

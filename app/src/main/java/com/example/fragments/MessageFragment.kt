package com.example.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.MainActivity
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

class MessageFragment : Fragment() {

    private lateinit var tvMessages: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    
    private val firestore = FirebaseFirestore.getInstance()
    private var savedKey: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_message, container, false)
        tvMessages = view.findViewById(R.id.tv_messages)
        etMessage = view.findViewById(R.id.et_message)
        btnSend = view.findViewById(R.id.btn_send)

        val prefs = requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, 0)
        savedKey = prefs.getString(MainActivity.KEY_SAVED_KEY, "") ?: ""

        if (savedKey.isNotEmpty()) {
            firestore.collection("users").document(savedKey).update("messageSeen", true)
            listenForMessages()
        } else {
            tvMessages.text = "You need to activate the panel first."
            etMessage.isEnabled = false
            btnSend.isEnabled = false
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        return view
    }

    private fun listenForMessages() {
        firestore.collection("users").document(savedKey).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                return@addSnapshotListener
            }

            val messagesList = snapshot.get("messages") as? List<Map<String, Any>> ?: emptyList()
            var chatText = ""
            for (msg in messagesList) {
                val sender = msg["sender"] as? String ?: "Admin"
                val text = msg["text"] as? String ?: ""
                chatText += "$sender: $text\n\n"
            }
            tvMessages.text = chatText
            
            // Mark as seen whenever a new message flows in while we are on this screen
            firestore.collection("users").document(savedKey).update("messageSeen", true)
        }
    }

    private fun sendMessage(text: String) {
        btnSend.isEnabled = false
        val docRef = firestore.collection("users").document(savedKey)
        docRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val messagesList = snapshot.get("messages") as? MutableList<Map<String, Any>> ?: mutableListOf()
                messagesList.add(mapOf("sender" to "USER", "text" to text))
                
                docRef.update("messages", messagesList).addOnSuccessListener {
                    etMessage.text.clear()
                    btnSend.isEnabled = true
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                    btnSend.isEnabled = true
                }
            } else {
                btnSend.isEnabled = true
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load chat.", Toast.LENGTH_SHORT).show()
            btnSend.isEnabled = true
        }
    }
}

package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.CopyOnWriteArrayList

object FreezeManager {
    private const val TAG = "FreezeManager"

    const val PREF_IS_FROZEN = "is_app_frozen"
    const val PREF_FREEZE_REASON = "freeze_reason"
    const val PREF_WAS_ON_BEFORE_FREEZE = "was_on_before_freeze"
    const val PREF_LAST_KNOWN_FROZEN = "last_known_is_frozen"

    private var listenerRegistration: ListenerRegistration? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    interface FreezeStateListener {
        fun onFreezeStateChanged(isFrozen: Boolean, reason: String)
    }

    private val listeners = CopyOnWriteArrayList<FreezeStateListener>()

    fun registerListener(listener: FreezeStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: FreezeStateListener) {
        listeners.remove(listener)
    }

    fun isCurrentlyFrozen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IS_FROZEN, false)
    }

    fun getFreezeReason(context: Context): String {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_FREEZE_REASON, "Server maintenance in progress") ?: "Server maintenance in progress"
    }

    fun startListening(context: Context) {
        if (listenerRegistration != null) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        try {
            listenerRegistration = FirebaseFirestore.getInstance()
                .collection("app_settings")
                .document("global")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        return@addSnapshotListener
                    }

                    val isFrozen = snapshot.getBoolean("isFrozen") ?: false
                    val reason = snapshot.getString("freezeReason") ?: "Server maintenance in progress"

                    handleFreezeState(appContext, prefs, isFrozen, reason)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching Firestore listener: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            listenerRegistration?.remove()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        listenerRegistration = null
    }

    @Synchronized
    fun handleFreezeState(
        context: Context,
        prefs: SharedPreferences,
        isFrozen: Boolean,
        reason: String
    ) {
        val previouslyFrozen = prefs.getBoolean(PREF_LAST_KNOWN_FROZEN, false)
        prefs.edit()
            .putBoolean(PREF_IS_FROZEN, isFrozen)
            .putString(PREF_FREEZE_REASON, reason)
            .apply()

        val optDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"

        if (isFrozen) {
            if (!previouslyFrozen) {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, true).apply()

                Thread {
                    val aDataExists = RenameUtil.checkDirExists("$optDir/android-data")
                    val aMujahiExists = RenameUtil.checkDirExists("$optDir/android-mujahi")
                    val currentlyOn = aDataExists && !aMujahiExists

                    if (currentlyOn) {
                        Log.d(TAG, "Transition to FROZEN: Device is ON. Saving state as true and turning OFF...")
                        prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, true).apply()
                        RenameUtil.turnOff()
                    } else {
                        Log.d(TAG, "Transition to FROZEN: Device is OFF. Saving state as false.")
                        prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, false).apply()
                    }

                    notifyListeners(true, reason)
                }.start()
            } else {
                notifyListeners(true, reason)
            }
        } else {
            // Unfrozen
            if (previouslyFrozen) {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, false).apply()

                Thread {
                    val wasOn = prefs.getBoolean(PREF_WAS_ON_BEFORE_FREEZE, false)
                    prefs.edit().putBoolean(PREF_WAS_ON_BEFORE_FREEZE, false).apply()

                    if (wasOn) {
                        Log.d(TAG, "Transition to UNFROZEN: Device was ON before freeze. Restoring to ON...")
                        RenameUtil.turnOn()
                    } else {
                        Log.d(TAG, "Transition to UNFROZEN: Device was OFF before freeze. Leaving OFF.")
                    }

                    notifyListeners(false, reason)
                }.start()
            } else {
                prefs.edit().putBoolean(PREF_LAST_KNOWN_FROZEN, false).apply()
                notifyListeners(false, reason)
            }
        }
    }

    private fun notifyListeners(isFrozen: Boolean, reason: String) {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener.onFreezeStateChanged(isFrozen, reason)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

package com.example.vocaleyesnew.facenet_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Intent
import org.koin.android.ext.android.inject
import android.util.Log

/**
 * Main entry point for face recognition feature.
 * This activity serves as a bridge to the FaceMainActivity implementation.
 */
class FaceActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Start the actual implementation activity
            startActivity(Intent(this, FaceMainActivity::class.java))
            // Finish this activity so the back stack is clean
            finish()
        } catch (e: Exception) {
            Log.e("FaceActivity", "Error starting FaceMainActivity: ${e.message}")
            // If there's an error, finish the activity
            finish()
        }
    }
} 
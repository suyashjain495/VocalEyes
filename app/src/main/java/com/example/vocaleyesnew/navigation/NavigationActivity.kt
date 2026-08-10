package com.example.vocaleyesnew.navigation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.vocaleyesnew.accessibility.AppCommand
import com.example.vocaleyesnew.accessibility.BaseAccessibleActivity
import com.example.vocaleyesnew.accessibility.UserGuidanceManager
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.facenet_android.FaceMainActivity
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import com.example.vocaleyesnew.EnhancedMainActivity
import com.example.vocaleyesnew.ui.theme.VocalEyesNewTheme

class NavigationActivity : BaseAccessibleActivity() {
    
    override fun onAccessibilityReady() {
        setContent {
            VocalEyesNewTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlindModeScreen(
                        activity = this@NavigationActivity
                    )
                }
            }
        }
    }
    
    override fun onTtsReady() {
        val welcomeMessage = guidanceManager.getWelcomeMessage(
            UserGuidanceManager.FEATURE_NAVIGATION,
            "Navigation"
        )
        
        if (guidanceManager.shouldShowGuidance(UserGuidanceManager.FEATURE_NAVIGATION)) {
            speak(welcomeMessage)
            guidanceManager.markGuidanceShown(UserGuidanceManager.FEATURE_NAVIGATION)
        } else {
            speak("Navigation ready. Single tap for voice commands.")
        }
    }
    
    override fun onVoiceCommand(command: String) {
        when (command.lowercase().trim()) {
            "status", "progress" -> {
                speak("I am actively processing your surroundings and will describe what I see.")
            }
            else -> {
                speak("Say the name of a feature to navigate to, like: object detection, face recognition, text reading, or AI assistant. Or say 'home' to return to main menu.")
            }
        }
    }
    
    override fun parseScreenSpecificCommand(command: String): AppCommand? {
        return when (command.lowercase().trim()) {
            "status", "progress" -> AppCommand.ScreenSpecific("status")
            else -> null
        }
    }
    
    override fun getScreenSpecificHelp(): String {
        return "Navigation commands: status, or navigate to other features"
    }
    
    override fun handleDoubleTapEvent(x: Float, y: Float) {
        // Double tap on background should do nothing (like home page)
        // Only UI buttons should respond to double taps
    }
} 
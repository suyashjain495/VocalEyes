package com.example.vocaleyesnew.facenet_android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.vocaleyesnew.accessibility.singleTapOnlyBackgroundHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vocaleyesnew.accessibility.AppCommand
import com.example.vocaleyesnew.accessibility.BaseAccessibleActivity
import com.example.vocaleyesnew.accessibility.UserGuidanceManager
import com.example.vocaleyesnew.facenet_android.presentation.screens.add_face.AddFaceScreen
import com.example.vocaleyesnew.facenet_android.presentation.screens.detect_screen.DetectScreen
import com.example.vocaleyesnew.facenet_android.presentation.screens.detect_screen.flipCamera
import com.example.vocaleyesnew.facenet_android.presentation.screens.face_list.FaceListScreen
import com.example.vocaleyesnew.ui.theme.VocalEyesNewTheme
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.EnhancedMainActivity

class FaceMainActivity : BaseAccessibleActivity() {
    
    private val navActions = mutableMapOf<String, () -> Unit>()
    private var currentRoute by mutableStateOf("detect")
    
    override fun onAccessibilityReady() {
        enableEdgeToEdge()
        
        // Set up UI
        setContent {
            VocalEyesNewTheme {
                Content()
            }
        }
    }
    
    override fun onTtsReady() {
        val welcomeMessage = guidanceManager.getWelcomeMessage(
            UserGuidanceManager.FEATURE_FACE_RECOGNITION,
            "Face Recognition"
        )
        
        if (guidanceManager.shouldShowGuidance(UserGuidanceManager.FEATURE_FACE_RECOGNITION)) {
            speak(welcomeMessage)
            guidanceManager.markGuidanceShown(UserGuidanceManager.FEATURE_FACE_RECOGNITION)
        } else {
            speak("Face recognition ready. Single tap for voice commands.")
        }
    }
    
    @Composable
    fun Content() {
        val navHostController = rememberNavController()
        
        // Get current route for action handling
        val currentBackStackEntry by navHostController.currentBackStackEntryAsState()
        currentRoute = currentBackStackEntry?.destination?.route ?: "detect"
        
        // Set up navigation actions
        navActions["add-face"] = { navHostController.navigate("add-face") }
        navActions["face-list"] = { navHostController.navigate("face-list") }
        navActions["detect"] = { navHostController.navigate("detect") }
        navActions["back"] = { navHostController.navigateUp() }
        
        // Direct navigation functions
        val navigateToFaceList = {
            navHostController.navigate("face-list")
        }
        
        val navigateToAddFace = {
            navHostController.navigate("add-face")
        }
        
        // Box with unified tap handling system (single tap only like home page)
        Box(modifier = Modifier
            .fillMaxSize()
            .singleTapOnlyBackgroundHandler(this@FaceMainActivity)
        ) {
            NavHost(
                navController = navHostController,
                startDestination = "detect",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() }
            ) {
                composable("add-face") { 
                    AddFaceScreen(
                        onNavigateBack = { navHostController.navigateUp() }
                    ) 
                }
                composable("detect") { 
                    DetectScreen(
                        onOpenFaceListClick = navigateToFaceList
                    ) 
                }
                composable("face-list") {
                    FaceListScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onAddFaceClick = navigateToAddFace
                    )
                }
            }
        }
    }
    
    override fun onVoiceCommand(command: String) {
        when (command.lowercase().trim()) {
            // Face recognition specific commands
            "add face", "new face", "register face", "create face" -> {
                speak("Opening add face screen") {
                    navActions["add-face"]?.invoke()
                }
            }
            "show faces", "list faces", "view faces", "show all", "face list" -> {
                speak("Opening face list") {
                    navActions["face-list"]?.invoke()
                }
            }
            "detect", "detection", "scan face", "recognize", "start detection" -> {
                speak("Opening face detection") {
                    navActions["detect"]?.invoke()
                }
            }
            "flip", "switch", "camera", "rotate", "flip camera", "switch camera" -> {
                speak("Flipping camera") {
                    flipCamera()
                }
            }
            
            // Navigation commands to other features
            "object detection", "objects", "detect objects", "object recognition" -> {
                navigateToObjectDetection()
            }
            "text reading", "read text", "text extraction", "ocr", "book reading" -> {
                navigateToTextReading()
            }
            "chat", "ai chat", "assistant", "talk" -> {
                navigateToChat()
            }
            "home", "main menu", "menu" -> {
                navigateToHome()
            }
            "back", "return", "previous" -> {
                navActions["back"]?.invoke() ?: finish()
            }
            
            else -> {
                // Provide comprehensive help
                val helpMessage = buildString {
                    append("Available commands: ")
                    append("add face, show faces, detect faces, flip camera, ")
                    append("object detection, text reading, chat, home, or back.")
                }
                speak(helpMessage)
            }
        }
    }
    
    override fun parseScreenSpecificCommand(command: String): AppCommand? {
        return when (command.lowercase().trim()) {
            "add face", "new face", "register face" -> AppCommand.ScreenSpecific("add-face")
            "show faces", "list faces", "view faces" -> AppCommand.ScreenSpecific("face-list")
            "detect", "detection", "scan face" -> AppCommand.ScreenSpecific("detect")
            "flip", "switch", "camera", "rotate" -> AppCommand.ScreenSpecific("flip")
            else -> null
        }
    }
    
    override fun getScreenSpecificHelp(): String {
        return "Face recognition commands: add face, show faces, detect faces, flip camera"
    }
    
    override fun handleDoubleTapEvent(x: Float, y: Float) {
        // Double tap on background should do nothing (like home page)
        // Only UI buttons should respond to double taps
    }
    
    // Navigation methods to other features
    private fun navigateToObjectDetection() {
        speak("Opening object detection") {
            val intent = Intent(this, ObjectDetectionActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToTextReading() {
        speak("Opening text reading") {
            val intent = Intent(this, TextExtractionActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToChat() {
        speak("Opening AI chat") {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToHome() {
        speak("Going to main menu") {
            val intent = Intent(this, EnhancedMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}

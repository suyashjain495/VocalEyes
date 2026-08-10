package com.example.vocaleyesnew.accessibility

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vocaleyesnew.VoiceRecognitionManager
import com.example.vocaleyesnew.EnhancedMainActivity
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.facenet_android.FaceActivity
import com.example.vocaleyesnew.navigation.NavigationActivity
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * Sealed class representing all app commands for type-safe dispatch
 */
sealed class AppCommand {
    // Navigation commands
    object Home : AppCommand()
    object Back : AppCommand()
    object Help : AppCommand()
    
    // Feature navigation
    object OpenObjectDetection : AppCommand()
    object OpenNavigation : AppCommand()
    object OpenFaceRecognition : AppCommand()
    object OpenTextReading : AppCommand()
    object OpenAssistant : AppCommand()
    
    // Screen-specific commands (to be handled by child activities)
    data class ScreenSpecific(val command: String) : AppCommand()
    
    // Unknown command
    data class Unknown(val command: String) : AppCommand()
}

/**
 * Abstract base activity providing unified accessibility features for blind users
 * 
 * Features:
 * - Single-tap anywhere → Voice recognition (via CentralTapHandler with 1.5s timeout)
 * - Double-tap UI elements → Button activation
 * - Unified voice command parsing
 * - Shared TTS and voice recognition instances
 * - Proper lifecycle management
 * - Cross-screen navigation commands
 */
abstract class BaseAccessibleActivity : ComponentActivity(), TextToSpeech.OnInitListener, TapListener {
    
    companion object {
        private const val TAG = "BaseAccessibleActivity"
        private const val MICROPHONE_PERMISSION_REQUEST_CODE = 100
        private const val VOICE_LISTENING_TIMEOUT_MS = 7000L
    }

    // Shared accessibility instances
    protected lateinit var voiceRecognitionManager: VoiceRecognitionManager
    protected lateinit var textToSpeech: TextToSpeech
    protected lateinit var accessibilityUtils: AccessibilityUtils
    protected lateinit var guidanceManager: UserGuidanceManager
    
    // Coroutine scope for managing async operations
    protected val accessibilityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var voiceTimeoutJob: Job? = null
    
    // State management
    private var isTtsReady = false
    private var isVoiceListening = false
    private var isOpenedViaDoubleTap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "Initializing accessibility features for ${this::class.simpleName}")
        
        initializeAccessibilityFeatures()
        checkMicrophonePermission()
        
        // Call the abstract method for child-specific setup
        onAccessibilityReady()
    }

    /**
     * Initialize all accessibility components
     */
    private fun initializeAccessibilityFeatures() {
        // Initialize guidance manager
        guidanceManager = UserGuidanceManager.getInstance(this)
        
        // Initialize TTS
        textToSpeech = TextToSpeech(this, this)
        
        // Initialize voice recognition on UI thread (SpeechRecognizer requirement)
        runOnUiThread {
            voiceRecognitionManager = VoiceRecognitionManager(this)
            setupVoiceRecognition()
        }
        
        // No gesture detector needed - using CentralTapHandler instead
        
        // Check if opened via double tap (from intent extras)
        isOpenedViaDoubleTap = intent.getBooleanExtra("opened_via_double_tap", false)
        
        Log.d(TAG, "Accessibility features initialized")
    }

    /**
     * Setup voice recognition with unified command handling
     */
    private fun setupVoiceRecognition() {
        voiceRecognitionManager.setCommandListener { command ->
            isVoiceListening = false
            voiceTimeoutJob?.cancel()
            
            val parsedCommand = parseCommand(command)
            handleCommand(parsedCommand)
        }
    }

    // TapListener implementation for CentralTapHandler
    override fun onSingleTap(x: Float, y: Float) {
        Log.d(TAG, "Single tap received at ($x, $y) - triggering voice recognition")
        if (!isVoiceListening && isTtsReady) {
            startVoiceListening()
        }
    }

    override fun onDoubleTap(x: Float, y: Float) {
        Log.d(TAG, "Double tap received at ($x, $y) - forwarding to child activity")
        // Mark that this interaction was via double-tap
        isOpenedViaDoubleTap = true
        
        // Call abstract method for child to handle double-tap on UI elements
        handleDoubleTapEvent(x, y)
    }

    /**
     * Start voice listening with timeout
     */
    private fun startVoiceListening() {
        if (!isVoiceListening) {
            isVoiceListening = true
            
            voiceRecognitionManager.startListening()
            
            // Set timeout to stop listening
            voiceTimeoutJob = accessibilityScope.launch {
                delay(VOICE_LISTENING_TIMEOUT_MS)
                if (isVoiceListening) {
                    isVoiceListening = false
                    voiceRecognitionManager.stopListening()
                    speak("Voice listening timed out. Tap to try again.")
                }
            }
            
            onVoiceListeningStarted()
        }
    }

    /**
     * Parse voice command into typed command object
     */
    private fun parseCommand(command: String): AppCommand {
        val cmd = command.lowercase().trim()
        
        return when {
            // Home/Back commands
            cmd.contains("home") || cmd.contains("main") || cmd.contains("start") -> AppCommand.Home
            cmd.contains("back") || cmd.contains("return") || cmd.contains("exit") -> AppCommand.Back
            
            // Help
            cmd.contains("help") || cmd.contains("what can you do") || cmd.contains("commands") || cmd == "menu" -> AppCommand.Help
            
            // Navigation to other features
            (cmd.contains("object") && (cmd.contains("detect") || cmd.contains("recognition") || cmd.contains("identify"))) || cmd == "objects" -> AppCommand.OpenObjectDetection
            
            cmd.contains("navigation") || cmd.contains("navigate") || cmd.contains("direction") || cmd.contains("map") || cmd == "go" -> AppCommand.OpenNavigation
            
            ((cmd.contains("face") || cmd.contains("person") || cmd.contains("people")) && (cmd.contains("detect") || cmd.contains("recognition") || cmd.contains("identify"))) || cmd == "faces" -> AppCommand.OpenFaceRecognition
            
            ((cmd.contains("text") || cmd.contains("book") || cmd.contains("read")) && (cmd.contains("extract") || cmd.contains("recognition") || cmd.contains("reading"))) || cmd == "read" || cmd == "reading" -> AppCommand.OpenTextReading
            
            cmd.contains("assistant") || cmd.contains("chat") || cmd.contains("help") || cmd.contains("talk") || cmd == "ai" -> AppCommand.OpenAssistant
            
            else -> {
                // Let child activities handle screen-specific commands
                val childResult = parseScreenSpecificCommand(cmd)
                childResult ?: AppCommand.Unknown(cmd)
            }
        }
    }

    /**
     * Handle parsed commands
     */
    private fun handleCommand(command: AppCommand) {
        when (command) {
            AppCommand.Home -> navigateToHome()
            AppCommand.Back -> navigateBack()
            AppCommand.Help -> provideHelp()
            AppCommand.OpenObjectDetection -> navigateToObjectDetection()
            AppCommand.OpenNavigation -> navigateToNavigation()
            AppCommand.OpenFaceRecognition -> navigateToFaceRecognition()
            AppCommand.OpenTextReading -> navigateToTextReading()
            AppCommand.OpenAssistant -> navigateToAssistant()
            is AppCommand.ScreenSpecific -> onVoiceCommand(command.command)
            is AppCommand.Unknown -> handleUnknownCommand(command.command)
        }
    }

    /**
     * Navigation methods
     */
    private fun navigateToHome() {
        speak("Going to home screen") {
            val intent = Intent(this, EnhancedMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun navigateBack() {
        speak("Going back") {
            finish()
        }
    }

    private fun navigateToObjectDetection() {
        speak("Opening object detection") {
            startActivity(Intent(this, ObjectDetectionActivity::class.java))
            finish()
        }
    }

    private fun navigateToNavigation() {
        speak("Opening navigation") {
            startActivity(Intent(this, NavigationActivity::class.java))
            finish()
        }
    }

    private fun navigateToFaceRecognition() {
        speak("Opening face recognition") {
            startActivity(Intent(this, FaceActivity::class.java))
            finish()
        }
    }

    private fun navigateToTextReading() {
        speak("Opening text reading") {
            startActivity(Intent(this, TextExtractionActivity::class.java))
            finish()
        }
    }

    private fun navigateToAssistant() {
        speak("Opening AI assistant") {
            startActivity(Intent(this, ChatActivity::class.java))
            finish()
        }
    }

    private fun provideHelp() {
        val helpMessage = buildString {
            append("Available commands: ")
            append("Home, Back, Help, ")
            append("Open object detection, Open navigation, ")
            append("Open face recognition, Open text reading, Open assistant")
            
            // Add screen-specific help
            val screenHelp = getScreenSpecificHelp()
            if (screenHelp.isNotEmpty()) {
                append(". ")
                append(screenHelp)
            }
        }
        
        speak(helpMessage)
    }

    private fun handleUnknownCommand(command: String) {
        speak("Sorry, I didn't understand '$command'. Say 'help' for available commands, or try rephrasing.")
    }

    /**
     * Check microphone permission
     */
    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val message = guidanceManager.getHelpMessage(
                    UserGuidanceManager.VOICE_COMMANDS_INTRO,
                    "Microphone permission granted. Voice commands are now available."
                )
                message?.let { speak(it) }
                
                if (message != null) {
                    guidanceManager.markGuidanceShown(UserGuidanceManager.VOICE_COMMANDS_INTRO)
                }
            } else {
                speak("Microphone permission is required for voice commands. You can still use double-tap gestures.")
            }
        }
    }

    /**
     * TTS initialization
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            
            if (isTtsReady) {
                textToSpeech.setSpeechRate(0.8f)
                accessibilityUtils = AccessibilityUtils(this, textToSpeech)
                
                // Announce screen ready
                onTtsReady()
            } else {
                Log.e(TAG, "TTS language not supported")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    /**
     * Utility method to speak text
     */
    protected fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (isTtsReady) {
            accessibilityUtils.speakInstruction(text, onComplete)
        } else {
            onComplete?.invoke()
        }
    }

    /**
     * Utility method to speak content (for longer text)
     */
    protected fun speakContent(content: String, onComplete: (() -> Unit)? = null) {
        if (isTtsReady) {
            accessibilityUtils.speakContent(content, onComplete)
        } else {
            onComplete?.invoke()
        }
    }

    /**
     * Stop speaking
     */
    protected fun stopSpeaking() {
        if (::accessibilityUtils.isInitialized) {
            accessibilityUtils.stopSpeaking()
        }
    }

    /**
     * Intercept touch events and forward to CentralTapHandler
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Forward tap to CentralTapHandler with 1.5s timeout
            CentralTapHandler.registerTap(event.x, event.y, this)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (isVoiceListening) {
            voiceRecognitionManager.stopListening()
            isVoiceListening = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup voice recognition
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.cleanup()
        }
        
        // Cleanup TTS
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        
        // Cancel all coroutines
        accessibilityScope.cancel()
        
        Log.d(TAG, "Accessibility cleanup completed")
    }

    // Abstract methods for child activities to implement

    /**
     * Called when accessibility features are ready
     */
    protected abstract fun onAccessibilityReady()

    /**
     * Called when TTS is initialized and ready
     */
    protected abstract fun onTtsReady()

    /**
     * Handle screen-specific voice commands
     */
    abstract fun onVoiceCommand(command: String)

    /**
     * Parse screen-specific commands (return null if not handled)
     */
    protected abstract fun parseScreenSpecificCommand(command: String): AppCommand?

    /**
     * Get help text for screen-specific commands
     */
    protected abstract fun getScreenSpecificHelp(): String

    /**
     * Handle double-tap at specific coordinates
     */
    protected abstract fun handleDoubleTapEvent(x: Float, y: Float)

    /**
     * Called when voice listening starts (for UI feedback)
     */
    protected open fun onVoiceListeningStarted() {
        // Default implementation - child can override for UI feedback
    }

    /**
     * Check if TTS is ready
     */
    protected fun isTtsReady(): Boolean = isTtsReady

    /**
     * Check if currently listening for voice
     */
    protected fun isListening(): Boolean = isVoiceListening

    /**
     * Public method for Compose screens to trigger voice recognition
     * This is needed because Compose consumes touch events before they reach onTouchEvent
     */
    fun handleSingleTap() {
        if (!isVoiceListening && isTtsReady) {
            startVoiceListening()
        }
    }

    /**
     * Public method for Compose screens to check if voice is currently listening
     * Used for UI state management
     */
    fun isVoiceListeningForCompose(): Boolean = isVoiceListening
}

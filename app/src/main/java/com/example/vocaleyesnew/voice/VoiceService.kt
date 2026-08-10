package com.example.vocaleyesnew.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified Voice Service for consistent voice recognition and TTS across all activities.
 * This service ensures:
 * - Single instance of SpeechRecognizer and TTS to avoid conflicts
 * - Consistent single-tap activation behavior
 * - Proper resource management and lifecycle handling
 * - Feature-specific command routing
 */
class VoiceService private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: VoiceService? = null
        
        fun getInstance(context: Context): VoiceService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // TTS and Speech Recognition components
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // State management
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText
    
    // Command routing
    private val commandListeners = ConcurrentHashMap<String, (VoiceCommand) -> Unit>()
    private var activeFeatureId: String? = null
    private val commandRouter = VoiceCommandRouter(context)
    
    // Coroutine scope for internal operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // Configuration
    private var isInitialized = false
    private val voiceTimeout = 7000L // 7 seconds
    
    init {
        initializeComponents()
    }
    
    private fun initializeComponents() {
        initializeTextToSpeech()
        initializeSpeechRecognizer()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.apply {
                    language = Locale.US
                    setSpeechRate(0.9f)
                    setPitch(1.0f)
                    
                    // Configure audio attributes for better audio focus handling
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .build()
                        setAudioAttributes(audioAttributes)
                    }
                }
                isInitialized = true
                Log.d("VoiceService", "TTS initialized successfully")
            } else {
                Log.e("VoiceService", "TTS initialization failed")
                // Try to reinitialize after a delay if failed
                scope.launch {
                    delay(1000)
                    if (textToSpeech == null || !isInitialized) {
                        Log.d("VoiceService", "Attempting to reinitialize TTS")
                        initializeTextToSpeech()
                    }
                }
            }
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { initializeSpeechRecognizer() }
            return
        }
        
        // Clean up any existing recognizer first
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceService", "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d("VoiceService", "Speech started")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Optional: Use for visual feedback
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d("VoiceService", "Speech ended")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                        else -> "Unknown error"
                    }
                    Log.e("VoiceService", "Speech recognition error: $errorMessage (code: $error)")
                    
                    // Provide user feedback for certain errors
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            speak("I didn't hear anything. Please tap and try again.")
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Reinitialize the recognizer and try again
                            scope.launch {
                                delay(300)
                                if (_isListening.value) {
                                    // Recreate the speech recognizer
                                    initializeSpeechRecognizer()
                                    delay(200)
                                    startListening()
                                }
                            }
                        }
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_CLIENT -> {
                            // For serious errors, try to reinitialize the recognizer
                            scope.launch {
                                delay(500)
                                initializeSpeechRecognizer()
                            }
                        }
                    }
                    
                    _isListening.value = false
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    if (!matches.isNullOrEmpty()) {
                        // Get the best match (highest confidence or first result)
                        val recognizedText = matches[0]
                        val confidence = confidenceScores?.getOrNull(0) ?: 0f
                        
                        _recognizedText.value = recognizedText
                        Log.d("VoiceService", "Recognized: $recognizedText (confidence: $confidence)")
                        
                        // Log alternatives for debugging
                        if (matches.size > 1) {
                            for (i in 1 until matches.size) {
                                val altConfidence = confidenceScores?.getOrNull(i) ?: 0f
                                Log.d("VoiceService", "Alternative $i: ${matches[i]} (confidence: $altConfidence)")
                            }
                        }
                        
                        // Parse command using the router
                        val command = commandRouter.parseCommand(recognizedText, activeFeatureId)
                        Log.d("VoiceService", "Parsed command: $command")
                        
                        // Try to route global commands first
                        if (commandRouter.routeGlobalCommand(command)) {
                            Log.d("VoiceService", "Routed as global command")
                        } else {
                            // Route to active feature
                            activeFeatureId?.let { featureId ->
                                commandListeners[featureId]?.invoke(command)
                            } ?: run {
                                // No active feature, provide general help
                                when (command) {
                                    is VoiceCommand.Help -> {
                                        speak(commandRouter.getGlobalHelpText())
                                    }
                                    is VoiceCommand.Unknown -> {
                                        // If confidence is very low, ask for clarification
                                        if (confidence < 0.3f) {
                                            speak("I'm not sure what you said. Could you please repeat?")
                                        } else {
                                            speak("I didn't understand that. ${commandRouter.getGlobalHelpText()}")
                                        }
                                    }
                                    else -> {
                                        speak("No active feature. Say 'help' for available commands.")
                                    }
                                }
                            }
                        }
                    } else {
                        speak("I didn't catch that, please try again")
                    }
                    
                    _isListening.value = false
                }
                
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
                Log.d("VoiceService", "Speech recognizer initialized")
            } catch (e: Exception) {
                Log.e("VoiceService", "Error initializing speech recognizer", e)
                // Try to recover
                scope.launch {
                    delay(1000)
                    initializeSpeechRecognizer()
                }
            }
        } else {
            Log.e("VoiceService", "Speech recognition not available")
            // Notify user that speech recognition is not available
            speak("Speech recognition is not available on this device.")
        }
    }
    
    /**
     * Register a command listener for a specific feature
     */
    fun registerFeature(featureId: String, commandListener: (VoiceCommand) -> Unit) {
        commandListeners[featureId] = commandListener
        Log.d("VoiceService", "Registered feature: $featureId")
    }
    
    /**
     * Unregister a feature's command listener
     */
    fun unregisterFeature(featureId: String) {
        commandListeners.remove(featureId)
        if (activeFeatureId == featureId) {
            activeFeatureId = null
        }
        Log.d("VoiceService", "Unregistered feature: $featureId")
    }
    
    /**
     * Set the currently active feature for voice command routing
     */
    fun setActiveFeature(featureId: String) {
        activeFeatureId = featureId
        Log.d("VoiceService", "Active feature set to: $featureId")
    }
    
    /**
     * Start voice recognition with automatic timeout
     */
    @MainThread
    fun startListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }
        
        if (_isListening.value) {
            Log.w("VoiceService", "Already listening, stopping first")
            stopListening()
            // Small delay to ensure clean restart
            mainHandler.postDelayed({ startListening() }, 200)
            return
        }
        
        if (!isInitialized) {
            Log.w("VoiceService", "Not initialized, initializing components first")
            initializeComponents()
            // Try again after initialization
            mainHandler.postDelayed({ startListening() }, 500)
            return
        }
        
        // Request audio focus before starting recognition
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w("VoiceService", "Could not get audio focus, proceeding anyway")
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, 
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            
            // Auto-stop after timeout
            scope.launch {
                delay(voiceTimeout)
                if (_isListening.value) {
                    stopListening()
                }
            }
            
            Log.d("VoiceService", "Started listening with ${voiceTimeout}ms timeout")
        } catch (e: Exception) {
            Log.e("VoiceService", "Error starting speech recognition", e)
            speak("Error starting voice recognition")
            _isListening.value = false
        }
    }
    
    /**
     * Stop voice recognition
     */
    @MainThread
    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }
        
        _isListening.value = false
        speechRecognizer?.stopListening()
        Log.d("VoiceService", "Stopped listening")
    }
    
    /**
     * Speak text with optional completion callback
     */
    fun speak(text: String, tag: String = "default", onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onComplete?.invoke()
            return
        }
        
        // Stop listening if active
        if (_isListening.value) {
            stopListening()
        }
        
        // Request audio focus before speaking
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, 
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        
        val utteranceId = "${tag}_${System.currentTimeMillis()}"
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                Log.d("VoiceService", "Started speaking: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                Log.d("VoiceService", "Finished speaking: $utteranceId")
                onComplete?.invoke()
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceService", "TTS error: $errorCode for utterance: $utteranceId")
                _isSpeaking.value = false
                onComplete?.invoke()
            }
            
            // For backward compatibility
            override fun onError(utteranceId: String?) {
                Log.e("VoiceService", "TTS error for utterance: $utteranceId")
                _isSpeaking.value = false
                onComplete?.invoke()
            }
        })
        
        // Use HashMap for parameters for better compatibility
        val params = HashMap<String, String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }
        
        Log.d("VoiceService", "Speaking: ${text.take(50)}...")
    }
    
    /**
     * Stop current speech
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        Log.d("VoiceService", "Stopped speaking")
    }
    
    /**
     * Handle single tap activation - the main interface for all activities
     */
    fun handleSingleTap() {
        if (_isListening.value) {
            Log.d("VoiceService", "Already listening, ignoring tap")
            return
        }
        
        if (_isSpeaking.value) {
            Log.d("VoiceService", "Currently speaking, stopping speech first")
            stopSpeaking()
            return
        }
        
        startListening()
    }
    
    /**
     * Handle double tap - typically used to cancel current operation
     */
    fun handleDoubleTap() {
        if (_isListening.value) {
            Log.d("VoiceService", "Double tap - stopping listening")
            stopListening()
            return
        }
        
        if (_isSpeaking.value) {
            Log.d("VoiceService", "Double tap - stopping speech")
            stopSpeaking()
            return
        }
        
        // If neither listening nor speaking, toggle listening
        startListening()
    }
    
    /**
     * Check if the service is ready to use
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Get the context (for activities that need it)
     */
    fun getContext(): Context = context
    
    /**
     * Cleanup resources - should be called when the app is destroyed
     */
    @MainThread
    fun cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cleanup() }
            return
        }
        
        try {
            // Cancel any pending operations
            mainHandler.removeCallbacksAndMessages(null)
            
            // Stop active processes
            stopListening()
            stopSpeaking()
            
            // Release audio focus if needed
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            
            // Clean up resources
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("VoiceService", "Error destroying speech recognizer", e)
            } finally {
                speechRecognizer = null
            }
            
            try {
                textToSpeech?.shutdown()
            } catch (e: Exception) {
                Log.e("VoiceService", "Error shutting down TTS", e)
            } finally {
                textToSpeech = null
            }
            
            // Clear data structures
            commandListeners.clear()
            activeFeatureId = null
            
            // Cancel coroutines
            scope.cancel()
            
            isInitialized = false
            Log.d("VoiceService", "Cleanup completed")
        } catch (e: Exception) {
            Log.e("VoiceService", "Error during cleanup", e)
        }
    }
}

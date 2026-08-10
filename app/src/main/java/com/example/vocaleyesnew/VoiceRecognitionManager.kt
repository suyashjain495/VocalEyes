package com.example.vocaleyesnew

import android.content.Context
import android.content.Intent
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

class VoiceRecognitionManager(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private var onCommandRecognized: ((String) -> Unit)? = null

    init {
        initializeTextToSpeech()
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        // Ensure initialization happens on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { initializeSpeechRecognizer() }
            return
        }
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("VoiceRecognition", "Error destroying previous recognizer: ${e.message}")
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceRecognition", "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("VoiceRecognition", "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Optional: Use for visual feedback of voice level
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d("VoiceRecognition", "Speech ended")
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
                    Log.e("VoiceRecognition", "Error: $errorMessage (code: $error)")
                    
                    // For no-match and speech timeout errors, give user feedback to try again
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        speak("I didn't hear anything. Please tap and try again.")
                    } 
                    // For busy errors, try again after a short delay
                    else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        scope.launch {
                            delay(500)
                            if (isListening) {
                                startListening()
                            }
                        }
                    }
                    // For other errors, just log them but don't bother the user
                    
                    // Mark as not listening anymore
                    isListening = false
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0].lowercase()
                        _recognizedText.value = recognizedText
                        Log.d("VoiceRecognition", "Recognized: $recognizedText (Alternatives: ${matches.size > 1})")
                        
                        // Log alternatives for debugging
                        if (matches.size > 1) {
                            for (i in 1 until matches.size) {
                                Log.d("VoiceRecognition", "Alternative $i: ${matches[i]}")
                            }
                        }
                        
                        // Call the command listener with the best match
                        onCommandRecognized?.invoke(recognizedText)
                    } else {
                        Log.d("VoiceRecognition", "No speech recognition results")
                        speak("I didn't catch that, please try again")
                    }
                    
                    // After processing the results, stop the current recognition session
                    // Don't restart listening automatically - this will be done by the tap gesture
                    isListening = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Optional: Handle partial results if needed
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speak("Voice recognition ready")
        } else {
            Log.e("VoiceRecognition", "Speech recognition not available")
            speak("Speech recognition not available on this device")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setSpeechRate(0.9f) // Slightly faster for better user experience
                textToSpeech?.setPitch(1.0f)
                
                // Set audio attributes for better audio focus handling
                textToSpeech?.setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                
                Log.d("VoiceRecognition", "TextToSpeech initialized successfully")
            } else {
                Log.e("VoiceRecognition", "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    @MainThread
    fun startListening() {
        // Ensure we're on the main thread for SpeechRecognizer operations
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }
        
        if (!isListening) {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Increased for better accuracy
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L) // Reduced for faster response
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L) // Increased for better command detection
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // Prefer offline for better reliability
            }
            
            // Cancel any previous recognition session
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("VoiceRecognition", "Error canceling previous recognition: ${e.message}")
            }
            
            try {
                speechRecognizer?.startListening(intent)
                Log.d("VoiceRecognition", "Started listening on main thread")
                
                // Auto-timeout to prevent indefinite listening
                scope.launch {
                    delay(7000) // 7 seconds timeout
                    if (isListening) {
                        stopListening()
                        Log.d("VoiceRecognition", "Auto-stopped listening after timeout")
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceRecognition", "Error starting speech recognition: ${e.message}")
                speak("Error starting voice recognition")
                isListening = false
            }
        }
    }

    @MainThread
    fun stopListening() {
        // Ensure we're on the main thread for SpeechRecognizer operations
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }
        
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        val utteranceId = "utterance_${System.currentTimeMillis()}"
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("VoiceRecognition", "Started speaking: ${text.take(30)}...")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d("VoiceRecognition", "Finished speaking")
                mainHandler.post {
                    onComplete?.invoke()
                }
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceRecognition", "Error speaking: $errorCode")
                // Still call onComplete even if there's an error
                mainHandler.post {
                    onComplete?.invoke()
                }
            }
            
            // For compatibility with older Android versions
            override fun onError(utteranceId: String?) {
                Log.e("VoiceRecognition", "Error speaking")
                mainHandler.post {
                    onComplete?.invoke()
                }
            }
        })

        // If text is empty, immediately call onComplete without speaking
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        // Use a Bundle for parameters as required by TextToSpeech
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun setCommandListener(listener: (String) -> Unit) {
        onCommandRecognized = listener
    }

    fun getContext(): Context {
        return context
    }

    @MainThread
    fun cleanup() {
        // Ensure we're on the main thread for SpeechRecognizer operations
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cleanup() }
            return
        }
        
        try {
            stopListening()
            
            // Cancel any pending coroutines first
            scope.cancel()
            
            // Clean up speech recognizer
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            
            // Clean up text-to-speech
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            
            Log.d("VoiceRecognition", "Resources cleaned up successfully")
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Error during cleanup: ${e.message}")
        }
    }
}
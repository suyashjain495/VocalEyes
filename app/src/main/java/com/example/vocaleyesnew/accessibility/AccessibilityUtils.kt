package com.example.vocaleyesnew.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * Enhanced accessibility utilities for VocalEyes app
 * Provides better TTS guidance and user feedback for blind users
 */
class AccessibilityUtils(
    private val context: Context,
    private val textToSpeech: TextToSpeech
) {
    
    companion object {
        private const val TAG = "AccessibilityUtils"
        
        // Speech rate for different types of content
        const val SPEECH_RATE_INSTRUCTIONS = 0.9f
        const val SPEECH_RATE_CONTENT = 0.8f
        const val SPEECH_RATE_NAVIGATION = 1.0f
        
        // Priority levels for TTS
        const val PRIORITY_HIGH = TextToSpeech.QUEUE_FLUSH
        const val PRIORITY_NORMAL = TextToSpeech.QUEUE_ADD
    }
    
    /**
     * Speaks text with enhanced formatting for better comprehension
     */
    fun speakText(
        text: String, 
        priority: Int = PRIORITY_NORMAL,
        speechRate: Float = SPEECH_RATE_CONTENT,
        onComplete: (() -> Unit)? = null
    ) {
        if (text.isEmpty()) {
            Log.w(TAG, "Attempted to speak empty text")
            onComplete?.invoke()
            return
        }
        
        // Set appropriate speech rate
        textToSpeech.setSpeechRate(speechRate)
        
        // Format text for better TTS comprehension
        val formattedText = formatTextForTTS(text)
        
        val utteranceId = "utterance_${System.currentTimeMillis()}"
        
        // Set completion listener if callback provided
        onComplete?.let { callback ->
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Started speaking: ${text.take(50)}...")
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Finished speaking")
                    callback.invoke()
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error occurred")
                    callback.invoke()
                }
            })
        }
        
        textToSpeech.speak(formattedText, priority, null, utteranceId)
        
        Log.d(TAG, "Speaking text (${text.length} chars): ${text.take(100)}...")
    }
    
    /**
     * Speaks navigation instructions with appropriate pacing
     */
    fun speakInstruction(instruction: String, onComplete: (() -> Unit)? = null) {
        speakText(
            text = instruction,
            priority = PRIORITY_HIGH,
            speechRate = SPEECH_RATE_INSTRUCTIONS,
            onComplete = onComplete
        )
    }
    
    /**
     * Speaks long content with appropriate breaks and pacing
     */
    fun speakContent(content: String, onComplete: (() -> Unit)? = null) {
        if (content.isEmpty()) {
            speakInstruction("No text content available.") {
                onComplete?.invoke()
            }
            return
        }
        
        // Add reading statistics for user awareness
        val wordCount = content.split("\\s+".toRegex()).size
        val estimatedMinutes = (wordCount / 200.0) // Average reading speed: 200 words/minute
        
        val introduction = when {
            wordCount < 10 -> "Reading short text with $wordCount words."
            wordCount < 100 -> "Reading text with $wordCount words."
            estimatedMinutes < 1.0 -> "Reading text with $wordCount words, estimated reading time: less than a minute."
            else -> "Reading text with $wordCount words, estimated reading time: ${String.format("%.1f", estimatedMinutes)} minutes."
        }
        
        speakInstruction(introduction) {
            // Pause before reading content
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakText(
                    text = content,
                    priority = PRIORITY_NORMAL,
                    speechRate = SPEECH_RATE_CONTENT,
                    onComplete = {
                        speakInstruction("Finished reading.") {
                            onComplete?.invoke()
                        }
                    }
                )
            }, 500) // 500ms pause
        }
    }
    
    /**
     * Stops current speech immediately
     */
    fun stopSpeaking() {
        textToSpeech.stop()
        Log.d(TAG, "Stopped speaking")
    }
    
    /**
     * Provides detailed feedback about OCR results
     */
    fun announceOCRResults(
        text: String,
        processingTimeMs: Long? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val wordCount = if (text.isNotEmpty()) text.split("\\s+".toRegex()).size else 0
        
        val feedback = when {
            text.isEmpty() -> {
                "No text was detected in the image. Please ensure the camera is pointing at clear, well-lit text and try again."
            }
            wordCount == 1 -> {
                "Detected one word: $text"
            }
            wordCount < 10 -> {
                val timeInfo = processingTimeMs?.let { " Processing took ${it}ms." } ?: ""
                "Successfully detected $wordCount words.$timeInfo"
            }
            else -> {
                val timeInfo = processingTimeMs?.let { " Processing took ${it}ms." } ?: ""
                "Successfully detected $wordCount words of text.$timeInfo"
            }
        }
        
        speakInstruction(feedback) {
            if (text.isNotEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    speakContent(text, onComplete)
                }, 500)
            } else {
                onComplete?.invoke()
            }
        }
    }
    
    /**
     * Provides camera guidance for document scanning
     */
    fun provideCameraGuidance(
        confidence: Int,
        documentDetected: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val guidance = when {
            !documentDetected -> "Position a document in the camera frame. Make sure it's well-lit and fully visible."
            confidence < 30 -> "Document partially detected. Move closer and ensure all edges are visible."
            confidence < 60 -> "Document detected but edges unclear. Try to align the document better in the frame."
            confidence < 85 -> "Document detected with good alignment. Hold steady for automatic capture."
            else -> "Document ready for capture with high confidence. Capturing now."
        }
        
        speakInstruction(guidance, onComplete)
    }
    
    /**
     * Provides error feedback with helpful suggestions
     */
    fun announceError(
        error: String,
        suggestions: List<String> = emptyList(),
        onComplete: (() -> Unit)? = null
    ) {
        val fullMessage = buildString {
            append("Error: $error")
            if (suggestions.isNotEmpty()) {
                append(" Suggestions: ")
                append(suggestions.joinToString(". "))
            }
        }
        
        speakInstruction(fullMessage, onComplete)
    }
    
    /**
     * Formats text for better TTS pronunciation and comprehension
     */
    private fun formatTextForTTS(text: String): String {
        return text
            // Add pauses after sentences
            .replace(Regex("([.!?])\\s*"), "$1. ")
            // Add pauses after colons
            .replace(Regex(":"), ". ")
            // Handle common abbreviations
            .replace(Regex("\\bDr\\."), "Doctor")
            .replace(Regex("\\bMr\\."), "Mister")
            .replace(Regex("\\bMrs\\."), "Misses")
            .replace(Regex("\\bMs\\."), "Miss")
            // Handle currency
            .replace(Regex("\\$"), "dollar ")
            .replace(Regex("\\£"), "pound ")
            .replace(Regex("\\€"), "euro ")
            // Handle common symbols
            .replace(Regex("&"), " and ")
            .replace(Regex("%"), " percent")
            .replace(Regex("@"), " at ")
            // Handle phone numbers (basic pattern)
            .replace(Regex("(\\d{3})-(\\d{3})-(\\d{4})"), "$1 $2 $3")
            // Clean up extra spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Suspending function to speak text and wait for completion
     */
    suspend fun speakTextSuspending(
        text: String,
        priority: Int = PRIORITY_NORMAL,
        speechRate: Float = SPEECH_RATE_CONTENT
    ): Unit = suspendCancellableCoroutine { continuation ->
        
        val utteranceId = "suspend_utterance_${System.currentTimeMillis()}"
        
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            
            override fun onDone(utteranceId: String?) {
                continuation.resume(Unit)
            }
            
            override fun onError(utteranceId: String?) {
                continuation.resume(Unit)
            }
        })
        
        textToSpeech.setSpeechRate(speechRate)
        val formattedText = formatTextForTTS(text)
        textToSpeech.speak(formattedText, priority, null, utteranceId)
        
        continuation.invokeOnCancellation {
            textToSpeech.stop()
        }
    }
}

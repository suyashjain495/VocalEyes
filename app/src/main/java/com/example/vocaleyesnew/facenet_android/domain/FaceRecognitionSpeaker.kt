package com.example.vocaleyesnew.facenet_android.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.*

/**
 * Handles text-to-speech announcements for face recognition results with an Indian accent
 */
class FaceRecognitionSpeaker(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val recognizedFaces = mutableSetOf<String>() // To avoid repeating the same name
    private var unknownFaceAnnounced = false // Track if an unknown face was announced
    
    init {
        initTTS()
    }
    
    private fun initTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("FaceRecognitionSpeaker", "Language not supported")
                } else {
                    // Try to find an Indian English voice
                    setIndianAccent()
                    isTtsReady = true
                }
            } else {
                Log.e("FaceRecognitionSpeaker", "TTS initialization failed")
            }
        }
    }
    
    private fun setIndianAccent() {
        // Attempt to find and set an Indian accent voice
        val availableVoices = textToSpeech?.voices
        val indianVoice = availableVoices?.find { voice ->
            (voice.name.contains("en-IN", ignoreCase = true) || 
             voice.name.contains("hindi", ignoreCase = true)) &&
            !voice.name.contains("network", ignoreCase = true)
        }
        
        // If an Indian voice is found, use it
        indianVoice?.let {
            textToSpeech?.voice = it
            Log.d("FaceRecognitionSpeaker", "Using Indian voice: ${it.name}")
        } ?: run {
            // Otherwise just set the speech rate and pitch to simulate an accent
            textToSpeech?.setSpeechRate(0.85f)
            textToSpeech?.setPitch(1.1f)
            Log.d("FaceRecognitionSpeaker", "No Indian voice found, simulating with pitch/rate")
        }
    }
    
    /**
     * Announces the detected face with name and whether it's real or spoof
     */
    fun announceFace(name: String, isSpoof: Boolean) {
        if (!isTtsReady || name.isBlank()) return
        
        // Don't repeat the same face within a short time
        if (recognizedFaces.contains(name)) return
        
        val message = if (isSpoof) {
            "$name, fake photo."
        } else {
            name
        }
        
        textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        
        // Add to recognized faces and clear after 5 seconds to allow announcing again
        recognizedFaces.add(name)
        Thread {
            Thread.sleep(5000)
            recognizedFaces.remove(name)
        }.start()
    }
    
    /**
     * Announces that an unknown person has been detected
     */
    fun announceUnknownFace() {
        if (!isTtsReady || unknownFaceAnnounced) return
        
        val message = "unknown"
        textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        
        // Don't repeat unknown face announcement for a short period
        unknownFaceAnnounced = true
        Thread {
            Thread.sleep(5000)
            unknownFaceAnnounced = false
        }.start()
    }
    
    /**
     * Called when a face is no longer detected
     */
    fun clearFace(name: String) {
        recognizedFaces.remove(name)
    }
    
    /**
     * Resets the unknown face detection flag
     */
    fun clearUnknownFace() {
        unknownFaceAnnounced = false
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
} 
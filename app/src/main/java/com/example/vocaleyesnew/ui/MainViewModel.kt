package com.example.vocaleyesnew.ui

import androidx.lifecycle.ViewModel
import com.example.vocaleyesnew.VoiceRecognitionManager

class MainViewModel(private val voiceRecognitionManager: VoiceRecognitionManager) : ViewModel() {

    fun startListening() {
        voiceRecognitionManager.startListening()
    }

    fun stopListening() {
        voiceRecognitionManager.stopListening()
    }

    fun setCommandListener(listener: (String) -> Unit) {
        voiceRecognitionManager.setCommandListener(listener)
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        voiceRecognitionManager.speak(text, onDone)
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognitionManager.cleanup()
    }
}


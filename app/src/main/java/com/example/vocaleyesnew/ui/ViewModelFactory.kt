package com.example.vocaleyesnew.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.vocaleyesnew.VoiceRecognitionManager

class ViewModelFactory(private val voiceRecognitionManager: VoiceRecognitionManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(voiceRecognitionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


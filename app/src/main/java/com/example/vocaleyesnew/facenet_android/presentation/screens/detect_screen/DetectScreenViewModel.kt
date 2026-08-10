package com.example.vocaleyesnew.facenet_android.presentation.screens.detect_screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.vocaleyesnew.facenet_android.data.RecognitionMetrics
import com.example.vocaleyesnew.facenet_android.domain.FaceRecognitionSpeaker
import com.example.vocaleyesnew.facenet_android.domain.ImageVectorUseCase
import com.example.vocaleyesnew.facenet_android.domain.PersonUseCase

class DetectScreenViewModel(
    val personUseCase: PersonUseCase,
    val imageVectorUseCase: ImageVectorUseCase,
    private val faceRecognitionSpeaker: FaceRecognitionSpeaker
) : ViewModel() {

    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)
    private val previouslyDetectedFaces = mutableSetOf<String>()
    
    fun getNumPeople(): Long = personUseCase.getCount()
    
    /**
     * Announce the detected faces using the Indian accent TTS
     */
    fun announceDetectedFaces(results: List<ImageVectorUseCase.FaceRecognitionResult>) {
        val currentFaces = mutableSetOf<String>()
        var hasUnknownFace = false
        
        results.forEach { result ->
            if (result.personName == "Not recognized") {
                hasUnknownFace = true
            } else if (result.personName.isNotBlank()) {
                val isSpoof = result.spoofResult?.isSpoof == true
                faceRecognitionSpeaker.announceFace(result.personName, isSpoof)
                currentFaces.add(result.personName)
            }
        }
        
        // Announce unknown face if detected
        if (hasUnknownFace) {
            faceRecognitionSpeaker.announceUnknownFace()
        } else {
            faceRecognitionSpeaker.clearUnknownFace()
        }
        
        // Clear faces that are no longer detected
        previouslyDetectedFaces.filter { it !in currentFaces }.forEach {
            faceRecognitionSpeaker.clearFace(it)
        }
        
        previouslyDetectedFaces.clear()
        previouslyDetectedFaces.addAll(currentFaces)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up resources
        faceRecognitionSpeaker.shutdown()
    }
}

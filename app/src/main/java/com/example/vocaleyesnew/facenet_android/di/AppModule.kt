package com.example.vocaleyesnew.facenet_android.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Manually defined Koin modules instead of using annotations
 * to avoid annotation processing issues
 */
object AppModule {
    val module = module {
        // We'll define dependencies manually as needed,
        // without relying on component scanning
        
        // Domain layer - Detection and ML components
        single { com.example.vocaleyesnew.facenet_android.domain.face_detection.MediapipeFaceDetector(androidContext()) }
        single { com.example.vocaleyesnew.facenet_android.domain.face_detection.FaceSpoofDetector(androidContext(), useGpu = false) }
        single { com.example.vocaleyesnew.facenet_android.domain.embeddings.FaceNet(androidContext(), useGpu = false) }
        single { com.example.vocaleyesnew.facenet_android.domain.ImageVectorUseCase(get(), get(), get(), get()) }
        single { com.example.vocaleyesnew.facenet_android.domain.FaceRecognitionSpeaker(androidContext()) }
        
        // Data layer
        single { com.example.vocaleyesnew.facenet_android.data.PersonDB() }
        single { com.example.vocaleyesnew.facenet_android.data.ImagesVectorDB() }
        
        // Use cases
        single { com.example.vocaleyesnew.facenet_android.domain.PersonUseCase(get()) }
        
        // View models
        viewModel { com.example.vocaleyesnew.facenet_android.presentation.screens.detect_screen.DetectScreenViewModel(get(), get(), get()) }
        viewModel { com.example.vocaleyesnew.facenet_android.presentation.screens.face_list.FaceListScreenViewModel(get(), get()) }
        viewModel { com.example.vocaleyesnew.facenet_android.presentation.screens.add_face.AddFaceScreenViewModel(get(), get()) }
    }
}

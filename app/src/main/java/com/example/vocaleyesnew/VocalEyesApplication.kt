package com.example.vocaleyesnew

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.example.vocaleyesnew.facenet_android.data.ObjectBoxStore
import com.example.vocaleyesnew.facenet_android.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VocalEyesApplication : Application(), CameraXConfig.Provider {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin for dependency injection
        startKoin {
            androidContext(this@VocalEyesApplication)
            modules(AppModule.module)
        }
        
        // Initialize ObjectBox for database
        ObjectBoxStore.init(this)
    }
    
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .build()
    }
} 
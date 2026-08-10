package com.example.vocaleyesnew.facenet_android

import android.app.Application
import com.example.vocaleyesnew.facenet_android.data.ObjectBoxStore
import com.example.vocaleyesnew.facenet_android.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * NOTE: This class is redundant and should be removed.
 * The main application class is VocalEyesApplication.kt in the parent package.
 * This file is kept here for reference but should not be used.
 */
class FaceMainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // UNUSED - See VocalEyesApplication instead
        // startKoin {
        //     androidContext(this@FaceMainApplication)
        //     modules(AppModule().module())
        // }
        // ObjectBoxStore.init(this)
    }
}

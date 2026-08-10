package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.util.Log

/**
 * Initializer class responsible for setting up OpenCV for document scanning.
 * This handles the static loader setup and provides graceful fallback handling.
 */
object DocumentScannerInitializer {
    private const val TAG = "DocumentScannerInitializer"
    
    @Volatile
    private var isOpenCVInitialized = false
    
    @Volatile
    private var initializationFailed = false
    
    private var initializationCallbacks = mutableListOf<(Boolean) -> Unit>()
    
    /**
     * Check if OpenCV is available and initialized.
     */
    fun isOpenCVAvailable(): Boolean = isOpenCVInitialized
    
    /**
     * Check if OpenCV initialization has failed.
     */
    fun hasInitializationFailed(): Boolean = initializationFailed
    
    /**
     * Initialize OpenCV for the application.
     * This should be called from the Activity's onCreate method.
     */
    fun initializeOpenCV(context: Context, onInitialized: ((Boolean) -> Unit)? = null) {
        if (isOpenCVInitialized) {
            onInitialized?.invoke(true)
            return
        }
        
        if (initializationFailed) {
            Log.w(TAG, "OpenCV initialization already failed, not retrying")
            onInitialized?.invoke(false)
            return
        }
        
        onInitialized?.let { callback ->
            initializationCallbacks.add(callback)
        }
        
        Log.w(TAG, "OpenCV dependency not available - using fallback initialization")
        
        // Since OpenCV is not available, mark as failed and notify callbacks
        initializationFailed = true
        isOpenCVInitialized = false
        
        // Notify all pending callbacks of failure
        initializationCallbacks.forEach { callback ->
            try {
                callback(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error in failure callback", e)
            }
        }
        initializationCallbacks.clear()
        
        Log.d(TAG, "Fallback initialization completed - will use legacy document detection")
    }
    
    /**
     * Reset initialization state (primarily for testing purposes).
     */
    fun resetInitialization() {
        isOpenCVInitialized = false
        initializationFailed = false
        initializationCallbacks.clear()
        Log.d(TAG, "Initialization state reset")
    }
}

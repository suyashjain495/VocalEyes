package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.util.Log

/**
 * Factory class to create the appropriate document scanner implementation
 * based on OpenCV availability and configuration.
 */
object DocumentScannerFactory {
    private const val TAG = "DocumentScannerFactory"
    
    /**
     * Creates the best available document scanner implementation.
     * Prefers EnhancedDocumentDetector when OpenCV is available,
     * falls back to legacy DocumentDetector otherwise.
     */
    fun createScanner(context: Context): IDocumentScanner {
        return when {
            DocumentScannerInitializer.isOpenCVAvailable() -> {
                Log.d(TAG, "Creating EnhancedDocumentDetector (OpenCV available)")
                EnhancedDocumentDetector(context)
            }
            DocumentScannerInitializer.hasInitializationFailed() -> {
                Log.d(TAG, "Creating legacy DocumentDetector (OpenCV initialization failed)")
                DocumentDetector(context)
            }
            else -> {
                // OpenCV not yet initialized, attempt initialization
                Log.d(TAG, "OpenCV not initialized yet, attempting initialization...")
                DocumentScannerInitializer.initializeOpenCV(context)
                
                // For now, return legacy detector and let the app retry later
                Log.d(TAG, "Creating legacy DocumentDetector (OpenCV initializing)")
                DocumentDetector(context)
            }
        }
    }
    
    /**
     * Creates a scanner with explicit preference.
     * This allows forcing a specific implementation for testing or debugging.
     */
    fun createScanner(context: Context, preferEnhanced: Boolean): IDocumentScanner {
        return if (preferEnhanced && DocumentScannerInitializer.isOpenCVAvailable()) {
            Log.d(TAG, "Creating EnhancedDocumentDetector (explicitly requested)")
            EnhancedDocumentDetector(context)
        } else {
            Log.d(TAG, "Creating legacy DocumentDetector (explicitly requested or OpenCV unavailable)")
            DocumentDetector(context)
        }
    }
    
    /**
     * Creates an enhanced scanner only if OpenCV is available.
     * Returns null if OpenCV is not available.
     */
    fun createEnhancedScannerOrNull(context: Context): EnhancedDocumentDetector? {
        return if (DocumentScannerInitializer.isOpenCVAvailable()) {
            Log.d(TAG, "Creating EnhancedDocumentDetector")
            EnhancedDocumentDetector(context)
        } else {
            Log.w(TAG, "Cannot create EnhancedDocumentDetector - OpenCV not available")
            null
        }
    }
    
    /**
     * Gets the name of the scanner implementation that would be created.
     * Useful for debugging and logging.
     */
    fun getScannerTypeName(context: Context): String {
        return when {
            DocumentScannerInitializer.isOpenCVAvailable() -> "EnhancedDocumentDetector"
            DocumentScannerInitializer.hasInitializationFailed() -> "DocumentDetector (OpenCV failed)"
            else -> "DocumentDetector (OpenCV not initialized)"
        }
    }
    
    /**
     * Check if the enhanced scanner is available.
     */
    fun isEnhancedScannerAvailable(): Boolean {
        return DocumentScannerInitializer.isOpenCVAvailable()
    }
}

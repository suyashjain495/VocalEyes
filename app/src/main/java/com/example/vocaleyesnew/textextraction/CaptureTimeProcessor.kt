package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Implements the capture-time processing workflow as specified in Step 5:
 * 1. After capture, run EnhancedDocumentDetector.detect() at full resolution
 * 2. If corners found and confidence ≥ 20%, call cropAndCorrectPerspective() then enhanceForOCR()
 * 3. Else, skip cropping and use full bitmap
 * 4. Pass final bitmap to TextRecognizer.recognizeText() (suspending callback)
 * 5. Emit both processed bitmap (for debug / preview) and extracted text
 */
class CaptureTimeProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "CaptureTimeProcessor"
        private const val MIN_CONFIDENCE_THRESHOLD = 20f
    }
    
    private val enhancedDocumentDetector = EnhancedDocumentDetector(context)
    private val textRecognizer = TextRecognizer(context) { /* Empty callback, will use direct callbacks */ }
    private val errorHandler = DocumentProcessingErrorHandler(context)
    
    /**
     * Set TTS for error handling feedback
     */
    fun setTextToSpeech(tts: TextToSpeech?) {
        errorHandler.setTextToSpeech(tts)
    }
    
    /**
     * Set error handling callbacks
     */
    fun setErrorCallbacks(
        onAutoCaptureModeChanged: ((Boolean) -> Unit)? = null,
        onRecoveryGuidanceNeeded: ((String) -> Unit)? = null,
        onFailureThresholdReached: ((String) -> Unit)? = null
    ) {
        errorHandler.setCallbacks(onAutoCaptureModeChanged, onRecoveryGuidanceNeeded, onFailureThresholdReached)
    }
    
    /**
     * Callback interface for capture-time processing results
     */
    interface ProcessingCallback {
        fun onProcessingComplete(
            processedBitmap: Bitmap,  // For debug/preview
            extractedText: String     // Extracted text content
        )
        
        fun onProcessingError(error: String)
    }
    
    /**
     * Process a captured bitmap through the complete capture-time workflow
     * 
     * @param capturedBitmap The bitmap captured from camera at full resolution
     * @param callback Callback to receive the processing results
     */
    suspend fun processCapturedImage(
        capturedBitmap: Bitmap,
        callback: ProcessingCallback
    ) {
        Log.d(TAG, "Starting capture-time processing for ${capturedBitmap.width}x${capturedBitmap.height} image")
        
        try {
            // Step 1: Run EnhancedDocumentDetector.detect() at full resolution with error handling
            val detectedCorners = withContext(Dispatchers.IO) {
                Log.d(TAG, "Step 1: Running enhanced document detection at full resolution")
                try {
                    enhancedDocumentDetector.detect(capturedBitmap)
                } catch (e: Exception) {
                    val errorResult = errorHandler.handleDetectionError(e)
                    if (errorResult.fallbackAction == DocumentProcessingErrorHandler.FallbackAction.PROCESS_FULL_IMAGE) {
                        Log.d(TAG, "Detection failed, proceeding with full image processing")
                        null // Continue with null corners to process full image
                    } else {
                        throw e // Re-throw if error handler says not to continue
                    }
                }
            }
            
            // Step 2 & 3: Process based on detection results with error handling
            val processedBitmap = withContext(Dispatchers.IO) {
                if (detectedCorners != null && detectedCorners.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "Step 2: Corners found with confidence ${detectedCorners.confidence}%, performing crop and enhance")
                    
                    // Call cropAndCorrectPerspective() with error handling
                    val croppedBitmap = try {
                        enhancedDocumentDetector.cropAndCorrect(capturedBitmap, detectedCorners)
                    } catch (e: Exception) {
                        val errorResult = errorHandler.handleCroppingError(e)
                        if (errorResult.fallbackAction == DocumentProcessingErrorHandler.FallbackAction.USE_ORIGINAL_IMAGE) {
                            Log.d(TAG, "Cropping failed, using original bitmap as fallback")
                            null // Use null to trigger original bitmap usage
                        } else {
                            throw e // Re-throw if error handler says not to continue
                        }
                    }
                    
                    if (croppedBitmap != null) {
                        // Then enhanceForOCR()
                        Log.d(TAG, "Cropping successful, enhancing for OCR")
                        try {
                            enhancedDocumentDetector.enhanceForOcr(croppedBitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error enhancing cropped bitmap, using original", e)
                            enhancedDocumentDetector.enhanceForOcr(capturedBitmap)
                        }
                    } else {
                        Log.w(TAG, "Cropping failed, using original bitmap with enhancement")
                        try {
                            enhancedDocumentDetector.enhanceForOcr(capturedBitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error enhancing original bitmap, using as-is", e)
                            capturedBitmap
                        }
                    }
                } else {
                    Log.d(TAG, "Step 3: No suitable corners found or confidence too low, using full bitmap with enhancement")
                    // Skip cropping and use full bitmap, but still enhance for OCR
                    try {
                        enhancedDocumentDetector.enhanceForOcr(capturedBitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enhancing bitmap, using as-is", e)
                        capturedBitmap
                    }
                }
            }
            
            Log.d(TAG, "Step 4: Passing final bitmap to TextRecognizer")
            
            // Step 4: Pass final bitmap to TextRecognizer.recognizeText() with error handling
            try {
                textRecognizer.recognizeText(processedBitmap) { extractedText ->
                    Log.d(TAG, "Step 5: OCR completed, extracted text length: ${extractedText.length}")
                    
                    // Handle empty OCR results
                    if (extractedText.trim().isEmpty()) {
                        val errorResult = errorHandler.handleEmptyOcrResult()
                        if (errorResult.shouldContinue) {
                            // Still complete processing but with empty text and error message
                            callback.onProcessingComplete(processedBitmap, "")
                        } else {
                            callback.onProcessingError(errorResult.userMessage)
                        }
                    } else {
                        // Success - reset failure counts
                        errorHandler.resetFailureCountsOnSuccess()
                        
                        // Step 5: Emit both processed bitmap (for debug/preview) and extracted text
                        callback.onProcessingComplete(processedBitmap, extractedText)
                    }
                }
            } catch (e: Exception) {
                val errorResult = errorHandler.handleOcrError(e)
                callback.onProcessingError(errorResult.userMessage)
                return // Exit early on OCR error
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during capture-time processing", e)
            val errorResult = errorHandler.handleGenericError(e, "capture-time processing")
            callback.onProcessingError(errorResult.userMessage)
        }
    }
    
    /**
     * Process a captured ImageProxy through the complete capture-time workflow
     * This is a convenience method for processing camera frames directly
     * 
     * @param imageProxy The ImageProxy captured from camera
     * @param callback Callback to receive the processing results
     */
    suspend fun processCapturedImage(
        imageProxy: ImageProxy,
        callback: ProcessingCallback
    ) {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                imageProxy.toBitmap()
            }
            processCapturedImage(bitmap, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            callback.onProcessingError("Failed to convert image: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Alternative method that returns results via a data class instead of callback
     * This is useful for simpler integration scenarios
     */
    data class ProcessingResult(
        val processedBitmap: Bitmap,
        val extractedText: String,
        val wasDocumentDetected: Boolean,
        val detectionConfidence: Float
    )
    
    /**
     * Process captured image and return results synchronously
     * Note: This method should be called from a background thread
     */
    suspend fun processCapturedImageSync(capturedBitmap: Bitmap): ProcessingResult {
        Log.d(TAG, "Starting synchronous capture-time processing")
        
        // Step 1: Run EnhancedDocumentDetector.detect() at full resolution
        val detectedCorners = enhancedDocumentDetector.detect(capturedBitmap)
        
        // Step 2 & 3: Process based on detection results
        val processedBitmap = if (detectedCorners != null && detectedCorners.confidence >= MIN_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Synchronous processing: Corners found, performing crop and enhance")
            val croppedBitmap = enhancedDocumentDetector.cropAndCorrect(capturedBitmap, detectedCorners)
            if (croppedBitmap != null) {
                enhancedDocumentDetector.enhanceForOcr(croppedBitmap)
            } else {
                enhancedDocumentDetector.enhanceForOcr(capturedBitmap)
            }
        } else {
            Log.d(TAG, "Synchronous processing: Using full bitmap with enhancement")
            enhancedDocumentDetector.enhanceForOcr(capturedBitmap)
        }
        
        // Step 4: OCR processing (Note: This uses callback internally, so we need to wait)
        var extractedText = ""
        var ocrCompleted = false
        
        textRecognizer.recognizeText(processedBitmap) { text ->
            extractedText = text
            ocrCompleted = true
        }
        
        // Wait for OCR to complete (with timeout)
        val startTime = System.currentTimeMillis()
        while (!ocrCompleted && (System.currentTimeMillis() - startTime) < 15000) { // 15 second timeout
            kotlinx.coroutines.delay(100)
        }
        
        if (!ocrCompleted) {
            Log.w(TAG, "OCR timed out, returning empty text")
        }
        
        // Step 5: Return both processed bitmap and extracted text
        return ProcessingResult(
            processedBitmap = processedBitmap,
            extractedText = extractedText,
            wasDocumentDetected = detectedCorners != null && detectedCorners.confidence >= MIN_CONFIDENCE_THRESHOLD,
            detectionConfidence = detectedCorners?.confidence ?: 0f
        )
    }
    
    /**
     * Stop any ongoing processing operations
     */
    fun stopProcessing() {
        textRecognizer.stopRecognition()
    }
    
    /**
     * Restart processing operations
     */
    fun restartProcessing() {
        textRecognizer.restartRecognition()
    }
}

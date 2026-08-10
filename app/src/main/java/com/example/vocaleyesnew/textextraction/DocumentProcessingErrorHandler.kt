package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive error handling system for document processing operations.
 * 
 * Features:
 * - Exception catching for detection, cropping, and OCR operations
 * - User-friendly TTS error messages
 * - Consecutive failure tracking
 * - Auto-capture disable after repeated failures
 * - Recovery suggestions and guidance
 */
class DocumentProcessingErrorHandler(
    private val context: Context,
    private val maxConsecutiveFailures: Int = 3
) {
    
    companion object {
        private const val TAG = "DocumentProcessingErrorHandler"
        
        // Error message categories
        private const val ERROR_DETECTION_FAILED = "detection_failed"
        private const val ERROR_CROPPING_FAILED = "cropping_failed" 
        private const val ERROR_OCR_FAILED = "ocr_failed"
        private const val ERROR_OCR_EMPTY = "ocr_empty"
        private const val ERROR_GENERIC = "generic_error"
        
        // Recovery guidance
        private const val GUIDANCE_LIGHTING = "lighting_guidance"
        private const val GUIDANCE_DISTANCE = "distance_guidance"
        private const val GUIDANCE_STABILITY = "stability_guidance"
        private const val GUIDANCE_MANUAL_MODE = "manual_mode"
    }
    
    // Error tracking
    private val consecutiveDetectionFailures = AtomicInteger(0)
    private val consecutiveCroppingFailures = AtomicInteger(0)
    private val consecutiveOcrFailures = AtomicInteger(0)
    private val consecutiveEmptyOcrResults = AtomicInteger(0)
    
    // State management
    private var isAutoCaptureModeDisabled = false
    private var lastErrorTime = 0L
    private var tts: TextToSpeech? = null
    
    // Callbacks
    private var onAutoCaptureModeChanged: ((Boolean) -> Unit)? = null
    private var onRecoveryGuidanceNeeded: ((String) -> Unit)? = null
    private var onFailureThresholdReached: ((String) -> Unit)? = null
    
    /**
     * Set TTS instance for audio feedback
     */
    fun setTextToSpeech(tts: TextToSpeech?) {
        this.tts = tts
    }
    
    /**
     * Set callback listeners
     */
    fun setCallbacks(
        onAutoCaptureModeChanged: ((Boolean) -> Unit)? = null,
        onRecoveryGuidanceNeeded: ((String) -> Unit)? = null,
        onFailureThresholdReached: ((String) -> Unit)? = null
    ) {
        this.onAutoCaptureModeChanged = onAutoCaptureModeChanged
        this.onRecoveryGuidanceNeeded = onRecoveryGuidanceNeeded
        this.onFailureThresholdReached = onFailureThresholdReached
    }
    
    /**
     * Handle document detection failures
     */
    fun handleDetectionError(exception: Exception?): ErrorHandlingResult {
        val failureCount = consecutiveDetectionFailures.incrementAndGet()
        Log.e(TAG, "Document detection failed (consecutive: $failureCount)", exception)
        
        // Reset other failure counters on different error type
        resetOtherFailureCounters(ErrorType.DETECTION)
        
        val errorMessage = "No document detected, processing full image"
        val ttsMessage = if (failureCount == 1) {
            "No document detected. Processing the full image instead."
        } else {
            "Still unable to detect document. Using full image. Try improving lighting or moving closer."
        }
        
        speakMessage(ttsMessage, ERROR_DETECTION_FAILED)
        
        // Check if threshold reached
        if (failureCount >= maxConsecutiveFailures) {
            return handleFailureThresholdReached(ErrorType.DETECTION, errorMessage)
        }
        
        // Provide guidance after 2 failures
        if (failureCount == 2) {
            provideLightingAndDistanceGuidance()
        }
        
        return ErrorHandlingResult(
            shouldContinue = true,
            fallbackAction = FallbackAction.PROCESS_FULL_IMAGE,
            userMessage = errorMessage,
            errorType = ErrorType.DETECTION
        )
    }
    
    /**
     * Handle document cropping failures
     */
    fun handleCroppingError(exception: Exception?): ErrorHandlingResult {
        val failureCount = consecutiveCroppingFailures.incrementAndGet()
        Log.e(TAG, "Document cropping failed (consecutive: $failureCount)", exception)
        
        resetOtherFailureCounters(ErrorType.CROPPING)
        
        val errorMessage = "Document cropping failed, using original image"
        val ttsMessage = if (failureCount == 1) {
            "Unable to crop document properly. Using the original image."
        } else {
            "Cropping continues to fail. Please ensure the document edges are clearly visible."
        }
        
        speakMessage(ttsMessage, ERROR_CROPPING_FAILED)
        
        if (failureCount >= maxConsecutiveFailures) {
            return handleFailureThresholdReached(ErrorType.CROPPING, errorMessage)
        }
        
        // Provide stability guidance after 2 failures
        if (failureCount == 2) {
            provideStabilityGuidance()
        }
        
        return ErrorHandlingResult(
            shouldContinue = true,
            fallbackAction = FallbackAction.USE_ORIGINAL_IMAGE,
            userMessage = errorMessage,
            errorType = ErrorType.CROPPING
        )
    }
    
    /**
     * Handle OCR processing failures
     */
    fun handleOcrError(exception: Exception?): ErrorHandlingResult {
        val failureCount = consecutiveOcrFailures.incrementAndGet()
        Log.e(TAG, "OCR processing failed (consecutive: $failureCount)", exception)
        
        resetOtherFailureCounters(ErrorType.OCR)
        
        val errorMessage = "Text recognition failed"
        val ttsMessage = when (failureCount) {
            1 -> "Text recognition failed. Please try again."
            2 -> "Text recognition failed again. Try improving lighting and ensure text is clearly visible."
            else -> "Repeated text recognition failures. Consider switching to manual mode."
        }
        
        speakMessage(ttsMessage, ERROR_OCR_FAILED)
        
        if (failureCount >= maxConsecutiveFailures) {
            return handleFailureThresholdReached(ErrorType.OCR, errorMessage)
        }
        
        return ErrorHandlingResult(
            shouldContinue = false,
            fallbackAction = FallbackAction.RETRY_LATER,
            userMessage = errorMessage,
            errorType = ErrorType.OCR
        )
    }
    
    /**
     * Handle empty OCR results
     */
    fun handleEmptyOcrResult(): ErrorHandlingResult {
        val failureCount = consecutiveEmptyOcrResults.incrementAndGet()
        Log.w(TAG, "OCR returned empty result (consecutive: $failureCount)")
        
        resetOtherFailureCounters(ErrorType.OCR_EMPTY)
        
        val errorMessage = "No text detected in document"
        val ttsMessage = when (failureCount) {
            1 -> "No text was detected in the document. Please try again with better lighting."
            2 -> "Still no text detected. Please adjust the lighting and move closer to the document."
            else -> "Repeatedly unable to detect text. Please check if the document contains readable text, adjust lighting, or move closer."
        }
        
        speakMessage(ttsMessage, ERROR_OCR_EMPTY)
        
        if (failureCount >= maxConsecutiveFailures) {
            return handleFailureThresholdReached(ErrorType.OCR_EMPTY, errorMessage)
        }
        
        // Provide detailed guidance after each failure for empty OCR
        provideLightingAndDistanceGuidance()
        
        return ErrorHandlingResult(
            shouldContinue = true,
            fallbackAction = FallbackAction.REQUEST_RETRY,
            userMessage = errorMessage,
            errorType = ErrorType.OCR_EMPTY
        )
    }
    
    /**
     * Handle generic processing errors
     */
    fun handleGenericError(exception: Exception?, operation: String): ErrorHandlingResult {
        Log.e(TAG, "Generic processing error in $operation", exception)
        
        val errorMessage = "Processing error occurred"
        val ttsMessage = "An error occurred while processing. Please try again."
        
        speakMessage(ttsMessage, ERROR_GENERIC)
        
        return ErrorHandlingResult(
            shouldContinue = false,
            fallbackAction = FallbackAction.RETRY_LATER,
            userMessage = errorMessage,
            errorType = ErrorType.GENERIC
        )
    }
    
    /**
     * Reset failure counts on successful operation
     */
    fun resetFailureCountsOnSuccess() {
        val hadFailures = hasAnyFailures()
        
        consecutiveDetectionFailures.set(0)
        consecutiveCroppingFailures.set(0)
        consecutiveOcrFailures.set(0)
        consecutiveEmptyOcrResults.set(0)
        
        // Re-enable auto-capture if it was disabled
        if (isAutoCaptureModeDisabled) {
            isAutoCaptureModeDisabled = false
            onAutoCaptureModeChanged?.invoke(true)
            
            speakMessage(
                "Processing successful! Auto-capture mode has been re-enabled.",
                "auto_capture_reenabled"
            )
        } else if (hadFailures) {
            speakMessage("Processing successful!", "success_after_failure")
        }
        
        Log.d(TAG, "All failure counts reset on successful operation")
    }
    
    /**
     * Check if auto-capture mode is currently disabled
     */
    fun isAutoCaptureModeDisabled(): Boolean = isAutoCaptureModeDisabled
    
    /**
     * Get current failure statistics
     */
    fun getFailureStatistics(): FailureStatistics {
        return FailureStatistics(
            detectionFailures = consecutiveDetectionFailures.get(),
            croppingFailures = consecutiveCroppingFailures.get(),
            ocrFailures = consecutiveOcrFailures.get(),
            emptyOcrResults = consecutiveEmptyOcrResults.get(),
            isAutoCaptureModeDisabled = isAutoCaptureModeDisabled,
            lastErrorTime = lastErrorTime
        )
    }
    
    /**
     * Manually enable auto-capture mode (for user override)
     */
    fun enableAutoCaptureModeManually() {
        if (isAutoCaptureModeDisabled) {
            isAutoCaptureModeDisabled = false
            onAutoCaptureModeChanged?.invoke(true)
            
            // Reset failure counts when manually re-enabling
            consecutiveDetectionFailures.set(0)
            consecutiveCroppingFailures.set(0)
            consecutiveOcrFailures.set(0)
            consecutiveEmptyOcrResults.set(0)
            
            speakMessage(
                "Auto-capture mode manually re-enabled. The app will now automatically detect and capture documents again.",
                "manual_auto_capture_enable"
            )
            
            Log.d(TAG, "Auto-capture mode manually re-enabled by user")
        }
    }
    
    // Private helper methods
    
    private fun handleFailureThresholdReached(errorType: ErrorType, errorMessage: String): ErrorHandlingResult {
        isAutoCaptureModeDisabled = true
        onAutoCaptureModeChanged?.invoke(false)
        onFailureThresholdReached?.invoke(errorType.toString())
        
        val ttsMessage = when (errorType) {
            ErrorType.DETECTION -> "Too many document detection failures. Auto-capture has been disabled. Please use manual mode by double-tapping when ready."
            ErrorType.CROPPING -> "Repeated cropping failures. Auto-capture disabled. Try manual mode and ensure document edges are clearly visible."
            ErrorType.OCR -> "Multiple text recognition failures. Auto-capture disabled. Please check lighting and document quality, then use manual mode."
            ErrorType.OCR_EMPTY -> "Unable to detect any text after multiple attempts. Auto-capture disabled. Please verify the document contains text, improve lighting, and use manual mode."
            ErrorType.GENERIC -> "Too many processing errors. Auto-capture disabled. Please use manual mode."
        }
        
        speakMessage(ttsMessage, GUIDANCE_MANUAL_MODE)
        
        Log.w(TAG, "Failure threshold reached for $errorType. Auto-capture mode disabled.")
        
        return ErrorHandlingResult(
            shouldContinue = false,
            fallbackAction = FallbackAction.DISABLE_AUTO_CAPTURE,
            userMessage = "$errorMessage. Auto-capture disabled.",
            errorType = errorType
        )
    }
    
    private fun resetOtherFailureCounters(currentErrorType: ErrorType) {
        when (currentErrorType) {
            ErrorType.DETECTION -> {
                consecutiveCroppingFailures.set(0)
                consecutiveOcrFailures.set(0)
                consecutiveEmptyOcrResults.set(0)
            }
            ErrorType.CROPPING -> {
                consecutiveDetectionFailures.set(0)
                consecutiveOcrFailures.set(0)
                consecutiveEmptyOcrResults.set(0)
            }
            ErrorType.OCR -> {
                consecutiveDetectionFailures.set(0)
                consecutiveCroppingFailures.set(0)
                consecutiveEmptyOcrResults.set(0)
            }
            ErrorType.OCR_EMPTY -> {
                consecutiveDetectionFailures.set(0)
                consecutiveCroppingFailures.set(0)
                consecutiveOcrFailures.set(0)
            }
            ErrorType.GENERIC -> {
                // Don't reset other counters for generic errors
            }
        }
    }
    
    private fun provideLightingAndDistanceGuidance() {
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(2000) // Wait a bit before providing guidance
            
            val guidance = "For better results: ensure good lighting, avoid shadows, and hold the camera 6 to 12 inches from the document."
            speakMessage(guidance, GUIDANCE_LIGHTING)
            onRecoveryGuidanceNeeded?.invoke(GUIDANCE_LIGHTING)
        }
    }
    
    private fun provideStabilityGuidance() {
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(2000)
            
            val guidance = "Hold the device steady and ensure all four corners of the document are visible in the camera view."
            speakMessage(guidance, GUIDANCE_STABILITY)
            onRecoveryGuidanceNeeded?.invoke(GUIDANCE_STABILITY)
        }
    }
    
    private fun speakMessage(message: String, utteranceId: String) {
        tts?.let { tts ->
            if (tts.isSpeaking) {
                tts.stop() // Stop current speech for important error messages
            }
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
        
        lastErrorTime = System.currentTimeMillis()
    }
    
    private fun hasAnyFailures(): Boolean {
        return consecutiveDetectionFailures.get() > 0 ||
                consecutiveCroppingFailures.get() > 0 ||
                consecutiveOcrFailures.get() > 0 ||
                consecutiveEmptyOcrResults.get() > 0
    }
    
    // Data classes and enums
    
    enum class ErrorType {
        DETECTION,
        CROPPING, 
        OCR,
        OCR_EMPTY,
        GENERIC
    }
    
    enum class FallbackAction {
        PROCESS_FULL_IMAGE,
        USE_ORIGINAL_IMAGE,
        RETRY_LATER,
        REQUEST_RETRY,
        DISABLE_AUTO_CAPTURE
    }
    
    data class ErrorHandlingResult(
        val shouldContinue: Boolean,
        val fallbackAction: FallbackAction,
        val userMessage: String,
        val errorType: ErrorType
    )
    
    data class FailureStatistics(
        val detectionFailures: Int,
        val croppingFailures: Int,
        val ocrFailures: Int,
        val emptyOcrResults: Int,
        val isAutoCaptureModeDisabled: Boolean,
        val lastErrorTime: Long
    )
}

package com.example.vocaleyesnew.accessibility

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Interface for listening to tap events from the centralized tap handler
 */
interface TapListener {
    fun onSingleTap(x: Float, y: Float)
    fun onDoubleTap(x: Float, y: Float)
}

/**
 * Centralized tap handling system that distinguishes between single and double taps
 * with a configurable timeout (default 1.5 seconds).
 * 
 * This class provides a universal handler that can be used by both traditional Android
 * touch handling and Jetpack Compose gesture detection to ensure consistent behavior
 * across the entire app.
 * 
 * Usage:
 * ```
 * CentralTapHandler.registerTap(x, y, object : TapListener {
 *     override fun onSingleTap(x: Float, y: Float) { /* handle single tap */ }
 *     override fun onDoubleTap(x: Float, y: Float) { /* handle double tap */ }
 * })
 * ```
 */
object CentralTapHandler {
    
    private const val TAG = "CentralTapHandler"
    private const val DEFAULT_DOUBLE_TAP_TIMEOUT = 1500L // 1.5 seconds as requested
    
    // Coroutine scope for managing timeout operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // Current pending tap state
    private data class PendingTap(
        val x: Float,
        val y: Float,
        val listener: TapListener,
        val timestamp: Long
    )
    
    // Atomic reference to current pending tap to ensure thread safety
    private val pendingTap: AtomicReference<PendingTap?> = AtomicReference(null)
    
    // Current timeout job
    private var timeoutJob: Job? = null
    
    /**
     * Register a tap at the specified coordinates with the given listener.
     * This method implements the timeout logic to distinguish between single and double taps.
     * 
     * @param x X coordinate of the tap
     * @param y Y coordinate of the tap  
     * @param listener Callback interface for tap events
     * @param doubleTapTimeout Timeout in milliseconds (defaults to 1.5 seconds)
     */
    @JvmOverloads
    fun registerTap(
        x: Float, 
        y: Float, 
        listener: TapListener,
        doubleTapTimeout: Long = DEFAULT_DOUBLE_TAP_TIMEOUT
    ) {
        val currentTime = System.currentTimeMillis()
        val current = pendingTap.get()
        
        Log.d(TAG, "Tap registered at ($x, $y) with ${doubleTapTimeout}ms timeout")
        
        if (current != null && (currentTime - current.timestamp) <= doubleTapTimeout) {
            // This is a double tap - cancel timeout and trigger double tap immediately
            Log.d(TAG, "Double tap detected at ($x, $y)")
            
            // Cancel the pending single tap timeout
            timeoutJob?.cancel()
            timeoutJob = null
            
            // Clear pending tap
            pendingTap.set(null)
            
            // Trigger double tap on the original listener (first tap determines the listener)
            current.listener.onDoubleTap(current.x, current.y)
            
        } else {
            // This is potentially the first tap of a sequence
            Log.d(TAG, "First tap detected at ($x, $y), waiting for potential double tap")
            
            // Cancel any existing timeout
            timeoutJob?.cancel()
            
            // Store this tap as pending
            val newPendingTap = PendingTap(x, y, listener, currentTime)
            pendingTap.set(newPendingTap)
            
            // Set timeout for single tap confirmation
            timeoutJob = scope.launch {
                try {
                    delay(doubleTapTimeout)
                    
                    // Timeout reached - this is a confirmed single tap
                    val stillPending = pendingTap.getAndSet(null)
                    if (stillPending != null) {
                        Log.d(TAG, "Single tap confirmed at (${stillPending.x}, ${stillPending.y})")
                        stillPending.listener.onSingleTap(stillPending.x, stillPending.y)
                    }
                } catch (e: CancellationException) {
                    // Timeout was cancelled (double tap occurred) - this is expected
                    Log.d(TAG, "Single tap timeout cancelled (double tap detected)")
                }
            }
        }
    }
    
    /**
     * Cancel any pending tap operations.
     * Useful when the UI context changes and pending taps should be discarded.
     */
    fun cancelPendingTaps() {
        Log.d(TAG, "Cancelling all pending taps")
        timeoutJob?.cancel()
        timeoutJob = null
        pendingTap.set(null)
    }
    
    /**
     * Check if there's a tap currently pending (waiting for potential double tap)
     */
    fun hasPendingTap(): Boolean {
        return pendingTap.get() != null
    }
    
    /**
     * Clean up resources. Should be called when the app is shutting down.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up CentralTapHandler")
        cancelPendingTaps()
        scope.cancel()
    }
}

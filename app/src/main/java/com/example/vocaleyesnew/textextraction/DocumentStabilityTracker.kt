package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Document stability tracker implementing Adobe Scan-like auto-capture functionality.
 * 
 * Features:
 * - Circular buffer of last N DocumentCorners + accelerometer readings
 * - Stability detection (IoU > 0.8 for ≥5 consecutive frames, motion < 0.1 m/s²)
 * - 1-second haptic + audible countdown when stable AND confidence ≥ 85%
 * - Auto-capture with ImageCapture.takePicture() and overlay freeze
 */
class DocumentStabilityTracker(
    private val context: Context,
    private val bufferSize: Int = 8,
    private val minStableFrames: Int = 5,
    private val minIoUThreshold: Float = 0.8f,
    private val maxMotionThreshold: Float = 0.1f, // m/s²
    private val minConfidenceForCapture: Float = 85f
) : SensorEventListener {

    companion object {
        private const val TAG = "DocumentStabilityTracker"
        private const val COUNTDOWN_DURATION_MS = 1000L
        private const val VIBRATION_DURATION_MS = 200L
    }

    // Circular buffer for DocumentCorners and accelerometer data
    private data class FrameData(
        val corners: DocumentCorners?,
        val timestamp: Long,
        val accelerationMagnitude: Float
    )

    private val frameBuffer = mutableListOf<FrameData>()
    private var currentBufferIndex = 0

    // Sensor management
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    // Vibrator for haptic feedback
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // State management
    private var isStable = false
    private var isCapturing = false
    private var countdownJob: Job? = null
    private var stableStartTime = 0L

    // Callbacks
    private var onStabilityChanged: ((Boolean) -> Unit)? = null
    private var onCountdownStarted: (() -> Unit)? = null
    private var onCountdownTick: ((Int) -> Unit)? = null
    private var onAutoCapture: ((DocumentCorners) -> Unit)? = null
    private var onMotionDetected: ((Float) -> Unit)? = null

    // TTS and ImageCapture references
    private var tts: TextToSpeech? = null
    private var imageCapture: ImageCapture? = null
    private var errorHandler: DocumentProcessingErrorHandler? = null

    init {
        // Initialize buffer with empty data
        repeat(bufferSize) {
            frameBuffer.add(FrameData(null, 0L, 0f))
        }
    }

    /**
     * Start tracking stability (begins accelerometer monitoring)
     */
    fun startTracking() {
        Log.d(TAG, "Starting document stability tracking")
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    /**
     * Stop tracking stability (stops accelerometer monitoring)
     */
    fun stopTracking() {
        Log.d(TAG, "Stopping document stability tracking")
        sensorManager.unregisterListener(this)
        countdownJob?.cancel()
        countdownJob = null
        isCapturing = false
        isStable = false
    }

    /**
     * Add new document detection result to circular buffer
     */
    fun addDetectionResult(corners: DocumentCorners?) {
        val timestamp = System.currentTimeMillis()
        
        // Add to circular buffer
        val frameData = FrameData(corners, timestamp, currentAcceleration)
        frameBuffer[currentBufferIndex] = frameData
        currentBufferIndex = (currentBufferIndex + 1) % bufferSize

        // Check stability conditions
        checkStability()
    }

    /**
     * Set callback functions
     */
    fun setCallbacks(
        onStabilityChanged: ((Boolean) -> Unit)? = null,
        onCountdownStarted: (() -> Unit)? = null,
        onCountdownTick: ((Int) -> Unit)? = null,
        onAutoCapture: ((DocumentCorners) -> Unit)? = null,
        onMotionDetected: ((Float) -> Unit)? = null
    ) {
        this.onStabilityChanged = onStabilityChanged
        this.onCountdownStarted = onCountdownStarted
        this.onCountdownTick = onCountdownTick
        this.onAutoCapture = onAutoCapture
        this.onMotionDetected = onMotionDetected
    }

    /**
     * Set TTS for audio feedback
     */
    fun setTextToSpeech(tts: TextToSpeech?) {
        this.tts = tts
    }

    /**
     * Set ImageCapture for auto-capture functionality
     */
    fun setImageCapture(imageCapture: ImageCapture?) {
        this.imageCapture = imageCapture
    }
    
    /**
     * Set error handler for failure tracking and auto-capture management
     */
    fun setErrorHandler(errorHandler: DocumentProcessingErrorHandler?) {
        this.errorHandler = errorHandler
    }

    /**
     * Force manual capture (for testing or user-triggered capture)
     */
    fun forceCapture() {
        val latestFrame = getLatestValidFrame()
        latestFrame?.corners?.let { corners ->
            Log.d(TAG, "Force capture triggered")
            performCapture(corners)
        }
    }

    // Accelerometer sensor event handling
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { 
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1] 
                val z = it.values[2]
                
                lastAcceleration = currentAcceleration
                currentAcceleration = sqrt(x * x + y * y + z * z)
                val deltaAcceleration = abs(currentAcceleration - lastAcceleration)
                
                // Notify motion callback
                onMotionDetected?.invoke(deltaAcceleration)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    /**
     * Check if current conditions meet stability requirements
     */
    private fun checkStability() {
        val currentTime = System.currentTimeMillis()
        
        // Get recent frames for stability analysis
        val recentFrames = getRecentFrames(minStableFrames)
        
        if (recentFrames.size < minStableFrames) {
            updateStabilityState(false)
            return
        }

        // Check if all recent frames have valid document detection
        val allFramesValid = recentFrames.all { it.corners != null }
        if (!allFramesValid) {
            updateStabilityState(false)
            return
        }

        // Check motion stability (all frames must have low motion)
        val motionStable = recentFrames.all { it.accelerationMagnitude < maxMotionThreshold }
        if (!motionStable) {
            updateStabilityState(false)
            return
        }

        // Check document position stability using IoU
        val positionStable = checkPositionStability(recentFrames)
        if (!positionStable) {
            updateStabilityState(false)
            return
        }

        // Check confidence for auto-capture eligibility
        val latestCorners = recentFrames.last().corners!!
        val confidenceHigh = latestCorners.confidence >= minConfidenceForCapture

        // Update stability state
        val nowStable = true
        updateStabilityState(nowStable)

        // Start countdown if conditions are met, not already capturing, and auto-capture is enabled
        val isAutoCaptureModeEnabled = errorHandler?.isAutoCaptureModeDisabled()?.not() ?: true
        if (nowStable && confidenceHigh && !isCapturing && isAutoCaptureModeEnabled) {
            startCountdown(latestCorners)
        } else if (!isAutoCaptureModeEnabled) {
            Log.d(TAG, "Auto-capture disabled by error handler, skipping countdown")
        }
    }

    /**
     * Check if document position is stable using IoU (Intersection over Union)
     */
    private fun checkPositionStability(frames: List<FrameData>): Boolean {
        if (frames.size < 2) return false

        val referenceCorners = frames.first().corners ?: return false

        // Check IoU between reference frame and all subsequent frames
        for (i in 1 until frames.size) {
            val currentCorners = frames[i].corners ?: return false
            val iou = calculateIoU(referenceCorners, currentCorners)
            
            if (iou < minIoUThreshold) {
                Log.d(TAG, "Position unstable: IoU=$iou < $minIoUThreshold")
                return false
            }
        }

        Log.d(TAG, "Position stable: All IoU values > $minIoUThreshold")
        return true
    }

    /**
     * Calculate Intersection over Union (IoU) between two document corner detections
     */
    private fun calculateIoU(corners1: DocumentCorners, corners2: DocumentCorners): Float {
        // Convert corners to axis-aligned bounding boxes for simplicity
        val bbox1 = getBoundingBox(corners1)
        val bbox2 = getBoundingBox(corners2)

        // Calculate intersection
        val intersectionLeft = maxOf(bbox1.left, bbox2.left)
        val intersectionTop = maxOf(bbox1.top, bbox2.top)
        val intersectionRight = minOf(bbox1.right, bbox2.right)
        val intersectionBottom = minOf(bbox1.bottom, bbox2.bottom)

        val intersectionArea = if (intersectionLeft < intersectionRight && intersectionTop < intersectionBottom) {
            (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        } else {
            0f
        }

        // Calculate union
        val area1 = (bbox1.right - bbox1.left) * (bbox1.bottom - bbox1.top)
        val area2 = (bbox2.right - bbox2.left) * (bbox2.bottom - bbox2.top)
        val unionArea = area1 + area2 - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    /**
     * Get axis-aligned bounding box from document corners
     */
    private fun getBoundingBox(corners: DocumentCorners): android.graphics.RectF {
        val minX = minOf(corners.topLeft.x, corners.topRight.x, corners.bottomLeft.x, corners.bottomRight.x)
        val maxX = maxOf(corners.topLeft.x, corners.topRight.x, corners.bottomLeft.x, corners.bottomRight.x)
        val minY = minOf(corners.topLeft.y, corners.topRight.y, corners.bottomLeft.y, corners.bottomRight.y)
        val maxY = maxOf(corners.topLeft.y, corners.topRight.y, corners.bottomLeft.y, corners.bottomRight.y)
        
        return android.graphics.RectF(minX, minY, maxX, maxY)
    }

    /**
     * Update stability state and notify callbacks
     */
    private fun updateStabilityState(stable: Boolean) {
        if (stable != isStable) {
            isStable = stable
            if (stable) {
                stableStartTime = System.currentTimeMillis()
                Log.d(TAG, "Document became stable")
            } else {
                stableStartTime = 0L
                countdownJob?.cancel()
                countdownJob = null
                isCapturing = false
                Log.d(TAG, "Document became unstable")
            }
            onStabilityChanged?.invoke(stable)
        }
    }

    /**
     * Start countdown for auto-capture
     */
    private fun startCountdown(corners: DocumentCorners) {
        if (isCapturing) return

        isCapturing = true
        Log.d(TAG, "Starting auto-capture countdown")

        // Notify countdown started
        onCountdownStarted?.invoke()

        // Provide haptic feedback
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }
        }

        // Provide audio feedback
        tts?.speak("Auto capture starting", TextToSpeech.QUEUE_FLUSH, null, "countdown_start")

        // Start countdown coroutine
        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                val steps = (COUNTDOWN_DURATION_MS / 100).toInt() // 10 steps over 1 second
                
                for (i in steps downTo 1) {
                    // Check if still stable
                    if (!isStable) {
                        Log.d(TAG, "Countdown cancelled - document became unstable")
                        isCapturing = false
                        return@launch
                    }

                    onCountdownTick?.invoke(i)
                    delay(100L)
                }

                // Final stability check before capture
                val finalFrame = getLatestValidFrame()
                if (finalFrame?.corners != null && isStable) {
                    Log.d(TAG, "Countdown complete - performing auto-capture")
                    performCapture(finalFrame.corners)
                } else {
                    Log.d(TAG, "Auto-capture cancelled - final stability check failed")
                    isCapturing = false
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Countdown cancelled")
                isCapturing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error during countdown", e)
                isCapturing = false
            }
        }
    }

    /**
     * Perform the actual image capture
     */
    private fun performCapture(corners: DocumentCorners) {
        Log.d(TAG, "Performing auto-capture with confidence: ${corners.confidence}%")

        // Provide final haptic feedback
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50, 100),
                    intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                    -1
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
            }
        }

        // Provide audio feedback
        tts?.speak("Capturing", TextToSpeech.QUEUE_FLUSH, null, "capture_complete")

        // Trigger capture callback
        onAutoCapture?.invoke(corners)

        // Reset state
        isCapturing = false
        
        // Brief delay before allowing next capture
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000) // 2 second cooldown
            Log.d(TAG, "Auto-capture cooldown complete")
        }
    }

    /**
     * Get recent frames from circular buffer
     */
    private fun getRecentFrames(count: Int): List<FrameData> {
        val frames = mutableListOf<FrameData>()
        val actualCount = minOf(count, bufferSize)
        
        for (i in 0 until actualCount) {
            val index = (currentBufferIndex - 1 - i + bufferSize) % bufferSize
            val frame = frameBuffer[index]
            if (frame.timestamp > 0) {
                frames.add(0, frame) // Add to beginning to maintain chronological order
            }
        }
        
        return frames
    }

    /**
     * Get the latest valid frame with document detection
     */
    private fun getLatestValidFrame(): FrameData? {
        for (i in 0 until bufferSize) {
            val index = (currentBufferIndex - 1 - i + bufferSize) % bufferSize
            val frame = frameBuffer[index]
            if (frame.corners != null && frame.timestamp > 0) {
                return frame
            }
        }
        return null
    }

    /**
     * Get current stability metrics for debugging/UI
     */
    fun getStabilityMetrics(): StabilityMetrics {
        val recentFrames = getRecentFrames(minStableFrames)
        val avgAcceleration = recentFrames.map { it.accelerationMagnitude }.average().toFloat()
        val avgConfidence = recentFrames.mapNotNull { it.corners?.confidence }.average().toFloat()
        val frameCount = recentFrames.count { it.corners != null }
        
        return StabilityMetrics(
            isStable = isStable,
            frameCount = frameCount,
            avgAcceleration = avgAcceleration,
            avgConfidence = avgConfidence,
            stableDuration = if (stableStartTime > 0) System.currentTimeMillis() - stableStartTime else 0L
        )
    }

    /**
     * Data class for stability metrics
     */
    data class StabilityMetrics(
        val isStable: Boolean,
        val frameCount: Int,
        val avgAcceleration: Float,
        val avgConfidence: Float,
        val stableDuration: Long
    )
}

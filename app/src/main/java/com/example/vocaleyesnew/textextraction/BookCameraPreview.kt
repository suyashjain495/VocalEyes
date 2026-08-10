package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Class that manages document detection for live previews
 * Throttles detection to prevent overloading the device
 */
class LiveDocumentDetector(private val context: Context) {
    private val enhancedDetector = EnhancedDocumentDetector(context)
    private val _documentCorners = MutableStateFlow<DocumentCorners?>(null)
    val documentCorners: StateFlow<DocumentCorners?> = _documentCorners
    
    private var lastProcessTime = 0L
    private var isProcessing = false
    private val processingScope = CoroutineScope(Dispatchers.Default)
    
    // Constants for detection pipeline
    companion object {
        private const val TAG = "LiveDocumentDetector"
        private const val PROCESSING_INTERVAL_MS = 200L // Process at ~5 FPS (200ms interval)
        private const val TARGET_ANALYSIS_WIDTH = 480 // Lower resolution for faster processing
        private const val TARGET_ANALYSIS_HEIGHT = 640
    }
    
    /**
     * Processes an image frame for document detection with throttling
     */
    fun processImageFrame(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle processing to ~5 FPS
        if (isProcessing || (currentTime - lastProcessTime) < PROCESSING_INTERVAL_MS) {
            imageProxy.close() // Make sure to close if we're skipping this frame
            return
        }
        
        // Convert image to bitmap on camera thread
        val originalBitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap", e)
            imageProxy.close()
            return
        }
        
        // Scale down the bitmap for faster processing
        val bitmap = try {
            Bitmap.createScaledBitmap(originalBitmap, TARGET_ANALYSIS_WIDTH, TARGET_ANALYSIS_HEIGHT, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scale bitmap", e)
            originalBitmap.recycle()
            imageProxy.close()
            return
        } finally {
            // Recycle original bitmap since we have the scaled version
            originalBitmap.recycle()
        }
        
        // Mark as processing and update timestamp
        isProcessing = true
        lastProcessTime = currentTime
        
        // Run detection on background thread
        processingScope.launch {
            try {
                // Detect document in bitmap using EnhancedDocumentDetector
                val corners = enhancedDetector.detect(bitmap)
                
                // Emit result to StateFlow
                _documentCorners.emit(corners)
                
                if (corners != null) {
                    Log.d(TAG, "Document detected with confidence: ${corners.confidence}%")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in document detection", e)
            } finally {
                // Clean up resources
                bitmap.recycle()
                isProcessing = false
                imageProxy.close()
            }
        }
    }
    
    /**
     * Resets the detector state
     */
    fun reset() {
        _documentCorners.tryEmit(null)
    }
}

@Composable
fun BookCameraPreview(
    onImageCaptureCreated: (ImageCapture) -> Unit,
    onError: (Exception) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Create detector for live document detection
    val liveDetector = remember { LiveDocumentDetector(context) }
    val documentCorners = liveDetector.documentCorners.collectAsState(null).value
    
    // Track preview size for overlay scaling
    val previewSize = remember { android.util.Pair(1080, 1920) } // Default size
    
    // Create camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Clean up executor when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val preview = remember { 
        Preview.Builder()
            .build() 
    }
    val previewView = remember { 
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build() 
    }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }
    
    // Create image analysis use case
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(480, 640))
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { imageProxy ->
                    liveDetector.processImageFrame(imageProxy)
                }
            }
    }

    LaunchedEffect(previewView) {
        try {
            val cameraProvider = context.getCameraProvider()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis // Add image analysis use case
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
            onImageCaptureCreated(imageCapture)
        } catch (exc: Exception) {
            onError(Exception("Failed to initialize camera", exc))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Document detection overlay
        DocumentDetectionOverlay(
            documentCorners = documentCorners,
            previewWidth = previewSize.first,
            previewHeight = previewSize.second,
            modifier = Modifier.fillMaxSize()
        )
    }
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener(
            {
                continuation.resume(future.get())
            },
            this.executor
        )
    }
}

private val Context.executor: Executor
    get() = ContextCompat.getMainExecutor(this) 
package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.*
import androidx.compose.ui.input.pointer.pointerInput
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun DocumentCameraPreview(
    onImageCaptureCreated: (ImageCapture) -> Unit,
    onDocumentDetected: (DocumentCorners) -> Unit,
    onError: (Exception) -> Unit,
    errorHandler: DocumentProcessingErrorHandler? = null,
    tts: TextToSpeech? = null,
    isTtsReady: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val documentDetector = remember { DocumentDetector(context) }
    
    var documentCorners by remember { mutableStateOf<DocumentCorners?>(null) }
    var previewSize by remember { mutableStateOf(Pair(1080, 1920)) } // Default camera resolution
    var autoScanEnabled by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Enhanced stability tracking with circular buffer
    val stabilityTracker = remember { DocumentStabilityTracker(context) }
    var isStable by remember { mutableStateOf(false) }
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownProgress by remember { mutableStateOf(0f) }
    var isFrozen by remember { mutableStateOf(false) }
    
    // Set callbacks for stability tracker
    LaunchedEffect(Unit) {
        stabilityTracker.setTextToSpeech(tts)
        stabilityTracker.setErrorHandler(errorHandler)
        stabilityTracker.setCallbacks(
            onStabilityChanged = { stable ->
                isStable = stable
                Log.d("DocumentCapture", "Document stability changed: $stable")
            },
            onCountdownStarted = {
                isCountingDown = true
                countdownProgress = 0f
                Log.d("DocumentCapture", "Document capture countdown started")
            },
            onCountdownTick = { tick ->
                countdownProgress = 1f - (tick / 10f) // 10 steps in countdown
                Log.d("DocumentCapture", "Countdown progress: $countdownProgress")
            },
            onAutoCapture = { corners ->
                Log.d("DocumentCapture", "Auto-capture triggered with confidence: ${corners.confidence}%")
                isFrozen = true
                onDocumentDetected(corners)
                
                // Reset state after capture (with delay)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    isFrozen = false
                    isCountingDown = false
                    countdownProgress = 0f
                }
            },
            onMotionDetected = { motion ->
                // Optional logging for motion detection debugging
                if (motion > 0.2f) {
                    Log.v("DocumentCapture", "Motion detected: $motion m/s²")
                }
            }
        )
        
        // Start tracking
        stabilityTracker.startTracking()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            stabilityTracker.stopTracking()
            cameraExecutor.shutdown()
        }
    }
    
    // Update stability tracker with new document detection results
    LaunchedEffect(documentCorners) {
        stabilityTracker.addDetectionResult(documentCorners)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        bindCameraWithDocumentDetection(
                            context = ctx,
                            cameraProvider = cameraProvider,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            cameraExecutor = cameraExecutor,
                            documentDetector = documentDetector,
                            onImageCaptureCreated = { capture ->
                                imageCapture = capture
                                stabilityTracker.setImageCapture(capture)
                                onImageCaptureCreated(capture)
                            },
                            onDocumentDetected = { corners ->
                                documentCorners = corners
                            },
                            onPreviewSizeChanged = { width, height ->
                                previewSize = Pair(width, height)
                            },
                            onError = onError
                        )
                    } catch (e: Exception) {
                        Log.e("DocumentCameraPreview", "Failed to bind camera", e)
                        onError(e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            }
        )
        
        // Enhanced document detection overlay with stability feedback
        EnhancedDocumentDetectionOverlay(
            documentCorners = documentCorners,
            previewWidth = previewSize.first,
            previewHeight = previewSize.second,
            isStable = isStable,
            isCountingDown = isCountingDown,
            countdownProgress = countdownProgress,
            isFrozen = isFrozen,
            modifier = Modifier.fillMaxSize()
        )
        
        // Double-tap gesture overlay for manual capture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            Log.d("DocumentCapture", "Double-tap detected")
                            // Manual capture on double-tap if document is stable
                            if (isStable) {
                                Log.d("DocumentCapture", "Double-tap manual capture triggered")
                                stabilityTracker.forceCapture()
                            } else {
                                Log.w("DocumentCapture", "Double-tap ignored - document not stable")
                            }
                        }
                    )
                }
        )
        
        // Enhanced status indicator
        EnhancedStatusIndicator(
            documentCorners = documentCorners,
            isStable = isStable,
            isCountingDown = isCountingDown,
            isFrozen = isFrozen,
            stabilityMetrics = stabilityTracker.getStabilityMetrics(),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Temporary manual capture button for testing
        // Enhanced manual capture buttons
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Stability metrics display
            val metrics = stabilityTracker.getStabilityMetrics()
            Text(
                text = "Stable: ${if (isStable) "YES" else "NO"} | ${metrics.frameCount}/5 frames | Motion: ${metrics.avgAcceleration.toInt()} m/s²",
                color = if (isStable) Color.Green else Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 50.dp)
            )
            
            // Force capture button
            Button(
                onClick = {
                    Log.d("DocumentCapture", "Force capture button pressed")
                    stabilityTracker.forceCapture()
                },
                modifier = Modifier.padding(end = 110.dp)
            ) {
                Text("Force Capture")
            }
            
            // Test capture button
            Button(
                onClick = {
                    Log.d("DocumentCapture", "Manual test button pressed")
                    documentCorners?.let { corners ->
                        Log.d("DocumentCapture", "Test capture: corners confidence = ${corners.confidence}%")
                        onDocumentDetected(corners)
                    } ?: run {
                        Log.w("DocumentCapture", "Test capture: no document detected")
                        // Create dummy corners for testing
                        val testCorners = DocumentCorners(
                            topLeft = android.graphics.PointF(100f, 100f),
                            topRight = android.graphics.PointF(500f, 100f),
                            bottomLeft = android.graphics.PointF(100f, 400f),
                            bottomRight = android.graphics.PointF(500f, 400f),
                            confidence = 85f
                        )
                        onDocumentDetected(testCorners)
                    }
                }
            ) {
                Text("Test Capture")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedStatusIndicator(
    documentCorners: DocumentCorners?,
    isStable: Boolean,
    isCountingDown: Boolean,
    isFrozen: Boolean,
    stabilityMetrics: DocumentStabilityTracker.StabilityMetrics,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        isFrozen -> "DOCUMENT CAPTURED"
        isCountingDown -> "CAPTURING..."
        isStable -> "READY TO CAPTURE"
        documentCorners == null -> "Position document in frame"
        documentCorners.confidence < 30f -> "Move closer to document"
        documentCorners.confidence < 60f -> "Align document edges"
        documentCorners.confidence < 80f -> "Hold steady..."
        else -> "Document detected"
    }
    
    val statusColor = when {
        isFrozen -> Color.Cyan
        isCountingDown -> Color.Green
        isStable -> Color.Green
        documentCorners == null -> Color.White
        documentCorners.confidence < 30f -> Color.Red
        documentCorners.confidence < 60f -> Color(0xFFFFA500) // Orange
        documentCorners.confidence < 80f -> Color.Yellow
        else -> Color.Green
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    
    // Show confidence percentage and stability info if document is detected
    documentCorners?.let { corners ->
        if (corners.confidence > 0f) {
            Card(
                modifier = modifier.offset(y = 40.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "${corners.confidence.roundToInt()}% | ${stabilityMetrics.frameCount}/5 stable",
                    color = statusColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }

    // Show motion indicator
    if (isStable || isCountingDown) {
        Card(
            modifier = modifier.offset(y = 80.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Green.copy(alpha = 0.6f)
            )
        ) {
            Text(
                text = "Motion: ${stabilityMetrics.avgAcceleration.toInt()} m/s²",
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentStatusIndicator(
    documentCorners: DocumentCorners?,
    autoScanEnabled: Boolean,
    hasAnnouncedReady: Boolean = false,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        documentCorners == null -> "Position document in frame"
        documentCorners.confidence < 30f -> "Move closer to document"
        documentCorners.confidence < 60f -> "Align document edges"
        documentCorners.confidence < 80f -> "Hold steady..."
        hasAnnouncedReady && autoScanEnabled -> "Double-tap to capture or wait for auto-scan"
        autoScanEnabled -> "Auto-capturing in 1s..."
        else -> "Document ready!"
    }
    
    val statusColor = when {
        documentCorners == null -> Color.White
        documentCorners.confidence < 30f -> Color.Red
        documentCorners.confidence < 60f -> Color(0xFFFFA500) // Orange
        documentCorners.confidence < 80f -> Color.Yellow
        else -> Color.Green
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    
    // Show confidence percentage if document is detected
    documentCorners?.let { corners ->
        if (corners.confidence > 0f) {
            Card(
                modifier = modifier.offset(y = 40.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "${corners.confidence.roundToInt()}% match",
                    color = statusColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun bindCameraWithDocumentDetection(
    context: Context,
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: ExecutorService,
    documentDetector: DocumentDetector,
    onImageCaptureCreated: (ImageCapture) -> Unit,
    onDocumentDetected: (DocumentCorners?) -> Unit,
    onPreviewSizeChanged: (Int, Int) -> Unit,
    onError: (Exception) -> Unit
) {
    val preview = Preview.Builder()
        .build()
        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
    
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()
    
    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetResolution(android.util.Size(640, 480)) // Use lower resolution for analysis
        .build()
        .apply {
            setAnalyzer(cameraExecutor) { imageProxy ->
                processImageForDocumentDetection(
                    imageProxy,
                    documentDetector,
                    onDocumentDetected,
                    onPreviewSizeChanged
                )
            }
        }
    
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()
    
    try {
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )
        
        onImageCaptureCreated(imageCapture)
    } catch (e: Exception) {
        Log.e("DocumentCameraPreview", "Camera binding failed", e)
        onError(e)
    }
}

private var lastProcessTime = 0L
private var isProcessing = false
private const val PROCESSING_INTERVAL_MS = 1000L // Process only every 1 second to prevent buffer overrun

private fun processImageForDocumentDetection(
    imageProxy: ImageProxy,
    documentDetector: DocumentDetector,
    onDocumentDetected: (DocumentCorners?) -> Unit,
    onPreviewSizeChanged: (Int, Int) -> Unit
) {
    var bitmap: android.graphics.Bitmap? = null
    
    try {
        // Update preview size (do this every frame as it's lightweight)
        onPreviewSizeChanged(imageProxy.width, imageProxy.height)
        
        val currentTime = System.currentTimeMillis()
        
        // Throttle document detection to prevent ANR
        if (isProcessing || (currentTime - lastProcessTime) < PROCESSING_INTERVAL_MS) {
            return // Skip this frame
        }
        
        // Convert ImageProxy to Bitmap BEFORE launching background thread
        // This must happen on the same thread that has access to the ImageProxy
        bitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e("DocumentCameraPreview", "Failed to convert ImageProxy to Bitmap", e)
            return
        }
        
        // Bitmap is now safe to use in background thread
        if (bitmap == null) {
            Log.e("DocumentCameraPreview", "Bitmap conversion returned null")
            return
        }
        
        isProcessing = true
        lastProcessTime = currentTime
        
        // Process on background thread with the already-converted bitmap
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("DocumentCameraPreview", "Starting document detection on background thread")
                
                // Detect document in the bitmap
                val documentCorners = documentDetector.detect(bitmap)
                
                // Notify the UI about the detected document on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        onDocumentDetected(documentCorners)
                    } catch (e: Exception) {
                        Log.e("DocumentCameraPreview", "Error in UI callback", e)
                    }
                }
                
                Log.d("DocumentCameraPreview", "Document detection completed")
                
            } catch (e: Exception) {
                Log.e("DocumentCameraPreview", "Error in background document detection", e)
                
                // Reset to null on error so UI knows detection failed
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        onDocumentDetected(null)
                    } catch (e2: Exception) {
                        Log.e("DocumentCameraPreview", "Error in error callback", e2)
                    }
                }
            } finally {
                // Clean up resources
                try {
                    bitmap?.recycle()
                } catch (e: Exception) {
                    Log.e("DocumentCameraPreview", "Error recycling bitmap", e)
                }
                isProcessing = false
                Log.d("DocumentCameraPreview", "Background processing cleanup completed")
            }
        }
        
    } catch (e: Exception) {
        Log.e("DocumentCameraPreview", "Error in processImageForDocumentDetection outer scope", e)
        
        // Clean up bitmap if we created it but failed to process
        try {
            bitmap?.recycle()
        } catch (e2: Exception) {
            Log.e("DocumentCameraPreview", "Error recycling bitmap in catch", e2)
        }
        
        isProcessing = false
    } finally {
        // Always close the ImageProxy to free buffer
        try {
            imageProxy.close()
        } catch (e: Exception) {
            Log.e("DocumentCameraPreview", "Error closing ImageProxy", e)
        }
    }
}

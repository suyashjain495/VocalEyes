package com.example.vocaleyesnew.objectdetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.vocaleyesnew.accessibility.AppCommand
import com.example.vocaleyesnew.accessibility.BaseAccessibleActivity
import com.example.vocaleyesnew.accessibility.UserGuidanceManager
import com.example.vocaleyesnew.accessibility.backgroundTapHandler
import com.example.vocaleyesnew.objectdetection.Constants.LABELS_PATH
import com.example.vocaleyesnew.objectdetection.Constants.MODEL_PATH
import com.example.vocaleyesnew.ui.theme.BoundingBoxColor
import com.example.vocaleyesnew.ui.theme.YOLOv8Theme

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetectionActivity : BaseAccessibleActivity() {
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService
    private var isDetectorInitialized = false

    // State variables
    private var errorMessage by mutableStateOf<String?>(null)
    private val detectedObjects = mutableMapOf<String, Long>()
    private val objectTimeoutInterval = 5000L
    private var boundingBoxes by mutableStateOf<List<BoundingBox>>(emptyList())
    private var inferenceTime by mutableStateOf(0L)
    private var isDetectionPaused by mutableStateOf(false)
    private var lastAnnouncementTime = 0L
    private val announcementInterval = 3000L // 3 seconds between announcements

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speak("Camera permission granted")
        } else {
            speak("Camera permission is required for object detection")
        }
    }

    override fun onAccessibilityReady() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize detector
        try {
            detector = Detector(
                context = applicationContext,
                modelPath = MODEL_PATH,
                labelPath = LABELS_PATH,
                detectorListener = object : Detector.DetectorListener {
                    override fun onEmptyDetect() {
                        val currentTime = System.currentTimeMillis()
                        val iterator = detectedObjects.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (currentTime - entry.value > objectTimeoutInterval) {
                                iterator.remove()
                            }
                        }
                    }

                    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                        this@ObjectDetectionActivity.boundingBoxes = boundingBoxes
                        this@ObjectDetectionActivity.inferenceTime = inferenceTime

                        val currentTime = System.currentTimeMillis()
                        
                        // Update detected objects
                        boundingBoxes.forEach { box ->
                            detectedObjects[box.clsName] = currentTime
                        }

                        // Announce detected objects
                        if (currentTime - lastAnnouncementTime >= announcementInterval && !isDetectionPaused) {
                            val objectsToAnnounce = boundingBoxes.map { it.clsName }.distinct()
                            if (objectsToAnnounce.isNotEmpty()) {
                                val announcement = if (objectsToAnnounce.size == 1) {
                                    "I see ${objectsToAnnounce.first()}"
                                } else {
                                    "I see ${objectsToAnnounce.dropLast(1).joinToString(", ")} and ${objectsToAnnounce.last()}"
                                }
                                speak(announcement)
                                lastAnnouncementTime = currentTime
                            }
                        }

                        // Remove old detections
                        val iterator = detectedObjects.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (currentTime - entry.value > objectTimeoutInterval) {
                                iterator.remove()
                            }
                        }
                    }
                }
            )
            
            try {
                detector.setup()
                isDetectorInitialized = true
            } catch (e: Exception) {
                Log.e("Detector", "Failed to setup detector", e)
                errorMessage = "Failed to initialize detector: ${e.message}"
            }
        } catch (e: Exception) {
            Log.e("Detector", "Failed to create detector", e)
            errorMessage = "Failed to create detector: ${e.message}"
        }

        // Setup UI
        setContent {
            YOLOv8Theme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .backgroundTapHandler(this@ObjectDetectionActivity),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show error message if there's an error
                    errorMessage?.let { error ->
                        ErrorScreen(error = error)
                        return@Surface
                    }

                    var hasPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    if (!hasPermission) {
                        PermissionRequest(
                            onRequestPermission = {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                hasPermission = ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            }
                        )
                    } else {
                        CameraScreen()
                    }
                }
            }
        }
    }
    
    override fun onTtsReady() {
        val welcomeMessage = guidanceManager.getWelcomeMessage(
            UserGuidanceManager.FEATURE_OBJECT_DETECTION,
            "Object Detection"
        )
        
        if (guidanceManager.shouldShowGuidance(UserGuidanceManager.FEATURE_OBJECT_DETECTION)) {
            speak(welcomeMessage)
            guidanceManager.markGuidanceShown(UserGuidanceManager.FEATURE_OBJECT_DETECTION)
        } else {
            speak("Object Detection ready. Single tap for voice commands.")
        }
    }

    override fun onVoiceCommand(command: String) {
        when (command.lowercase().trim()) {
            "pause", "stop" -> {
                if (!isDetectionPaused) {
                    toggleDetection()
                }
            }
            "play", "start", "resume" -> {
                if (isDetectionPaused) {
                    toggleDetection()
                }
            }
            "what do you see", "describe" -> {
                announceCurrentObjects()
            }
            else -> {
                speak("Object detection commands: pause, play, resume, or what do you see. Say home to return.")
            }
        }
    }

    override fun parseScreenSpecificCommand(command: String): AppCommand? {
        return when (command.lowercase().trim()) {
            "pause", "stop" -> AppCommand.ScreenSpecific("pause")
            "play", "start", "resume" -> AppCommand.ScreenSpecific("resume")
            "what do you see", "describe" -> AppCommand.ScreenSpecific("describe")
            else -> null
        }
    }

    override fun getScreenSpecificHelp(): String {
        return "Object detection commands: pause, play, resume, describe what you see"
    }

    override fun handleDoubleTapEvent(x: Float, y: Float) {
        // Double tap on camera screen toggles detection
        toggleDetection()
    }

    private fun toggleDetection() {
        isDetectionPaused = !isDetectionPaused
        if (isDetectionPaused) {
            speak("Detection paused")
            lastAnnouncementTime = System.currentTimeMillis()
        } else {
            speak("Detection resumed")
        }
    }

    private fun announceCurrentObjects() {
        if (boundingBoxes.isNotEmpty()) {
            val objectsToAnnounce = boundingBoxes.map { it.clsName }.distinct()
            val announcement = if (objectsToAnnounce.size == 1) {
                "I can see ${objectsToAnnounce.first()}"
            } else {
                "I can see ${objectsToAnnounce.dropLast(1).joinToString(", ")} and ${objectsToAnnounce.last()}"
            }
            speak(announcement)
        } else {
            speak("I don't see any objects right now")
        }
    }

    @Composable
    fun ErrorScreen(error: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    @Composable
    fun PermissionRequest(onRequestPermission: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera permission is required for object detection",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }

    @Composable
    fun CameraScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .build()
                        )
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            if (!isDetectionPaused && isDetectorInitialized) {
                                val bitmapBuffer = Bitmap.createBitmap(
                                    imageProxy.width,
                                    imageProxy.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                
                                imageProxy.use { 
                                    bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
                                }

                                val matrix = Matrix().apply {
                                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                }

                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                                    matrix, true
                                )

                                try {
                                    detector.detect(rotatedBitmap)
                                } finally {
                                    rotatedBitmap.recycle()
                                    bitmapBuffer.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CameraX", "Analysis error", e)
                            errorMessage = "Detection error: ${e.message}"
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    try {
                        cameraProvider?.unbindAll()
                        camera = cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Log.e("CameraX", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            // Overlay for bounding boxes
            Canvas(modifier = Modifier.fillMaxSize()) {
                boundingBoxes.forEach { box ->
                    drawBoundingBox(box)
                }
            }

            // Inference time display
            Text(
                text = "${inferenceTime}ms",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(4.dp)
            )

            // Control button
            Button(
                onClick = { toggleDetection() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(if (isDetectionPaused) "Resume" else "Pause")
            }
        }
    }

    private fun DrawScope.drawBoundingBox(box: BoundingBox) {
        // Draw bounding box
        drawRect(
            color = BoundingBoxColor,
            topLeft = Offset(box.x1 * size.width, box.y1 * size.height),
            size = Size(
                (box.x2 - box.x1) * size.width,
                (box.y2 - box.y1) * size.height
            ),
            style = Stroke(width = 8f)
        )

        // Draw label background and text
        val textPaint = android.graphics.Paint().apply {
            color = Color.White.toArgb()
            textSize = 50f
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(box.clsName, 0, box.clsName.length, textBounds)

        drawRect(
            color = Color.Black,
            topLeft = Offset(box.x1 * size.width, box.y1 * size.height),
            size = Size(
                textBounds.width().toFloat() + 16f,
                textBounds.height().toFloat() + 16f
            )
        )

        drawContext.canvas.nativeCanvas.drawText(
            box.clsName,
            box.x1 * size.width + 8f,
            box.y1 * size.height + textBounds.height() + 8f,
            textPaint
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::detector.isInitialized) {
            detector.clear()
        }
    }
}

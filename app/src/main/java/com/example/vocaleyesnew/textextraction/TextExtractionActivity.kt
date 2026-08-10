package com.example.vocaleyesnew.textextraction

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.example.vocaleyesnew.accessibility.singleTapOnlyBackgroundHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.vocaleyesnew.accessibility.AppCommand
import com.example.vocaleyesnew.accessibility.BaseAccessibleActivity
import com.example.vocaleyesnew.accessibility.UserGuidanceManager
import com.example.vocaleyesnew.ui.theme.VocalEyesNewTheme
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.facenet_android.FaceMainActivity
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.EnhancedMainActivity
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class TextExtractionActivity : BaseAccessibleActivity() {
    
    private lateinit var textRecognizer: TextRecognizer
    private var currentState by mutableStateOf<BookReadingState>(BookReadingState.Scanning)
    private var extractedText by mutableStateOf("")
    private var capturedImage by mutableStateOf<Bitmap?>(null)
    private var currentStatistics: TextStatistics? = null
    private var imageCapture: ImageCapture? = null
    
    // Timer state
    private var countdownSeconds by mutableStateOf(7)
    private var isCountingDown by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("TextExtraction", "Camera permission granted")
        } else {
            speak("Camera permission is required for text reading. Please grant permission to continue.") {
                finish()
            }
        }
    }

    override fun onAccessibilityReady() {
        // Initialize text recognizer
        textRecognizer = TextRecognizer(this) { recognizedText ->
            // Handle text recognition results
            extractedText = recognizedText
        }
        
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Set up the UI
        setContent {
            VocalEyesNewTheme {
                AutoTextReadingScreen(
                    activity = this@TextExtractionActivity,
                    currentState = currentState,
                    extractedText = extractedText,
                    capturedImage = capturedImage,
                    currentStatistics = currentStatistics,
                    countdownSeconds = countdownSeconds,
                    isCountingDown = isCountingDown,
                    onImageCaptureReady = { capture ->
                        imageCapture = capture
                    }
                )
            }
        }
    }

    override fun onTtsReady() {
        // Disable guidance - mark as shown to prevent future prompts
        guidanceManager.markGuidanceShown(UserGuidanceManager.FEATURE_TEXT_EXTRACTION)
        // Start auto-capture countdown after TTS is ready
        startAutoCapture()
    }
    
    /**
     * Start automatic capture countdown
     */
    fun startAutoCapture() {
        if (currentState != BookReadingState.Scanning) return
        
        isCountingDown = true
        countdownSeconds = 7
        
        accessibilityScope.launch {
            repeat(7) { i ->
                countdownSeconds = 7 - i
                delay(1000)
            }
            
            // After countdown, capture
            if (currentState == BookReadingState.Scanning) {
                isCountingDown = false
                performAutoCapture()
            }
        }
    }
    
    /**
     * Reset state and restart auto-capture
     */
    fun resetAndRestart() {
        currentState = BookReadingState.Scanning
        extractedText = ""
        capturedImage = null
        currentStatistics = null
        isCountingDown = false
        countdownSeconds = 7
        startAutoCapture()
    }

    override fun onVoiceCommand(command: String) {
        when (command.lowercase().trim()) {
            // Text reading control commands
            "restart", "start again", "restart reading", "new scan", "scan again" -> {
                speak("Restarting text reading") {
                    resetAndRestart()
                }
            }
            "stop reading", "stop", "pause", "pause reading" -> {
                speak("Stopping text reading") {
                    stopSpeaking()
                }
            }
            "read again", "repeat", "repeat text", "read text" -> {
                if (extractedText.isNotEmpty()) {
                    speak("Reading text again") {
                        speakContent(extractedText) {
                            speak("Finished reading.")
                        }
                    }
                } else {
                    speak("No text available to read. Please scan a document first.")
                }
            }
            "capture now", "capture", "take photo", "scan now" -> {
                if (currentState == BookReadingState.Scanning && isCountingDown) {
                    speak("Capturing immediately") {
                        isCountingDown = false
                        performAutoCapture()
                    }
                } else {
                    speak("Starting new scan") {
                        resetAndRestart()
                    }
                }
            }
            
            // Navigation commands to other features
            "object detection", "objects", "detect objects", "object recognition" -> {
                navigateToObjectDetection()
            }
            "face recognition", "faces", "face detection", "detect faces" -> {
                navigateToFaceRecognition()
            }
            "chat", "ai chat", "assistant", "talk" -> {
                navigateToChat()
            }
            "home", "main menu", "menu" -> {
                navigateToHome()
            }
            "back", "return", "previous" -> {
                finish()
            }
            
            else -> {
                // Provide comprehensive help
                val helpMessage = buildString {
                    append("Available commands: ")
                    append("restart reading, stop reading, read again, capture now, ")
                    append("object detection, face recognition, chat, home, or back.")
                }
                speak(helpMessage)
            }
        }
    }
    
    override fun parseScreenSpecificCommand(command: String): AppCommand? {
        return when (command.lowercase().trim()) {
            "restart", "start again", "restart reading" -> AppCommand.ScreenSpecific("restart")
            "stop", "pause", "stop reading" -> AppCommand.ScreenSpecific("stop")
            "read again", "repeat", "repeat text" -> AppCommand.ScreenSpecific("repeat")
            "capture", "capture now", "scan now" -> AppCommand.ScreenSpecific("capture")
            else -> null
        }
    }
    
    override fun getScreenSpecificHelp(): String {
        return "Text reading commands: restart reading, stop reading, read again, capture now"
    }

    override fun handleDoubleTapEvent(x: Float, y: Float) {
        // Double tap on background should do nothing (like home page)
        // Only UI buttons should respond to double taps
    }

    override fun onVoiceListeningStarted() {
        Log.d("TextExtraction", "Voice listening started")
    }

    /**
     * Perform automatic text capture (triggered by timer)
     */
    private fun performAutoCapture() {
        if (currentState != BookReadingState.Scanning) return
        
        currentState = BookReadingState.Capturing
        speak("Capturing image...")
        
        imageCapture?.let { capture ->
            capture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        accessibilityScope.launch {
                            try {
                                val bitmap = image.toBitmap()
                                capturedImage = bitmap
                                
                                currentState = BookReadingState.Processing
                                speak("Processing image for text extraction...")
                                
                                // Perform OCR
                                textRecognizer.recognizeText(bitmap) { text ->
                                    accessibilityScope.launch {
                                        extractedText = text
                                        
                                        val stats = TextStatistics(
                                            confidence = 85,
                                            wordCount = text.split(" ").filter { it.isNotBlank() }.size,
                                            characterCount = text.length,
                                            lineCount = text.lines().size,
                                            processingTimeMs = 1500
                                        )
                                        
                                        currentStatistics = stats
                                        currentState = BookReadingState.Reading(text, stats)
                                        
                                        // Announce and start reading
                                        if (text.isNotEmpty()) {
                                            speak("Text extracted successfully. Reading now...") {
                                                speakContent(text) {
                                                    speak("Finished reading.")
                                                }
                                            }
                                        } else {
                                            speak("No text found in the image.")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("TextExtraction", "Error processing image", e)
                                currentState = BookReadingState.Error("Failed to process image: ${e.message}")
                                speak("Error processing image.")
                            } finally {
                                image.close()
                            }
                        }
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("TextExtraction", "Image capture failed", exception)
                        currentState = BookReadingState.Error("Failed to capture image: ${exception.message}")
                        speak("Failed to capture image.")
                    }
                }
            )
        } ?: run {
            speak("Camera not ready.")
            currentState = BookReadingState.Error("Camera not initialized")
        }
    }

    // Navigation methods to other features
    private fun navigateToObjectDetection() {
        speak("Opening object detection") {
            val intent = Intent(this, ObjectDetectionActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToFaceRecognition() {
        speak("Opening face recognition") {
            val intent = Intent(this, FaceMainActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToChat() {
        speak("Opening AI chat") {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun navigateToHome() {
        speak("Going to main menu") {
            val intent = Intent(this, EnhancedMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TextExtraction", "TextExtractionActivity destroyed")
    }
}

/**
 * Auto Text Reading Screen with camera, timer, and results display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTextReadingScreen(
    activity: TextExtractionActivity,
    currentState: BookReadingState,
    extractedText: String,
    capturedImage: Bitmap?,
    currentStatistics: TextStatistics?,
    countdownSeconds: Int,
    isCountingDown: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Auto Text Reading",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        activity.finish()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .singleTapOnlyBackgroundHandler(activity)
        ) {
            when (currentState) {
                BookReadingState.Scanning -> {
                    // Real camera preview with countdown timer
                    CameraWithCountdown(
                        countdownSeconds = countdownSeconds,
                        isCountingDown = isCountingDown,
                        onImageCaptureReady = onImageCaptureReady
                    )
                }
                
                BookReadingState.Capturing, BookReadingState.Processing -> {
                    ProcessingIndicator(
                        message = when (currentState) {
                            BookReadingState.Capturing -> "Capturing image..."
                            BookReadingState.Processing -> "Extracting text..."
                            else -> "Processing..."
                        }
                    )
                }
                
                is BookReadingState.Reading -> {
                    // Display both captured image and extracted text
                    TextAndImageDisplayScreen(
                        text = extractedText,
                        image = capturedImage,
                        statistics = currentStatistics ?: TextStatistics()
                    )
                }
                
                is BookReadingState.Error -> {
                    ErrorScreen(
                        message = (currentState as BookReadingState.Error).message,
                        onRetry = {
                            // Reset state and restart
                            activity.resetAndRestart()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreviewPlaceholder(
    onManualCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera Preview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Text will be captured and read automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ProcessingIndicator(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TextDisplayScreen(
    text: String,
    statistics: TextStatistics
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Statistics card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Text Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${statistics.wordCount} words • ${statistics.characterCount} characters • ${statistics.lineCount} lines",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Confidence: ${statistics.confidence}% • Processing: ${statistics.processingTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Extracted text
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Extracted Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = "No text extracted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Instructions card removed - no manual commands needed for automatic text reading
    }
}

@Composable
fun CameraWithCountdown(
    countdownSeconds: Int,
    isCountingDown: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Real camera preview using existing DocumentCameraPreview
        DocumentCameraPreview(
            onImageCaptureCreated = onImageCaptureReady,
            onDocumentDetected = { /* Auto-capture is handled by timer */ },
            onError = { error ->
                Log.e("CameraWithCountdown", "Camera error: ${error.message}")
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Countdown timer overlay
        if (isCountingDown) {
            CountdownOverlay(
                seconds = countdownSeconds,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun CountdownOverlay(
    seconds: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdown")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .size(120.dp)
            .background(
                Color.Black.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .border(
                width = 4.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seconds.toString(),
            color = Color.White,
            fontSize = (32 * scale).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TextAndImageDisplayScreen(
    text: String,
    image: Bitmap?,
    statistics: TextStatistics
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Captured image display
        if (image != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Captured Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        
        // Statistics card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Text Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${statistics.wordCount} words • ${statistics.characterCount} characters • ${statistics.lineCount} lines",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Confidence: ${statistics.confidence}% • Processing: ${statistics.processingTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Extracted text
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Extracted Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "No text extracted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}

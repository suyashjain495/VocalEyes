package com.example.vocaleyesnew.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vocaleyesnew.accessibility.singleTapOnlyBackgroundHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun BlindModeScreen(
    activity: NavigationActivity
) {
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val context = LocalContext.current
    LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var sessionStarted by remember { mutableStateOf(true) }
    var analysisResult by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("Initializing...") }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    var lastSpokenIndex by remember { mutableStateOf(0) }
    var lastProcessedTimestamp by remember { mutableStateOf(0L) }
    var responseCount by remember { mutableStateOf(0) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    val frameInterval = 2000 // Process a frame every 2 seconds for better safety
    val scrollState = rememberScrollState()

    LaunchedEffect(context) {
        tts.value = TextToSpeech(context) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.value?.language = Locale.US
                tts.value?.setSpeechRate(1.5f)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            tts.value?.stop()
            tts.value?.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .singleTapOnlyBackgroundHandler(activity)
    ) {
        if (hasPermission) {
            if (sessionStarted) {
                CameraPreviewWithAnalysis { imageProxy ->
                    val currentTimestamp = System.currentTimeMillis()
                    if (currentTimestamp - lastProcessedTimestamp >= frameInterval) {
                        coroutineScope.launch {
                            val bitmap = imageProxy.toBitmap()
                            if (bitmap != null) {
                                // Reset for new response
                                analysisResult = ""
                                lastSpokenIndex = 0
                                
                                sendFrameToGeminiAI(bitmap, { partialResult ->
                                    // Clean JSON formatting and special characters
                                    val cleanedResult = partialResult
                                        .replace(Regex("[{}\\[\\]\",:]"), "") // Remove JSON characters
                                        .replace(Regex("\\n|\\t|\\r"), " ") // Remove escape characters
                                        .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
                                        .trim()
                                    
                                    if (cleanedResult.isNotEmpty()) {
                                        analysisResult += " $cleanedResult"
                                        displayText = analysisResult // Always show on screen
                                        
                                        // Increment response counter
                                        responseCount++
                                        
                                        // Check if response is urgent
                                        val isUrgent = cleanedResult.lowercase().let { text ->
                                            text.contains("stop") ||
                                            text.contains("immediately") ||
                                            text.contains("danger") ||
                                            text.contains("very close") ||
                                            text.contains("1 meter") ||
                                            text.contains("move now") ||
                                            text.contains("truck") ||
                                            text.contains("car approaching") ||
                                            text.contains("stairs")
                                        }
                                        
                                        // Speak if urgent OR every 3rd response OR if not currently speaking
                                        val shouldSpeak = isUrgent || (responseCount % 3 == 0) || !isTtsSpeaking
                                        
                                        if (shouldSpeak) {
                                            val newText = analysisResult.substring(lastSpokenIndex)
                                            
                                            if (isUrgent && isTtsSpeaking) {
                                                // For urgent messages, interrupt current speech
                                                tts.value?.stop()
                                                isTtsSpeaking = false
                                            }
                                            
                                            if (!isTtsSpeaking || isUrgent) {
                                                isTtsSpeaking = true
                                                val utteranceId = "nav_${System.currentTimeMillis()}"
                                                
                                                // Set up completion listener
                                                tts.value?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                                    override fun onStart(utteranceId: String?) {
                                                        isTtsSpeaking = true
                                                    }
                                                    override fun onDone(utteranceId: String?) {
                                                        isTtsSpeaking = false
                                                    }
                                                    override fun onError(utteranceId: String?) {
                                                        isTtsSpeaking = false
                                                    }
                                                })
                                                
                                                tts.value?.speak(newText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                                                lastSpokenIndex = analysisResult.length
                                            }
                                        }
                                    }
                                }, { error ->
                                    if (isTtsSpeaking) {
                                        tts.value?.stop()
                                        isTtsSpeaking = false
                                    }
                                    displayText = "Error: $error"
                                    isTtsSpeaking = true
                                    tts.value?.speak("Navigation error occurred", TextToSpeech.QUEUE_FLUSH, null, "error_${System.currentTimeMillis()}")
                                })
                                lastProcessedTimestamp = currentTimestamp
                            }
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                (context as Activity),
                arrayOf(Manifest.permission.CAMERA),
                1
            )
        }

        // Text overlay at the bottom with scrolling
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp)
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp)
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Left,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            )
        }
        
        // Voice listening indicator will be handled by BaseAccessibleActivity
    }
} 
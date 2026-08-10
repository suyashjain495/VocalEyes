package com.example.vocaleyesnew.facenet_android.presentation.screens.detect_screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.vocaleyesnew.R
import com.example.vocaleyesnew.VoiceRecognitionManager
import com.example.vocaleyesnew.facenet_android.presentation.components.AppAlertDialog
import com.example.vocaleyesnew.facenet_android.presentation.components.DelayedVisibility
import com.example.vocaleyesnew.facenet_android.presentation.components.FaceDetectionOverlay
import com.example.vocaleyesnew.facenet_android.presentation.components.createAlertDialog
import com.example.vocaleyesnew.facenet_android.presentation.theme.FaceNetAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// Global camera facing state that can be accessed from outside
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
private val cameraPermissionStatus = mutableStateOf(false)
private lateinit var cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

// Global function to flip the camera that can be called from other composables or activities
fun flipCamera() {
    if (cameraFacing.intValue == CameraSelector.LENS_FACING_BACK) {
        cameraFacing.intValue = CameraSelector.LENS_FACING_FRONT
    } else {
        cameraFacing.intValue = CameraSelector.LENS_FACING_BACK
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectScreen(onOpenFaceListClick: (() -> Unit)) {
    // Get the context for voice recognition
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Double tap detection variables for face list button
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val doubleTapThreshold = 300L // Double tap threshold in milliseconds
    
    // Voice recognition state
    var isVoiceListening by remember { mutableStateOf(false) }
    
    // Create or remember the voice recognition manager for permissions
    val voiceRecognitionManager = remember { VoiceRecognitionManager(context) }
    
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(),
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        // Face list button with double tap detection
                        IconButton(
                            onClick = { 
                                // Check for double tap
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastTapTime < doubleTapThreshold) {
                                    // Double tap detected - navigate to face list
                                    onOpenFaceListClick()
                                }
                                lastTapTime = currentTime
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Open Face List (Double Tap)"
                            )
                        }
                        
                        // Camera switch button with single tap
                        IconButton(
                            onClick = { flipCamera() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            // Main content with double tap to flip camera and single tap for voice
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Double tap on main screen area flips the camera
                                flipCamera()
                            },
                            onTap = {
                                // Single tap - start voice recognition
                                if (!isVoiceListening) {
                                    isVoiceListening = true
                                    
                                    // Start listening immediately
                                    voiceRecognitionManager.speak("Voice recognition activated") {
                                        voiceRecognitionManager.startListening()
                                    }
                                    
                                    // Reset voice listening state after timeout
                                    scope.launch {
                                        delay(7000) // 7 seconds timeout
                                        isVoiceListening = false
                                        voiceRecognitionManager.stopListening()
                                    }
                                }
                            }
                        )
                    }
            ) {
                ScreenUI()
            }
        }
    }
}

@Composable
private fun ScreenUI() {
    val viewModel: DetectScreenViewModel = koinViewModel()
    Box {
        Camera(viewModel)
        DelayedVisibility(viewModel.getNumPeople() > 0) {
            val metrics by remember{ viewModel.faceDetectionMetricsState }
            Column {
                Text(
                    text = "Recognition on ${viewModel.getNumPeople()} face(s)",
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
                metrics?.let {
                    Text(
                        text = "face detection: ${it.timeFaceDetection} ms" +
                                "\nface embedding: ${it.timeFaceEmbedding} ms" +
                                "\nvector search: ${it.timeVectorSearch} ms\n" +
                                "spoof detection: ${it.timeFaceSpoofDetection} ms",
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        DelayedVisibility(viewModel.getNumPeople() == 0L) {
            Text(
                text = "No images in database",
                color = Color.White,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Blue, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
        AppAlertDialog()
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun Camera(viewModel: DetectScreenViewModel) {
    val context = LocalContext.current
    
    cameraPermissionStatus.value =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val cameraFacing by remember { cameraFacing }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create or remember voice recognition manager for permissions
    val voiceRecognitionManager = remember { VoiceRecognitionManager(context) }

    cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                cameraPermissionStatus.value = true
                voiceRecognitionManager.speak("Camera permission granted")
            } else {
                camaraPermissionDialog()
            }
        }

    DelayedVisibility(cameraPermissionStatus.value) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { FaceDetectionOverlay(lifecycleOwner, context, viewModel) },
                update = { it.initializeCamera(cameraFacing) }
            )
        }
    }
    DelayedVisibility(!cameraPermissionStatus.value) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Allow Camera Permissions\nThe app cannot work without the camera permission.",
                textAlign = TextAlign.Center
            )
            
            // Camera permission button with direct activation
            Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = "Allow")
            }
        }
    }
}

private fun camaraPermissionDialog() {
    createAlertDialog(
        "Camera Permission",
        "The app couldn't function without the camera permission.",
        "ALLOW",
        "CLOSE",
        onPositiveButtonClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onNegativeButtonClick = {
            // Close the dialog
        }
    )
}

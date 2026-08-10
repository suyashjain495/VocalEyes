package com.example.vocaleyesnew.voice

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Composable that adds single-tap and double-tap voice recognition to any screen.
 * 
 * This composable:
 * - Adds a transparent tap area that covers the entire screen
 * - Detects both single and double taps with configurable behavior
 * - Shows a floating action button when voice recognition is active
 * - Handles the lifecycle of voice service registration
 * - Provides haptic feedback for better user experience
 * - Can be overlaid on any existing content
 * 
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // Your existing screen content
 *     MyScreenContent()
 *     
 *     // Add voice recognition overlay
 *     SingleTapVoiceHandler(
 *         featureId = "my_feature",
 *         onVoiceCommand = { command -> handleCommand(command) },
 *         enableDoubleTap = true,
 *         onDoubleTap = { /* Handle double tap action */ }
 *     )
 * }
 * ```
 */
@Composable
fun SingleTapVoiceHandler(
    featureId: String,
    onVoiceCommand: (VoiceCommand) -> Unit,
    modifier: Modifier = Modifier,
    showVisualIndicator: Boolean = true,
    enableDoubleTap: Boolean = true,
    onDoubleTap: (() -> Unit)? = null,
    doubleTapDelay: Long = 300 // milliseconds
) {
    val context = LocalContext.current
    val voiceService = VoiceService.getInstance(context)
    val haptic = LocalHapticFeedback.current
    
    // State for tracking double tap detection
    var lastTapTime by remember { mutableStateOf(0L) }
    var waitingForDoubleTap by remember { mutableStateOf(false) }
    
    // Coroutine scope for launching delayed operations
    val coroutineScope = rememberCoroutineScope()
    
    // Register/unregister with voice service
    DisposableEffect(featureId, onVoiceCommand) {
        voiceService.registerFeature(featureId, onVoiceCommand)
        voiceService.setActiveFeature(featureId)
        
        onDispose {
            voiceService.unregisterFeature(featureId)
        }
    }
    
    // Collect voice service state
    val isListening by voiceService.isListening.collectAsState()
    val isSpeaking by voiceService.isSpeaking.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        
                        if (enableDoubleTap) {
                            if (waitingForDoubleTap) {
                                // This is a double tap
                                waitingForDoubleTap = false
                                
                                // Provide haptic feedback for double tap
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                
                                if (onDoubleTap != null) {
                                    onDoubleTap()
                                } else {
                                    // Default double tap behavior: stop speaking if speaking
                                    if (isSpeaking) {
                                        voiceService.stopSpeaking()
                                    } else {
                                        // Otherwise toggle listening
                                        if (isListening) {
                                            voiceService.stopListening()
                                        } else {
                                            voiceService.handleSingleTap()
                                        }
                                    }
                                }
                            } else {
                                // This might be the first tap of a double tap
                                waitingForDoubleTap = true
                                lastTapTime = currentTime
                                
                                // Wait for potential second tap
                                coroutineScope.launch {
                                    delay(doubleTapDelay)
                                    if (waitingForDoubleTap && (System.currentTimeMillis() - lastTapTime >= doubleTapDelay)) {
                                        // No double tap occurred, handle as single tap
                                        waitingForDoubleTap = false
                                        
                                        // Provide haptic feedback for single tap
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        
                                        // Single tap activates voice recognition
                                        voiceService.handleSingleTap()
                                    }
                                }
                            }
                        } else {
                            // Double tap disabled, just handle single tap
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            voiceService.handleSingleTap()
                        }
                    }
                )
            }
    ) {
        // Show visual indicator when listening (optional)
        if (showVisualIndicator && isListening) {
            ExtendedFloatingActionButton(
                onClick = { voiceService.stopListening() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                containerColor = Color(0xFFE57373),
                contentColor = Color.White,
                icon = { 
                    Icon(
                        imageVector = Icons.Default.Mic, 
                        contentDescription = "Listening - tap to stop"
                    ) 
                },
                text = { 
                    Text("Listening...") 
                }
            )
        }
    }
}

/**
 * Modifier extension for adding single and double tap voice recognition to any composable.
 * This is a more lightweight alternative to the full SingleTapVoiceHandler composable.
 * 
 * Usage:
 * ```
 * Column(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .enableTapVoiceGestures(
 *             featureId = "my_feature",
 *             onVoiceCommand = { command -> handleCommand(command) },
 *             enableDoubleTap = true,
 *             onDoubleTap = { /* Handle double tap action */ }
 *         )
 * ) {
 *     // Your screen content
 * }
 * ```
 */
@Composable
fun Modifier.enableTapVoiceGestures(
    featureId: String,
    onVoiceCommand: (VoiceCommand) -> Unit,
    enableDoubleTap: Boolean = true,
    onDoubleTap: (() -> Unit)? = null,
    doubleTapDelay: Long = 300 // milliseconds
): Modifier {
    val context = LocalContext.current
    val voiceService = VoiceService.getInstance(context)
    val haptic = LocalHapticFeedback.current
    
    // State for tracking double tap detection
    var lastTapTime by remember { mutableStateOf(0L) }
    var waitingForDoubleTap by remember { mutableStateOf(false) }
    
    // Coroutine scope for launching delayed operations
    val coroutineScope = rememberCoroutineScope()
    
    // Register/unregister with voice service
    DisposableEffect(featureId, onVoiceCommand) {
        voiceService.registerFeature(featureId, onVoiceCommand)
        voiceService.setActiveFeature(featureId)
        
        onDispose {
            voiceService.unregisterFeature(featureId)
        }
    }
    
    // Collect voice service state
    val isSpeaking by voiceService.isSpeaking.collectAsState()
    val isListening by voiceService.isListening.collectAsState()
    
    return this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val currentTime = System.currentTimeMillis()
                
                if (enableDoubleTap) {
                    if (waitingForDoubleTap) {
                        // This is a double tap
                        waitingForDoubleTap = false
                        
                        // Provide haptic feedback for double tap
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        if (onDoubleTap != null) {
                            onDoubleTap()
                        } else {
                            // Default double tap behavior: stop speaking if speaking
                            if (isSpeaking) {
                                voiceService.stopSpeaking()
                            } else {
                                // Otherwise toggle listening
                                if (isListening) {
                                    voiceService.stopListening()
                                } else {
                                    voiceService.handleSingleTap()
                                }
                            }
                        }
                    } else {
                        // This might be the first tap of a double tap
                        waitingForDoubleTap = true
                        lastTapTime = currentTime
                        
                        // Wait for potential second tap
                        coroutineScope.launch {
                            delay(doubleTapDelay)
                            if (waitingForDoubleTap && (System.currentTimeMillis() - lastTapTime >= doubleTapDelay)) {
                                // No double tap occurred, handle as single tap
                                waitingForDoubleTap = false
                                
                                // Provide haptic feedback for single tap
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                
                                // Single tap activates voice recognition
                                voiceService.handleSingleTap()
                            }
                        }
                    }
                } else {
                    // Double tap disabled, just handle single tap
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    voiceService.handleSingleTap()
                }
            }
        )
    }
}

/**
 * Legacy support for single tap only - redirects to the new enableTapVoiceGestures
 */
@Composable
fun Modifier.enableSingleTapVoice(
    featureId: String,
    onVoiceCommand: (VoiceCommand) -> Unit
): Modifier = enableTapVoiceGestures(
    featureId = featureId,
    onVoiceCommand = onVoiceCommand,
    enableDoubleTap = false
)

/**
 * A floating action button that shows the current voice recognition status
 * and provides manual control. Use this alongside SingleTapVoiceHandler or
 * the enableSingleTapVoice modifier for a complete voice UI.
 */
@Composable
fun VoiceStatusFAB(
    modifier: Modifier = Modifier,
    onManualActivation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val voiceService = VoiceService.getInstance(context)
    
    val isListening by voiceService.isListening.collectAsState()
    val isSpeaking by voiceService.isSpeaking.collectAsState()
    
    if (isListening || isSpeaking) {
        ExtendedFloatingActionButton(
            onClick = { 
                if (isListening) {
                    voiceService.stopListening()
                } else if (isSpeaking) {
                    voiceService.stopSpeaking()
                } else {
                    onManualActivation?.invoke() ?: voiceService.handleSingleTap()
                }
            },
            modifier = modifier,
            containerColor = when {
                isListening -> MaterialTheme.colorScheme.primary
                isSpeaking -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.tertiary
            },
            contentColor = when {
                isListening -> MaterialTheme.colorScheme.onPrimary
                isSpeaking -> MaterialTheme.colorScheme.onSecondary
                else -> MaterialTheme.colorScheme.onTertiary
            },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Mic, 
                    contentDescription = when {
                        isListening -> "Listening - tap to stop"
                        isSpeaking -> "Speaking - tap to stop"
                        else -> "Voice recognition"
                    }
                ) 
            },
            text = { 
                Text(when {
                    isListening -> "Listening..."
                    isSpeaking -> "Speaking..."
                    else -> "Voice"
                }) 
            }
        )
    }
}

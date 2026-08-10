package com.example.vocaleyesnew

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vocaleyesnew.accessibility.AppCommand
import com.example.vocaleyesnew.accessibility.BaseAccessibleActivity
import com.example.vocaleyesnew.accessibility.UserGuidanceManager
import com.example.vocaleyesnew.accessibility.featureCardTapHandler
import com.example.vocaleyesnew.accessibility.backgroundTapHandler
import com.example.vocaleyesnew.accessibility.singleTapOnlyBackgroundHandler
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.facenet_android.FaceActivity
import com.example.vocaleyesnew.navigation.NavigationActivity
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import com.example.vocaleyesnew.ui.theme.VocalEyesNewTheme
import kotlinx.coroutines.delay

/**
 * Enhanced MainActivity with improved UI/UX and proper accessibility integration
 */
class EnhancedMainActivity : BaseAccessibleActivity() {

    override fun onAccessibilityReady() {
        setContent {
            VocalEyesNewTheme {
                EnhancedHomeScreen(
                    activity = this@EnhancedMainActivity,
                    onNavigateToFeature = { featureClass ->
                        startActivity(Intent(this@EnhancedMainActivity, featureClass))
                    }
                )
            }
        }
    }

    override fun onTtsReady() {
        // Use guidance manager to provide appropriate welcome message
        val welcomeMessage = guidanceManager.getWelcomeMessage(
            UserGuidanceManager.FEATURE_HOME,
            "VocalEyes"
        )
        
        // Only speak if guidance should be shown
        if (guidanceManager.shouldShowGuidance(UserGuidanceManager.FEATURE_HOME)) {
            speak(welcomeMessage)
            guidanceManager.markGuidanceShown(UserGuidanceManager.FEATURE_HOME)
        } else {
            speak("VocalEyes ready.")
        }
    }

    override fun onVoiceCommand(command: String) {
        // Screen-specific commands are handled automatically by base class
        // This method handles any home-screen specific commands
        when (command.lowercase().trim()) {
            "features", "options", "menu" -> {
                speak("Available features: Object detection, Navigation, Face recognition, Text reading, and AI assistant. Say the feature name to open it.")
            }
            else -> {
                speak("Say the name of a feature to open it, like: object detection, navigation, face recognition, text reading, or AI assistant.")
            }
        }
    }

    override fun parseScreenSpecificCommand(command: String): AppCommand? {
        // All main navigation commands are handled by base class
        return null
    }

    override fun getScreenSpecificHelp(): String {
        return "Available features: Object detection, Navigation, Face recognition, Text reading, AI assistant"
    }

    override fun handleDoubleTapEvent(x: Float, y: Float) {
        // Double tap on empty space should do nothing in home page
        // Only feature card double taps should work
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EnhancedHomeScreen(
    activity: EnhancedMainActivity,
    onNavigateToFeature: (Class<*>) -> Unit
) {
    val context = LocalContext.current
    
    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100) // Small delay for smooth animation
        isVisible = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "VocalEyes",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .singleTapOnlyBackgroundHandler(activity),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -40 })
                ) {
                    WelcomeCard()
                }
            }

            item {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "FEATURES",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
                ) {
                    // 2x3 Grid of square feature buttons
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Row 1: AI Assistant and Object Detection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SquareFeatureCard(
                                title = "AI Assistant",
                                description = "Ask questions and get help",
                                icon = Icons.Default.Assistant,
                                backgroundColor = Color(0xFF6B73FF),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onNavigateToFeature(ChatActivity::class.java)
                                }
                            )
                            
                            SquareFeatureCard(
                                title = "Object Detection",
                                description = "Identify objects around you",
                                icon = Icons.Outlined.Visibility,
                                backgroundColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onNavigateToFeature(ObjectDetectionActivity::class.java)
                                }
                            )
                        }

                        // Row 2: Navigation and Face Recognition
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SquareFeatureCard(
                                title = "Navigation",
                                description = "Get navigation assistance",
                                icon = Icons.Outlined.Map,
                                backgroundColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onNavigateToFeature(NavigationActivity::class.java)
                                }
                            )
                            
                            SquareFeatureCard(
                                title = "Face Recognition",
                                description = "Recognize people around you",
                                icon = Icons.Outlined.Face,
                                backgroundColor = Color(0xFF7B1FA2),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onNavigateToFeature(FaceActivity::class.java)
                                }
                            )
                        }

                        // Row 3: Text Reading (centered, takes half width)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SquareFeatureCard(
                                title = "Text Reading",
                                description = "Read text from images",
                                icon = Icons.Outlined.MenuBook,
                                backgroundColor = Color(0xFFF57C00),
                                modifier = Modifier.width(160.dp), // Fixed width for centered card
                                onClick = {
                                    onNavigateToFeature(TextExtractionActivity::class.java)
                                }
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 60 })
                ) {
                    InstructionsCard()
                }
            }
            
            // Add some bottom padding
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Voice Assistant for Vision",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Single tap anywhere for voice commands\nDouble tap feature cards to open them",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun FeaturedFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.tween(100)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onDoubleTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(colors = gradient)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EnhancedFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .height(120.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onDoubleTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPressed) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun SquareFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(1f) // This makes it square
            .featureCardTapHandler(onDoubleTap = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun InstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Quick Guide",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InstructionItem(
                    icon = Icons.Default.TouchApp,
                    text = "Single tap anywhere for voice commands"
                )
                
                
                InstructionItem(
                    icon = Icons.Default.TouchApp,
                    text = "Double tap feature cards to open them"
                )
                
                InstructionItem(
                    icon = Icons.Default.Mic,
                    text = "Say feature names like 'AI Assistant' or 'Object Detection'"
                )
            }
        }
    }
}

@Composable
fun InstructionItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

package com.example.vocaleyesnew.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.vocaleyesnew.EnhancedMainActivity
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.facenet_android.FaceMainActivity
import com.example.vocaleyesnew.navigation.NavigationActivity
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import java.util.*

/**
 * Unified Voice Command Router
 * 
 * Centralizes all voice command parsing and routing logic across the app.
 * Provides consistent command recognition and navigation behavior.
 */

sealed class VoiceCommand {
    // Navigation commands
    object Home : VoiceCommand()
    object Back : VoiceCommand()
    object Help : VoiceCommand()
    
    // Feature navigation
    object OpenObjectDetection : VoiceCommand()
    object OpenNavigation : VoiceCommand()
    object OpenFaceRecognition : VoiceCommand()
    object OpenTextReading : VoiceCommand()
    object OpenAssistant : VoiceCommand()
    
    // Feature-specific commands
    data class ObjectDetectionCommand(val action: ObjectDetectionAction) : VoiceCommand()
    data class FaceRecognitionCommand(val action: FaceRecognitionAction) : VoiceCommand()
    data class TextReadingCommand(val action: TextReadingAction) : VoiceCommand()
    data class NavigationCommand(val action: NavigationAction) : VoiceCommand()
    data class ChatCommand(val action: ChatAction) : VoiceCommand()
    
    // Unknown command
    data class Unknown(val originalCommand: String) : VoiceCommand()
}

enum class ObjectDetectionAction {
    DETECT_NOW,
    PAUSE_DETECTION,
    RESUME_DETECTION,
    TOGGLE_DETECTION,
    SWITCH_CAMERA
}

enum class FaceRecognitionAction {
    DETECT_FACES,
    ADD_PERSON,
    LIST_PEOPLE,
    TOGGLE_RECOGNITION
}

enum class TextReadingAction {
    RESTART_READING,
    STOP_READING,
    READ_AGAIN,
    CAPTURE_NOW
}

enum class NavigationAction {
    START_NAVIGATION,
    GET_DIRECTIONS,
    DESCRIBE_SURROUNDINGS
}

enum class ChatAction {
    START_CONVERSATION,
    STOP_SPEAKING
}

interface VoiceCommandHandler {
    fun handleCommand(command: VoiceCommand)
    fun getFeatureSpecificHelp(): String
}

class VoiceCommandRouter(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceCommandRouter"
        private const val SIMILARITY_THRESHOLD = 0.7 // Threshold for fuzzy matching
    }
    
    private val globalCommands = mapOf(
        // Home commands
        listOf("home", "main", "start", "main menu", "menu", "go home", "take me home", "main screen") to VoiceCommand.Home,
        listOf("back", "return", "exit", "previous", "go back", "take me back") to VoiceCommand.Back,
        listOf("help", "what can you do", "commands", "show help", "need help", "available commands", "what can i say") to VoiceCommand.Help,
        
        // Navigation to features
        listOf("object detection", "objects", "detect objects", "identify objects", "object recognition", 
               "what is this", "what's this", "what do you see", "identify this", "recognize objects", "find objects") to VoiceCommand.OpenObjectDetection,
        listOf("navigation", "navigate", "directions", "map", "go", "where am i", "guide me", 
               "find my way", "get directions", "show map", "location") to VoiceCommand.OpenNavigation,
        listOf("face recognition", "faces", "detect faces", "recognize faces", "face detection", 
               "who is this", "identify person", "recognize person", "find faces", "scan faces") to VoiceCommand.OpenFaceRecognition,
        listOf("text reading", "read text", "text extraction", "ocr", "read", "reading", 
               "read this", "extract text", "scan text", "recognize text", "what does this say") to VoiceCommand.OpenTextReading,
        listOf("assistant", "chat", "ai chat", "talk", "ai", "help me", 
               "conversation", "speak with ai", "virtual assistant", "chat bot", "talk to me") to VoiceCommand.OpenAssistant
    )
    
    private val objectDetectionCommands = mapOf(
        listOf("what do you see", "describe", "identify", "what's there", "scan", "detect") to ObjectDetectionAction.DETECT_NOW,
        listOf("pause", "pause detection", "stop detection") to ObjectDetectionAction.PAUSE_DETECTION,
        listOf("resume", "resume detection", "start detection", "play") to ObjectDetectionAction.RESUME_DETECTION,
        listOf("toggle", "toggle processing", "toggle detection") to ObjectDetectionAction.TOGGLE_DETECTION,
        listOf("switch camera", "flip camera", "change camera") to ObjectDetectionAction.SWITCH_CAMERA
    )
    
    private val faceRecognitionCommands = mapOf(
        listOf("detect faces", "find faces", "scan faces", "who is this") to FaceRecognitionAction.DETECT_FACES,
        listOf("add person", "register face", "new person", "add face") to FaceRecognitionAction.ADD_PERSON,
        listOf("list people", "show people", "who do you know") to FaceRecognitionAction.LIST_PEOPLE,
        listOf("toggle recognition", "pause recognition", "resume recognition") to FaceRecognitionAction.TOGGLE_RECOGNITION
    )
    
    private val textReadingCommands = mapOf(
        listOf("restart", "start again", "restart reading", "new scan", "scan again") to TextReadingAction.RESTART_READING,
        listOf("stop reading", "stop", "pause", "pause reading") to TextReadingAction.STOP_READING,
        listOf("read again", "repeat", "repeat text", "read text") to TextReadingAction.READ_AGAIN,
        listOf("capture now", "capture", "take photo", "scan now") to TextReadingAction.CAPTURE_NOW
    )
    
    private val navigationCommands = mapOf(
        listOf("start navigation", "begin navigation", "navigate now") to NavigationAction.START_NAVIGATION,
        listOf("get directions", "directions", "how to get there") to NavigationAction.GET_DIRECTIONS,
        listOf("describe surroundings", "what's around", "describe scene") to NavigationAction.DESCRIBE_SURROUNDINGS
    )
    
    private val chatCommands = mapOf(
        listOf("start conversation", "let's talk", "chat") to ChatAction.START_CONVERSATION,
        listOf("stop speaking", "be quiet", "stop talking") to ChatAction.STOP_SPEAKING
    )
    
    /**
     * Parse raw voice command into typed VoiceCommand
     */
    fun parseCommand(rawCommand: String, currentFeature: String? = null): VoiceCommand {
        val normalizedCommand = rawCommand.lowercase().trim()
        
        Log.d(TAG, "Parsing command: '$normalizedCommand' in feature: $currentFeature")
        
        // Check for exact matches first
        val exactMatch = findExactMatch(normalizedCommand, currentFeature)
        if (exactMatch != null) {
            return exactMatch
        }
        
        // Check for partial matches (command is contained within the spoken phrase)
        val partialMatch = findPartialMatch(normalizedCommand, currentFeature)
        if (partialMatch != null) {
            return partialMatch
        }
        
        // Check for fuzzy matches (for handling slight mispronunciations)
        val fuzzyMatch = findFuzzyMatch(normalizedCommand, currentFeature)
        if (fuzzyMatch != null) {
            return fuzzyMatch
        }
        
        // Check for intent-based matches (more semantic understanding)
        val intentMatch = findIntentMatch(normalizedCommand, currentFeature)
        if (intentMatch != null) {
            return intentMatch
        }
        
        Log.d(TAG, "No match found for command: '$normalizedCommand'")
        return VoiceCommand.Unknown(rawCommand)
    }
    
    /**
     * Find exact matches for commands
     */
    private fun findExactMatch(normalizedCommand: String, currentFeature: String?): VoiceCommand? {
        // Check global commands first
        for ((patterns, command) in globalCommands) {
            if (patterns.any { pattern -> normalizedCommand == pattern }) {
                Log.d(TAG, "Exact match for global command: $command")
                return command
            }
        }
        
        // Check feature-specific commands
        when (currentFeature) {
            "object_detection" -> {
                for ((patterns, action) in objectDetectionCommands) {
                    if (patterns.any { pattern -> normalizedCommand == pattern }) {
                        Log.d(TAG, "Exact match for object detection command: $action")
                        return VoiceCommand.ObjectDetectionCommand(action)
                    }
                }
            }
            "face_recognition" -> {
                for ((patterns, action) in faceRecognitionCommands) {
                    if (patterns.any { pattern -> normalizedCommand == pattern }) {
                        Log.d(TAG, "Exact match for face recognition command: $action")
                        return VoiceCommand.FaceRecognitionCommand(action)
                    }
                }
            }
            "text_reading" -> {
                for ((patterns, action) in textReadingCommands) {
                    if (patterns.any { pattern -> normalizedCommand == pattern }) {
                        Log.d(TAG, "Exact match for text reading command: $action")
                        return VoiceCommand.TextReadingCommand(action)
                    }
                }
            }
            "navigation" -> {
                for ((patterns, action) in navigationCommands) {
                    if (patterns.any { pattern -> normalizedCommand == pattern }) {
                        Log.d(TAG, "Exact match for navigation command: $action")
                        return VoiceCommand.NavigationCommand(action)
                    }
                }
            }
            "chat" -> {
                for ((patterns, action) in chatCommands) {
                    if (patterns.any { pattern -> normalizedCommand == pattern }) {
                        Log.d(TAG, "Exact match for chat command: $action")
                        return VoiceCommand.ChatCommand(action)
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find partial matches (command is contained within the spoken phrase)
     */
    private fun findPartialMatch(normalizedCommand: String, currentFeature: String?): VoiceCommand? {
        // Check global commands first
        for ((patterns, command) in globalCommands) {
            if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                Log.d(TAG, "Partial match for global command: $command")
                return command
            }
        }
        
        // Check feature-specific commands
        when (currentFeature) {
            "object_detection" -> {
                for ((patterns, action) in objectDetectionCommands) {
                    if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                        Log.d(TAG, "Partial match for object detection command: $action")
                        return VoiceCommand.ObjectDetectionCommand(action)
                    }
                }
            }
            "face_recognition" -> {
                for ((patterns, action) in faceRecognitionCommands) {
                    if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                        Log.d(TAG, "Partial match for face recognition command: $action")
                        return VoiceCommand.FaceRecognitionCommand(action)
                    }
                }
            }
            "text_reading" -> {
                for ((patterns, action) in textReadingCommands) {
                    if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                        Log.d(TAG, "Partial match for text reading command: $action")
                        return VoiceCommand.TextReadingCommand(action)
                    }
                }
            }
            "navigation" -> {
                for ((patterns, action) in navigationCommands) {
                    if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                        Log.d(TAG, "Partial match for navigation command: $action")
                        return VoiceCommand.NavigationCommand(action)
                    }
                }
            }
            "chat" -> {
                for ((patterns, action) in chatCommands) {
                    if (patterns.any { pattern -> normalizedCommand.contains(pattern) }) {
                        Log.d(TAG, "Partial match for chat command: $action")
                        return VoiceCommand.ChatCommand(action)
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find fuzzy matches for handling slight mispronunciations
     */
    private fun findFuzzyMatch(normalizedCommand: String, currentFeature: String?): VoiceCommand? {
        // Check global commands first with fuzzy matching
        for ((patterns, command) in globalCommands) {
            for (pattern in patterns) {
                if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                    Log.d(TAG, "Fuzzy match for global command: $command (pattern: $pattern)")
                    return command
                }
            }
        }
        
        // Check feature-specific commands with fuzzy matching
        when (currentFeature) {
            "object_detection" -> {
                for ((patterns, action) in objectDetectionCommands) {
                    for (pattern in patterns) {
                        if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                            Log.d(TAG, "Fuzzy match for object detection command: $action (pattern: $pattern)")
                            return VoiceCommand.ObjectDetectionCommand(action)
                        }
                    }
                }
            }
            "face_recognition" -> {
                for ((patterns, action) in faceRecognitionCommands) {
                    for (pattern in patterns) {
                        if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                            Log.d(TAG, "Fuzzy match for face recognition command: $action (pattern: $pattern)")
                            return VoiceCommand.FaceRecognitionCommand(action)
                        }
                    }
                }
            }
            "text_reading" -> {
                for ((patterns, action) in textReadingCommands) {
                    for (pattern in patterns) {
                        if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                            Log.d(TAG, "Fuzzy match for text reading command: $action (pattern: $pattern)")
                            return VoiceCommand.TextReadingCommand(action)
                        }
                    }
                }
            }
            "navigation" -> {
                for ((patterns, action) in navigationCommands) {
                    for (pattern in patterns) {
                        if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                            Log.d(TAG, "Fuzzy match for navigation command: $action (pattern: $pattern)")
                            return VoiceCommand.NavigationCommand(action)
                        }
                    }
                }
            }
            "chat" -> {
                for ((patterns, action) in chatCommands) {
                    for (pattern in patterns) {
                        if (calculateSimilarity(normalizedCommand, pattern) >= SIMILARITY_THRESHOLD) {
                            Log.d(TAG, "Fuzzy match for chat command: $action (pattern: $pattern)")
                            return VoiceCommand.ChatCommand(action)
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find intent-based matches (more semantic understanding)
     */
    private fun findIntentMatch(normalizedCommand: String, currentFeature: String?): VoiceCommand? {
        // Intent matching for common phrases that might not match exactly
        
        // Help intent
        if (normalizedCommand.contains("how") && normalizedCommand.contains("use") ||
            normalizedCommand.contains("what") && normalizedCommand.contains("do")) {
            return VoiceCommand.Help
        }
        
        // Feature-specific intents based on context
        when (currentFeature) {
            "object_detection" -> {
                if (normalizedCommand.contains("what") && 
                    (normalizedCommand.contains("that") || normalizedCommand.contains("this"))) {
                    return VoiceCommand.ObjectDetectionCommand(ObjectDetectionAction.DETECT_NOW)
                }
                if (normalizedCommand.contains("stop") || normalizedCommand.contains("wait")) {
                    return VoiceCommand.ObjectDetectionCommand(ObjectDetectionAction.PAUSE_DETECTION)
                }
                if (normalizedCommand.contains("continue") || normalizedCommand.contains("go on")) {
                    return VoiceCommand.ObjectDetectionCommand(ObjectDetectionAction.RESUME_DETECTION)
                }
            }
            "text_reading" -> {
                if (normalizedCommand.contains("again") || normalizedCommand.contains("repeat")) {
                    return VoiceCommand.TextReadingCommand(TextReadingAction.READ_AGAIN)
                }
                if (normalizedCommand.contains("stop") || normalizedCommand.contains("enough")) {
                    return VoiceCommand.TextReadingCommand(TextReadingAction.STOP_READING)
                }
                if (normalizedCommand.contains("take") && normalizedCommand.contains("picture") ||
                    normalizedCommand.contains("photo")) {
                    return VoiceCommand.TextReadingCommand(TextReadingAction.CAPTURE_NOW)
                }
            }
        }
        
        return null
    }
    
    /**
     * Calculate similarity between two strings (Levenshtein distance based)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        // For very short commands, require higher precision
        if (s2.length <= 4 && s1 != s2) {
            return 0.0
        }
        
        // For longer phrases, use Levenshtein distance
        val distance = levenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        
        // Convert to similarity score (0.0 to 1.0)
        return 1.0 - (distance.toDouble() / maxLength.toDouble())
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        // Create distance matrix
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // Initialize first row and column
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        // Fill the matrix
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[m][n]
    }
    
    /**
     * Route global navigation commands
     */
    fun routeGlobalCommand(command: VoiceCommand): Boolean {
        return when (command) {
            is VoiceCommand.Home -> {
                navigateToActivity(EnhancedMainActivity::class.java)
                true
            }
            is VoiceCommand.OpenObjectDetection -> {
                navigateToActivity(ObjectDetectionActivity::class.java)
                true
            }
            is VoiceCommand.OpenNavigation -> {
                navigateToActivity(NavigationActivity::class.java)
                true
            }
            is VoiceCommand.OpenFaceRecognition -> {
                navigateToActivity(FaceMainActivity::class.java)
                true
            }
            is VoiceCommand.OpenTextReading -> {
                navigateToActivity(TextExtractionActivity::class.java)
                true
            }
            is VoiceCommand.OpenAssistant -> {
                navigateToActivity(ChatActivity::class.java)
                true
            }
            else -> false // Not a global navigation command
        }
    }
    
    /**
     * Get comprehensive help text
     */
    fun getGlobalHelpText(): String {
        return buildString {
            append("Global commands: ")
            append("Home, Back, Help, ")
            append("Object detection, Navigation, ")
            append("Face recognition, Text reading, Assistant. ")
            append("You can say 'What can I do?' for more help.")
        }
    }
    
    /**
     * Get feature-specific help text
     */
    fun getFeatureHelpText(feature: String): String {
        return when (feature) {
            "object_detection" -> "Object detection: What do you see, Pause/Resume detection, Toggle detection, Switch camera"
            "face_recognition" -> "Face recognition: Detect faces, Add person, List people, Toggle recognition"
            "text_reading" -> "Text reading: Restart reading, Stop reading, Read again, Capture now"
            "navigation" -> "Navigation: Start navigation, Get directions, Describe surroundings"
            "chat" -> "Chat: Start conversation, Stop speaking"
            else -> ""
        }
    }
    
    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(context, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}

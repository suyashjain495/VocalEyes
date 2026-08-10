package com.example.vocaleyesnew.accessibility

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user guidance to prevent excessive TTS instructions.
 * Limits guidance to 2-3 times per feature after installation.
 */
class UserGuidanceManager private constructor(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "user_guidance_prefs", Context.MODE_PRIVATE
    )
    
    companion object {
        @Volatile
        private var INSTANCE: UserGuidanceManager? = null
        
        fun getInstance(context: Context): UserGuidanceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserGuidanceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Maximum number of times guidance is shown per feature
        private const val MAX_GUIDANCE_COUNT = 2
        
        // Feature keys
        const val FEATURE_HOME = "home_guidance"
        const val FEATURE_OBJECT_DETECTION = "object_detection_guidance"
        const val FEATURE_TEXT_EXTRACTION = "text_extraction_guidance"
        const val FEATURE_FACE_RECOGNITION = "face_recognition_guidance"
        const val FEATURE_AI_CHAT = "ai_chat_guidance"
        const val FEATURE_NAVIGATION = "navigation_guidance"
        
        // General app guidance
        const val GENERAL_APP_INTRO = "general_app_intro"
        const val VOICE_COMMANDS_INTRO = "voice_commands_intro"
        const val DOUBLE_TAP_INTRO = "double_tap_intro"
    }
    
    /**
     * Check if guidance should be shown for a specific feature
     */
    fun shouldShowGuidance(featureKey: String): Boolean {
        val count = preferences.getInt(featureKey, 0)
        return count < MAX_GUIDANCE_COUNT
    }
    
    /**
     * Mark that guidance has been shown for a feature
     */
    fun markGuidanceShown(featureKey: String) {
        val currentCount = preferences.getInt(featureKey, 0)
        preferences.edit().putInt(featureKey, currentCount + 1).apply()
    }
    
    /**
     * Get the number of times guidance has been shown for a feature
     */
    fun getGuidanceCount(featureKey: String): Int {
        return preferences.getInt(featureKey, 0)
    }
    
    /**
     * Check if this is the user's first time opening the app
     */
    fun isFirstLaunch(): Boolean {
        return preferences.getBoolean("first_launch", true)
    }
    
    /**
     * Mark that the user has completed the first launch
     */
    fun markFirstLaunchComplete() {
        preferences.edit().putBoolean("first_launch", false).apply()
    }
    
    /**
     * Reset all guidance counters (for testing or user preference)
     */
    fun resetAllGuidance() {
        preferences.edit().clear().apply()
    }
    
    /**
     * Check if user prefers minimal guidance
     */
    fun isMinimalGuidancePreferred(): Boolean {
        return preferences.getBoolean("minimal_guidance", false)
    }
    
    /**
     * Set user preference for minimal guidance
     */
    fun setMinimalGuidancePreference(minimal: Boolean) {
        preferences.edit().putBoolean("minimal_guidance", minimal).apply()
    }
    
    /**
     * Get appropriate welcome message based on usage count
     */
    fun getWelcomeMessage(featureKey: String, featureName: String): String {
        val count = getGuidanceCount(featureKey)
        return when (count) {
            0 -> "Welcome to $featureName. Tap anywhere for voice commands, double-tap buttons to activate them."
            1 -> "$featureName is ready. You can use voice commands or touch gestures."
            else -> "$featureName activated."
        }
    }
    
    /**
     * Get help message for a feature (only if guidance should be shown)
     */
    fun getHelpMessage(featureKey: String, helpText: String): String? {
        return if (shouldShowGuidance(featureKey)) helpText else null
    }
}

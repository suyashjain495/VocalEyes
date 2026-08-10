package com.example.vocaleyesnew.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.vocaleyesnew.BuildConfig
import java.io.IOException

val generativeModel = GenerativeModel(
    modelName = "gemini-2.5-flash",
    apiKey = BuildConfig.GEMINI_API_KEY
)

suspend fun sendFrameToGeminiAI(bitmap: Bitmap, onPartialResult: (String) -> Unit, onError: (String) -> Unit) {
    val startTime = System.currentTimeMillis()
    var firstResponseTime: Long? = null
    var hasReceivedFirstResponse = false
    
    try {
        withContext(Dispatchers.IO) {
            val prompt = """
You are a REAL-TIME navigation assistant for visually impaired users. SAFETY IS THE TOP PRIORITY.
Provide immediate, precise navigation commands in 2-3 SHORT sentences every 2 seconds.

IMPORTANT: Output ONLY plain text. NO JSON, NO brackets, NO special formatting.

CRITICAL SAFETY RULES:
🚨 ALWAYS identify obstacles within 3 meters as IMMEDIATE threats
🚨 Give SPECIFIC directional commands: "move left now", "step right", "stop immediately"
🚨 Estimate distances precisely: "1 meter ahead", "2 steps away", "very close"
🚨 NEVER say "walk straight" if ANY obstacle is in the direct path
🚨 Prioritize moving obstacles (vehicles, people) as highest danger

OUTPUT FORMAT (2-3 sentences max):
1. Environment type + immediate obstacle location
2. Precise distance + specific action command
3. Safe direction guidance

DISTANCE PRECISION:
- "Very close" = 0-1 meter (DANGER)
- "1-2 steps" = 1-2 meters (WARNING)
- "3-4 steps" = 2-3 meters (CAUTION)
- "Ahead" = 3+ meters (SAFE)

OBSTACLE PRIORITY (Most to Least Urgent):
1. Moving vehicles/people (STOP command)
2. Large stationary objects (trucks, walls, poles)
3. Small obstacles (chairs, bags, curbs)
4. Environmental features (stairs, doors)

ACTION COMMANDS:
- "STOP immediately" - for immediate danger
- "Move left/right now" - for close obstacles
- "Step left/right" - for navigation adjustments
- "Continue straight" - ONLY when path is completely clear

EXAMPLES:
❌ BAD: "You're on a sidewalk. Walk straight."
✅ GOOD: "Large truck close on left. Move right."

❌ BAD: "There's a car ahead. Continue walking."
✅ GOOD: "Road crossing.car 2 steps ahead. Stop and wait."

❌ BAD: "Hallway with some furniture."
✅ GOOD: "Chair blocking path 1 meter ahead. Step left"

EMERGENCY SITUATIONS:
- Moving vehicles: "STOP. Car approaching from left."
- Steep drops: "STOP. Stairs going down 1 step ahead."
- Blocked path: "Path blocked. Turn around or move right."
i want to give very short guidance as with which i can uderstand 

REMEMBER: User moves fast - by next frame (2 seconds) they may be 2-3 meters closer to obstacles.
ALWAYS assume user is MOVING FORWARD unless told to stop.

Final Rule: If there's ANY doubt about safety, command "STOP" first, then give directions.
""".trimIndent()


            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            var fullResponse = ""
            generativeModel.generateContentStream(inputContent).collect { response ->
                response.text?.let {
                    // Log first response time
                    if (!hasReceivedFirstResponse) {
                        firstResponseTime = System.currentTimeMillis()
                        val timeToFirstByte = firstResponseTime!! - startTime
                        Log.d("GeminiPerformance", "Time to first response: ${timeToFirstByte}ms")
                        hasReceivedFirstResponse = true
                    }
                    
                    fullResponse += it
                    onPartialResult(it)
                }
            }
            
            // Log total response time
            val totalTime = System.currentTimeMillis() - startTime
            Log.d("GeminiPerformance", "Total response time: ${totalTime}ms")
            Log.d("GeminiPerformance", "Full response length: ${fullResponse.length} characters")
        }
    } catch (e: IOException) {
        Log.e("GeminiAI", "Network error: ${e.message}")
        onError("Network error: ${e.message}")
    } catch (e: Exception) {
        Log.e("GeminiAI", "Unexpected error: ${e.message}")
        onError("Unexpected error: ${e.message}")
    }
}

fun ImageProxy.toBitmap(): Bitmap? {
    return try {
        val buffer = this.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("ImageProxy", "Error converting ImageProxy to Bitmap: ${e.message}")
        null
    }
} 
package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TextRecognizer(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val _recognizedTextFlow = MutableStateFlow("")
    val recognizedTextFlow: StateFlow<String> = _recognizedTextFlow
    private var isEnabled = true

    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    override fun analyze(imageProxy: ImageProxy) {
        if (!isEnabled) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = processTextRecognitionResult(visionText)
                    if (extractedText.isNotEmpty()) {
                        _recognizedTextFlow.value = extractedText
                        onTextRecognized(extractedText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TextRecognizer", "Text recognition failed: ${e.message}", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        Log.d("TextRecognizer", "Starting OCR on bitmap: ${bitmap.width}x${bitmap.height}")
        try {
            // Check if image is suitable for OCR
            val (isSuitable, message) = ImagePreprocessor.isImageSuitableForOCR(bitmap)
            Log.d("TextRecognizer", "Image suitability check: $message")
            
            // Resize if necessary
            val resizedBitmap = ImagePreprocessor.resizeForOCR(bitmap)
            
            // Preprocess image for better OCR accuracy
            val processedBitmap = ImagePreprocessor.preprocessForOCR(resizedBitmap)
            
            val image = InputImage.fromBitmap(processedBitmap, 0)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d("TextRecognizer", "OCR successful, processing result")
                    val extractedText = processTextRecognitionResult(visionText)
                    Log.d("TextRecognizer", "Extracted text length: ${extractedText.length}")
                    if (extractedText.isNotEmpty()) {
                        Log.d("TextRecognizer", "OCR result: ${extractedText.take(100)}...") // Log first 100 chars
                        _recognizedTextFlow.value = extractedText
                        callback(extractedText)
                    } else {
                        Log.w("TextRecognizer", "OCR completed but no text found")
                        callback("")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TextRecognizer", "OCR failed: ${e.message}", e)
                    callback("")
                }
        } catch (e: Exception) {
            Log.e("TextRecognizer", "Error setting up OCR: ${e.message}", e)
            callback("")
        }
    }
    
    // Synchronous version for compatibility (deprecated - use callback version)
    @Deprecated("Use callback version instead")
    fun recognizeText(bitmap: Bitmap): String {
        Log.d("TextRecognizer", "Using deprecated synchronous OCR method")
        var result = ""
        var completed = false
        
        recognizeText(bitmap) { text ->
            result = text
            completed = true
        }
        
        // Wait for completion with timeout
        val startTime = System.currentTimeMillis()
        while (!completed && (System.currentTimeMillis() - startTime) < 10000) { // 10 second timeout
            Thread.sleep(100)
        }
        
        if (!completed) {
            Log.e("TextRecognizer", "OCR timed out after 10 seconds")
        }
        
        return result
    }

    // New method to process an ImageProxy and call a callback with the result
    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    fun recognizeText(imageProxy: ImageProxy, callback: (String) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = processTextRecognitionResult(visionText)
                    if (extractedText.isNotEmpty()) {
                        _recognizedTextFlow.value = extractedText
                        callback(extractedText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TextRecognizer", "Text recognition failed: ${e.message}", e)
                    callback("")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
            callback("")
        }
    }

    fun stopRecognition() {
        isEnabled = false
    }

    fun restartRecognition() {
        isEnabled = true
    }

    fun getContext(): Context {
        return context
    }

    private fun processTextRecognitionResult(result: Text): String {
        val stringBuilder = StringBuilder()
        var wordCount = 0
        var lineCount = 0
        
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.isNotEmpty()) {
                    // Add proper spacing and punctuation for better TTS
                    if (lineCount > 0) {
                        stringBuilder.append(". ") // Add period for sentence breaks
                    }
                    stringBuilder.append(lineText)
                    wordCount += lineText.split("\\s+".toRegex()).size
                    lineCount++
                }
            }
        }
        
        val finalText = stringBuilder.toString().trim()
        
        // Log statistics for debugging
        Log.d("TextRecognizer", "Processed text: $lineCount lines, $wordCount words, ${finalText.length} characters")
        
        return finalText
    }
}
//
//package com.example.vocaleyesnew.textextraction
//
//import android.graphics.Bitmap
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.text.TextRecognition
//import com.google.mlkit.vision.text.TextRecognizer
//import com.google.mlkit.vision.text.latin.TextRecognizerOptions
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlin.coroutines.resume
//import kotlin.coroutines.resumeWithException
//
//class TextRecognizer {
//    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
//
//    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
//        val image = InputImage.fromBitmap(bitmap, 0)
//        recognizer.process(image)
//            .addOnSuccessListener { visionText ->
//                continuation.resume(visionText.text)
//            }
//            .addOnFailureListener { e ->
//                continuation.resumeWithException(e)
//            }
//
//        continuation.invokeOnCancellation {
//            // Cleanup if needed
//        }
//    }
//}
package com.example.vocaleyesnew.textextraction

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Clean minimal interface for text recognition operations.
 */
interface ITextRecognizer {
    /**
     * Recognize text from a bitmap image asynchronously.
     * 
     * @param bitmap The bitmap to analyze for text
     * @param callback Callback to receive the recognized text result
     */
    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit)

    /**
     * Recognize text from an ImageProxy (camera frame) asynchronously.
     * 
     * @param imageProxy The camera frame to analyze
     * @param callback Callback to receive the recognized text result
     */
    fun recognizeText(imageProxy: ImageProxy, callback: (String) -> Unit)

    /**
     * Stop ongoing text recognition operations.
     */
    fun stopRecognition()

    /**
     * Restart text recognition operations.
     */
    fun restartRecognition()
}

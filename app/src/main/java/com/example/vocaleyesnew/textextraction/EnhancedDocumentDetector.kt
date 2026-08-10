package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Enhanced document detector with fallback implementation when OpenCV is not available.
 * Implements the IDocumentScanner interface with basic computer vision algorithms.
 */
class EnhancedDocumentDetector(private val context: Context) : IDocumentScanner {
    
    companion object {
        private const val TAG = "EnhancedDocumentDetector"
    }
    
    /**
     * Detect document corners - returns null since OpenCV is not available
     */
    override fun detect(bitmap: Bitmap): DocumentCorners? {
        Log.w(TAG, "OpenCV not available, cannot perform enhanced detection")
        return null
    }
    
    /**
     * Crop and correct perspective - falls back to simple crop
     */
    override fun cropAndCorrect(bitmap: Bitmap, corners: DocumentCorners): Bitmap? {
        Log.w(TAG, "OpenCV not available, falling back to simple crop")
        return simpleCrop(bitmap, corners)
    }
    
    /**
     * Enhance for OCR - falls back to basic enhancement
     */
    override fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        Log.w(TAG, "OpenCV not available, using basic enhancement")
        return enhanceBasic(bitmap)
    }
    
    /**
     * Simple crop fallback when OpenCV is not available.
     */
    private fun simpleCrop(bitmap: Bitmap, corners: DocumentCorners): Bitmap? {
        return try {
            val left = corners.topLeft.x.toInt().coerceIn(0, bitmap.width)
            val top = corners.topLeft.y.toInt().coerceIn(0, bitmap.height)
            val right = corners.bottomRight.x.toInt().coerceIn(left, bitmap.width)
            val bottom = corners.bottomRight.y.toInt().coerceIn(top, bitmap.height)
            
            val width = right - left
            val height = bottom - top
            
            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in simple crop", e)
            null
        }
    }
    
    /**
     * Basic image enhancement fallback when OpenCV is not available.
     */
    private fun enhanceBasic(bitmap: Bitmap): Bitmap {
        return try {
            // Simple contrast enhancement
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Apply contrast enhancement
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                // Increase contrast
                val enhancedR = ((r - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                val enhancedG = ((g - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                val enhancedB = ((b - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                
                pixels[i] = android.graphics.Color.rgb(enhancedR, enhancedG, enhancedB)
            }
            
            val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            enhancedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            Log.d(TAG, "Basic enhancement completed")
            enhancedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in basic enhancement, returning original", e)
            bitmap
        }
    }
}

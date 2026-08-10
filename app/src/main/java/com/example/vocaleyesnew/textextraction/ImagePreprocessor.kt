package com.example.vocaleyesnew.textextraction

import android.graphics.*
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced image preprocessing utility for better OCR accuracy
 * Specifically optimized for text recognition to help blind users
 */
class ImagePreprocessor {
    
    companion object {
        private const val TAG = "ImagePreprocessor"
        
        /**
         * Preprocess image for optimal OCR results with optimized performance
         * Applies essential enhancement techniques with memory management
         */
        fun preprocessForOCR(bitmap: Bitmap): Bitmap {
            Log.d(TAG, "Starting optimized image preprocessing for OCR")
            val startTime = System.currentTimeMillis()
            
            var processedBitmap: Bitmap? = null
            var tempBitmap: Bitmap? = null
            
            try {
                // Create working copy
                processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                // Step 1: Convert to grayscale (most important for OCR)
                tempBitmap = convertToGrayscale(processedBitmap)
                if (processedBitmap != bitmap) processedBitmap.recycle()
                processedBitmap = tempBitmap
                Log.d(TAG, "Converted to grayscale")
                
                // Step 2: Enhance contrast (essential for text clarity)
                tempBitmap = enhanceContrast(processedBitmap, 1.3f) // Reduced intensity
                processedBitmap.recycle()
                processedBitmap = tempBitmap
                Log.d(TAG, "Enhanced contrast")
                
                // Skip adaptive threshold, noise reduction, and sharpening for performance
                // These are computationally expensive and ML Kit handles most of this
                
                val processingTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Optimized preprocessing completed in ${processingTime}ms")
                
                return processedBitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error during image preprocessing", e)
                // Clean up any temp bitmaps
                try {
                    tempBitmap?.recycle()
                    if (processedBitmap != bitmap) processedBitmap?.recycle()
                } catch (cleanupException: Exception) {
                    Log.w(TAG, "Error during cleanup", cleanupException)
                }
                // Return original bitmap if preprocessing fails
                return bitmap
            }
        }
        
        /**
         * Convert image to grayscale for better OCR performance
         */
        private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(grayBitmap)
            val paint = Paint()
            val colorMatrix = ColorMatrix().apply {
                setSaturation(0f) // Remove color saturation
            }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            return grayBitmap
        }
        
        /**
         * Enhance contrast to make text more readable
         */
        private fun enhanceContrast(bitmap: Bitmap, contrastFactor: Float): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()
            
            // Create contrast enhancement color matrix
            val colorMatrix = ColorMatrix(floatArrayOf(
                contrastFactor, 0f, 0f, 0f, 0f,
                0f, contrastFactor, 0f, 0f, 0f,
                0f, 0f, contrastFactor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            return enhancedBitmap
        }
        
        /**
         * Apply adaptive thresholding to separate text from background
         */
        private fun applyAdaptiveThreshold(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Calculate local threshold for each pixel
            val thresholdedPixels = IntArray(pixels.size)
            val windowSize = min(width, height) / 20 // Adaptive window size
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val localMean = calculateLocalMean(pixels, x, y, width, height, windowSize)
                    val grayValue = Color.red(pixels[index]) // Already grayscale
                    
                    // Apply threshold based on local mean
                    val thresholdedValue = if (grayValue > localMean * 0.85) 255 else 0
                    thresholdedPixels[index] = Color.rgb(thresholdedValue, thresholdedValue, thresholdedValue)
                }
            }
            
            val thresholdedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            thresholdedBitmap.setPixels(thresholdedPixels, 0, width, 0, 0, width, height)
            
            return thresholdedBitmap
        }
        
        /**
         * Calculate local mean for adaptive thresholding
         */
        private fun calculateLocalMean(
            pixels: IntArray, 
            centerX: Int, 
            centerY: Int, 
            width: Int, 
            height: Int, 
            windowSize: Int
        ): Int {
            var sum = 0
            var count = 0
            val halfWindow = windowSize / 2
            
            for (dy in -halfWindow..halfWindow) {
                for (dx in -halfWindow..halfWindow) {
                    val x = centerX + dx
                    val y = centerY + dy
                    
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        val index = y * width + x
                        sum += Color.red(pixels[index])
                        count++
                    }
                }
            }
            
            return if (count > 0) sum / count else 128
        }
        
        /**
         * Reduce noise while preserving text details
         */
        private fun reduceNoise(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val denoisedPixels = IntArray(pixels.size)
            
            // Apply median filter to reduce noise
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    val neighbors = mutableListOf<Int>()
                    
                    // Collect neighboring pixels
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val neighborIndex = (y + dy) * width + (x + dx)
                            neighbors.add(Color.red(pixels[neighborIndex]))
                        }
                    }
                    
                    // Use median value
                    neighbors.sort()
                    val medianValue = neighbors[neighbors.size / 2]
                    denoisedPixels[index] = Color.rgb(medianValue, medianValue, medianValue)
                }
            }
            
            // Copy edges from original
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                        val index = y * width + x
                        denoisedPixels[index] = pixels[index]
                    }
                }
            }
            
            val denoisedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            denoisedBitmap.setPixels(denoisedPixels, 0, width, 0, 0, width, height)
            
            return denoisedBitmap
        }
        
        /**
         * Sharpen image to enhance text edges
         */
        private fun sharpenImage(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val sharpenedPixels = IntArray(pixels.size)
            
            // Sharpening kernel
            val kernel = arrayOf(
                arrayOf(0, -1, 0),
                arrayOf(-1, 5, -1),
                arrayOf(0, -1, 0)
            )
            
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    var sum = 0
                    
                    // Apply kernel
                    for (ky in 0..2) {
                        for (kx in 0..2) {
                            val pixelIndex = (y + ky - 1) * width + (x + kx - 1)
                            val pixelValue = Color.red(pixels[pixelIndex])
                            sum += pixelValue * kernel[ky][kx]
                        }
                    }
                    
                    // Clamp values to 0-255 range
                    val sharpenedValue = max(0, min(255, sum))
                    sharpenedPixels[index] = Color.rgb(sharpenedValue, sharpenedValue, sharpenedValue)
                }
            }
            
            // Copy edges from original
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                        val index = y * width + x
                        sharpenedPixels[index] = pixels[index]
                    }
                }
            }
            
            val sharpenedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            sharpenedBitmap.setPixels(sharpenedPixels, 0, width, 0, 0, width, height)
            
            return sharpenedBitmap
        }
        
        /**
         * Resize image for optimal OCR processing if needed
         */
        fun resizeForOCR(bitmap: Bitmap, maxWidth: Int = 2048, maxHeight: Int = 2048): Bitmap {
            val currentWidth = bitmap.width
            val currentHeight = bitmap.height
            
            // Only resize if image is larger than max dimensions
            if (currentWidth <= maxWidth && currentHeight <= maxHeight) {
                return bitmap
            }
            
            // Calculate scaling factor while maintaining aspect ratio
            val widthScale = maxWidth.toFloat() / currentWidth
            val heightScale = maxHeight.toFloat() / currentHeight
            val scale = min(widthScale, heightScale)
            
            val newWidth = (currentWidth * scale).toInt()
            val newHeight = (currentHeight * scale).toInt()
            
            Log.d(TAG, "Resizing image from ${currentWidth}x${currentHeight} to ${newWidth}x${newHeight}")
            
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        /**
         * Check if image is suitable for OCR processing
         */
        fun isImageSuitableForOCR(bitmap: Bitmap): Pair<Boolean, String> {
            val width = bitmap.width
            val height = bitmap.height
            val totalPixels = width * height
            
            return when {
                width < 100 || height < 100 -> {
                    Pair(false, "Image is too small for reliable text recognition")
                }
                totalPixels > 8_000_000 -> {
                    Pair(false, "Image is too large and may cause processing delays")
                }
                width.toFloat() / height > 10 || height.toFloat() / width > 10 -> {
                    Pair(false, "Image aspect ratio is too extreme for optimal text recognition")
                }
                else -> {
                    Pair(true, "Image is suitable for OCR processing")
                }
            }
        }
    }
}

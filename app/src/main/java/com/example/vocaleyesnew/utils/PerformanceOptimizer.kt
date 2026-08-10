package com.example.vocaleyesnew.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Performance optimization utilities for the VocalEyes app.
 * 
 * This class provides:
 * - Memory management utilities
 * - Bitmap optimization
 * - Resource cleanup
 * - Performance monitoring
 */
object PerformanceOptimizer {
    
    private const val TAG = "PerformanceOptimizer"
    private const val LOW_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
    
    // Weak references to track active bitmaps
    private val activeBitmaps = mutableSetOf<WeakReference<Bitmap>>()
    
    /**
     * Get current memory usage information
     */
    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        return MemoryInfo(
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            maxMemory = maxMemory,
            usedMemory = usedMemory,
            availableMemory = availableMemory,
            systemAvailableMemory = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory || availableMemory < LOW_MEMORY_THRESHOLD
        )
    }
    
    /**
     * Optimize bitmap for memory efficiency
     */
    fun optimizeBitmap(bitmap: Bitmap?, maxWidth: Int = 1024, maxHeight: Int = 1024, quality: Int = 85): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // Calculate scale factor if resizing is needed
            val scaleFactor = calculateScaleFactor(width, height, maxWidth, maxHeight)
            
            val optimizedBitmap = if (scaleFactor < 1.0f) {
                // Resize if needed
                val newWidth = (width * scaleFactor).toInt()
                val newHeight = (height * scaleFactor).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            // Track the optimized bitmap
            addBitmapReference(optimizedBitmap)
            
            Log.d(TAG, "Bitmap optimized: ${width}x${height} -> ${optimizedBitmap.width}x${optimizedBitmap.height}")
            return optimizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing bitmap", e)
            return bitmap
        }
    }
    
    /**
     * Create a memory-efficient bitmap from file
     */
    fun createOptimizedBitmap(filePath: String, reqWidth: Int = 1024, reqHeight: Int = 1024): Bitmap? {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory than ARGB_8888
            
            val bitmap = BitmapFactory.decodeFile(filePath, options)
            if (bitmap != null) {
                addBitmapReference(bitmap)
                Log.d(TAG, "Created optimized bitmap: ${bitmap.width}x${bitmap.height}")
            }
            return bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating optimized bitmap from file", e)
            return null
        }
    }
    
    /**
     * Perform garbage collection and memory cleanup
     */
    fun performMemoryCleanup(force: Boolean = false) {
        try {
            // Clean up recycled bitmap references
            cleanupBitmapReferences()
            
            if (force) {
                // Force garbage collection (use sparingly)
                System.gc()
                Log.d(TAG, "Force garbage collection performed")
            }
            
            Log.d(TAG, "Memory cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory cleanup", e)
        }
    }
    
    /**
     * Check if low memory and perform cleanup if needed
     */
    fun checkMemoryAndCleanup(context: Context): Boolean {
        val memInfo = getMemoryInfo(context)
        
        if (memInfo.isLowMemory) {
            Log.w(TAG, "Low memory detected. Performing cleanup.")
            performMemoryCleanup(force = true)
            return true
        }
        return false
    }
    
    /**
     * Optimize app for performance based on device capabilities
     */
    fun optimizeForDevice(context: Context): DeviceOptimization {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass
        val largeMemoryClass = activityManager.largeMemoryClass
        
        return when {
            memoryClass >= 512 -> DeviceOptimization.HIGH_PERFORMANCE
            memoryClass >= 256 -> DeviceOptimization.MEDIUM_PERFORMANCE  
            else -> DeviceOptimization.LOW_PERFORMANCE
        }.also { optimization ->
            Log.d(TAG, "Device optimization level: $optimization (memory class: ${memoryClass}MB)")
        }
    }
    
    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val tempFiles = cacheDir.listFiles { file ->
                file.isFile && (
                    file.name.startsWith("temp_") ||
                    file.name.endsWith(".tmp") ||
                    System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000 // 24 hours
                )
            }
            
            tempFiles?.forEach { file ->
                try {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted temp file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete temp file: ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }
    
    private fun calculateScaleFactor(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Float {
        return if (width <= maxWidth && height <= maxHeight) {
            1.0f
        } else {
            minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun addBitmapReference(bitmap: Bitmap) {
        activeBitmaps.add(WeakReference(bitmap))
    }
    
    private fun cleanupBitmapReferences() {
        val iterator = activeBitmaps.iterator()
        var cleanedCount = 0
        
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val bitmap = ref.get()
            if (bitmap == null || bitmap.isRecycled) {
                iterator.remove()
                cleanedCount++
            }
        }
        
        if (cleanedCount > 0) {
            Log.d(TAG, "Cleaned up $cleanedCount bitmap references")
        }
    }
}

/**
 * Data class containing memory information
 */
data class MemoryInfo(
    val totalMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long,
    val usedMemory: Long,
    val availableMemory: Long,
    val systemAvailableMemory: Long,
    val isLowMemory: Boolean
)

/**
 * Device optimization levels
 */
enum class DeviceOptimization {
    LOW_PERFORMANCE,
    MEDIUM_PERFORMANCE,
    HIGH_PERFORMANCE
}

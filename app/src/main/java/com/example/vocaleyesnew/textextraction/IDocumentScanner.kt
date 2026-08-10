package com.example.vocaleyesnew.textextraction

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * Clean minimal interface for document scanning operations.
 * This interface defines the core functionality needed for document detection,
 * cropping, and OCR preparation.
 */
interface IDocumentScanner {
    /**
     * Detect document corners in a bitmap image.
     * 
     * @param bitmap The input image to analyze
     * @return DocumentCorners object containing corner points and confidence, or null if no document detected
     */
    fun detect(bitmap: Bitmap): DocumentCorners?

    /**
     * Crop and correct perspective of a document based on detected corners.
     * 
     * @param bitmap The original bitmap containing the document
     * @param corners The detected corners of the document
     * @return Cropped and perspective-corrected bitmap, or null if operation fails
     */
    fun cropAndCorrect(bitmap: Bitmap, corners: DocumentCorners): Bitmap?

    /**
     * Enhance a bitmap for optimal OCR performance.
     * This includes contrast adjustment, noise reduction, and other preprocessing steps.
     * 
     * @param bitmap The input bitmap to enhance
     * @return Enhanced bitmap optimized for OCR
     */
    fun enhanceForOcr(bitmap: Bitmap): Bitmap
}

/**
 * Data class representing the four corners of a detected document.
 * 
 * @param topLeft Top-left corner point
 * @param topRight Top-right corner point  
 * @param bottomLeft Bottom-left corner point
 * @param bottomRight Bottom-right corner point
 * @param confidence Detection confidence percentage (0-100)
 */
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomLeft: PointF,
    val bottomRight: PointF,
    val confidence: Float = 0f
)

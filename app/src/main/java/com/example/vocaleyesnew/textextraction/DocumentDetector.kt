package com.example.vocaleyesnew.textextraction

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlin.math.*

data class ContourPoint(
    val x: Int,
    val y: Int,
    val strength: Float = 0f
)

class DocumentDetector(private val context: Context) : IDocumentScanner {
    
    companion object {
        private const val TAG = "DocumentDetector"
        private const val MIN_CONTOUR_AREA = 0.1f // Minimum 10% of image area
        private const val MAX_CONTOUR_AREA = 0.95f // Maximum 95% of image area
        private const val MIN_CONFIDENCE = 25f // Minimum confidence to accept detection
    }
    
    // IDocumentScanner interface implementations
    override fun detect(bitmap: Bitmap): DocumentCorners? {
        return detectDocument(bitmap)
    }
    
    override fun cropAndCorrect(bitmap: Bitmap, corners: DocumentCorners): Bitmap? {
        return cropAndCorrectPerspective(bitmap, corners)
    }
    
    override fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        return enhanceForOCR(bitmap)
    }
    
    // Legacy method for backward compatibility
    fun detectDocument(bitmap: Bitmap): DocumentCorners? {
        return try {
            Log.d(TAG, "Starting optimized document detection on ${bitmap.width}x${bitmap.height} image")
            
            // Resize image for processing - use smaller size for better performance
            val processedBitmap = if (bitmap.width > 640 || bitmap.height > 480) {
                resizeImage(bitmap, 640)
            } else {
                bitmap
            }
            
            // Use only the most efficient method for real-time detection
            var result: DocumentCorners? = null
            
            // Try simple edge-based detection first (fastest)
            result = detectDocumentByEdges(processedBitmap)
            
            // Only try contour detection if edge detection failed or confidence is too low
            if (result == null || result.confidence < MIN_CONFIDENCE) {
                result = detectDocumentByContoursSimplified(processedBitmap)
            }
            
            // Scale coordinates back to original size if image was resized
            result = result?.let { scaleDocumentCorners(it, processedBitmap, bitmap) }
            
            if (result != null) {
                Log.d(TAG, "Document detected with confidence: ${result.confidence}%")
            } else {
                Log.d(TAG, "No document detected")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting document", e)
            null
        }
    }
    
    private fun detectEdges(bitmap: Bitmap): Bitmap {
        // Convert to grayscale
        val grayscale = toGrayscale(bitmap)
        
        // Apply simple edge detection using Sobel-like operator
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val edgePixels = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // Sobel X
                val gx = (-pixels[(y-1)*width + x-1] + pixels[(y-1)*width + x+1]) +
                        (-2*pixels[y*width + x-1] + 2*pixels[y*width + x+1]) +
                        (-pixels[(y+1)*width + x-1] + pixels[(y+1)*width + x+1])
                
                // Sobel Y
                val gy = (-pixels[(y-1)*width + x-1] - 2*pixels[(y-1)*width + x] - pixels[(y-1)*width + x+1]) +
                        (pixels[(y+1)*width + x-1] + 2*pixels[(y+1)*width + x] + pixels[(y+1)*width + x+1])
                
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val intensity = minOf(255, magnitude)
                
                edgePixels[idx] = if (intensity > 100) Color.WHITE else Color.BLACK
            }
        }
        
        return Bitmap.createBitmap(edgePixels, width, height, Bitmap.Config.ARGB_8888)
    }
    
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = gray
        }
        
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    
    private fun findLargestRectangle(edgesBitmap: Bitmap, originalWidth: Int, originalHeight: Int): DocumentCorners? {
        val width = edgesBitmap.width
        val height = edgesBitmap.height
        val pixels = IntArray(width * height)
        edgesBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Find document using improved corner detection
        val documentCorners = findDocumentCorners(pixels, width, height)
        
        return if (documentCorners != null) {
            val confidence = calculateDocumentConfidence(documentCorners, width, height)
            
            // Much more relaxed thresholds for better detection
            if (confidence >= 15f) {
                DocumentCorners(
                    topLeft = documentCorners[0],
                    topRight = documentCorners[1],
                    bottomLeft = documentCorners[2], 
                    bottomRight = documentCorners[3],
                    confidence = confidence
                )
            } else null
        } else {
            // Fallback: try to find largest rectangular region with edges
            findLargestEdgeRegion(pixels, width, height)
        }
    }
    
    private fun findDocumentCorners(pixels: IntArray, width: Int, height: Int): Array<PointF>? {
        // Find potential corner points by looking for intersections of strong edge lines
        val cornerCandidates = mutableListOf<PointF>()
        
        // Sample grid points and look for corner-like features
        val stepSize = minOf(width, height) / 20 // Sample every 5% of image
        
        for (y in stepSize until height - stepSize step stepSize) {
            for (x in stepSize until width - stepSize step stepSize) {
                if (isCornerPoint(pixels, x, y, width, height)) {
                    cornerCandidates.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }
        
        // If we found potential corners, try to form a quadrilateral
        if (cornerCandidates.size >= 4) {
            return findBestQuadrilateral(cornerCandidates, width, height)
        }
        
        return null
    }
    
    private fun isCornerPoint(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        // Check if this point has strong edges in perpendicular directions
        val radius = 3
        var horizontalEdges = 0
        var verticalEdges = 0
        
        // Check horizontal edges
        for (dx in -radius..radius) {
            val checkX = x + dx
            if (checkX >= 0 && checkX < width) {
                if (pixels[y * width + checkX] == Color.WHITE) {
                    horizontalEdges++
                }
            }
        }
        
        // Check vertical edges
        for (dy in -radius..radius) {
            val checkY = y + dy
            if (checkY >= 0 && checkY < height) {
                if (pixels[checkY * width + x] == Color.WHITE) {
                    verticalEdges++
                }
            }
        }
        
        // A corner should have moderate edge activity in both directions
        return horizontalEdges >= 2 && verticalEdges >= 2
    }
    
    private fun findBestQuadrilateral(candidates: List<PointF>, width: Int, height: Int): Array<PointF>? {
        if (candidates.size < 4) return null
        
        // Sort candidates to find corner points
        // Top-left: smallest x+y
        // Top-right: largest x, smallest y  
        // Bottom-left: smallest x, largest y
        // Bottom-right: largest x+y
        
        val topLeft = candidates.minByOrNull { it.x + it.y }
        val bottomRight = candidates.maxByOrNull { it.x + it.y }
        val topRight = candidates.minByOrNull { it.y - it.x }
        val bottomLeft = candidates.maxByOrNull { it.y - it.x }
        
        return if (topLeft != null && topRight != null && bottomLeft != null && bottomRight != null) {
            // Validate that we have a reasonable quadrilateral
            val corners = arrayOf(topLeft, topRight, bottomLeft, bottomRight)
            if (isValidQuadrilateral(corners, width, height)) {
                corners
            } else null
        } else null
    }
    
    private fun isValidQuadrilateral(corners: Array<PointF>, width: Int, height: Int): Boolean {
        val tl = corners[0]
        val tr = corners[1] 
        val bl = corners[2]
        val br = corners[3]
        
        // Check that corners form a reasonable rectangle shape
        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        
        // Be more conservative - require larger minimum dimensions to avoid over-cropping
        val minDimension = minOf(width, height) * 0.4f // At least 40% of image
        val maxDimension = maxOf(width, height) * 0.98f // At most 98% of image
        
        return widthTop > minDimension && widthBottom > minDimension &&
               heightLeft > minDimension && heightRight > minDimension &&
               widthTop < maxDimension && widthBottom < maxDimension &&
               heightLeft < maxDimension && heightRight < maxDimension
    }
    
    private fun findLargestEdgeRegion(pixels: IntArray, width: Int, height: Int): DocumentCorners? {
        // Fallback method: find bounding box of edge concentrations
        val edgeRegions = mutableListOf<Rect>()
        val blockSize = minOf(width, height) / 10
        
        for (y in 0 until height - blockSize step blockSize) {
            for (x in 0 until width - blockSize step blockSize) {
                val edgeCount = countEdgesInBlock(pixels, x, y, blockSize, width, height)
                val threshold = blockSize * blockSize * 0.05 // 5% edges in block
                
                if (edgeCount > threshold) {
                    edgeRegions.add(Rect(x, y, x + blockSize, y + blockSize))
                }
            }
        }
        
        if (edgeRegions.isNotEmpty()) {
            // Find bounding rectangle of all edge regions
            val left = edgeRegions.minOf { it.left }.toFloat()
            val top = edgeRegions.minOf { it.top }.toFloat() 
            val right = edgeRegions.maxOf { it.right }.toFloat()
            val bottom = edgeRegions.maxOf { it.bottom }.toFloat()
            
            // Add some padding
            val paddingX = (right - left) * 0.05f
            val paddingY = (bottom - top) * 0.05f
            
            val finalLeft = maxOf(0f, left - paddingX)
            val finalTop = maxOf(0f, top - paddingY)
            val finalRight = minOf(width.toFloat(), right + paddingX)
            val finalBottom = minOf(height.toFloat(), bottom + paddingY)
            
            val area = (finalRight - finalLeft) * (finalBottom - finalTop)
            val totalArea = width * height
            val areaRatio = area / totalArea
            
            // Much more relaxed threshold - accept any reasonable sized region
            if (areaRatio >= 0.1f && areaRatio <= 0.9f) {
                val confidence = (areaRatio * 50 + 10).coerceIn(0f, 100f)
                
                return DocumentCorners(
                    topLeft = PointF(finalLeft, finalTop),
                    topRight = PointF(finalRight, finalTop),
                    bottomLeft = PointF(finalLeft, finalBottom),
                    bottomRight = PointF(finalRight, finalBottom),
                    confidence = confidence
                )
            }
        }
        
        return null
    }
    
    private fun countEdgesInBlock(pixels: IntArray, startX: Int, startY: Int, blockSize: Int, width: Int, height: Int): Int {
        var edgeCount = 0
        val endX = minOf(startX + blockSize, width)
        val endY = minOf(startY + blockSize, height)
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                if (pixels[y * width + x] == Color.WHITE) {
                    edgeCount++
                }
            }
        }
        
        return edgeCount
    }
    
    private fun calculateDocumentConfidence(corners: Array<PointF>, width: Int, height: Int): Float {
        val tl = corners[0]
        val tr = corners[1]
        val bl = corners[2] 
        val br = corners[3]
        
        // Calculate area ratio
        val docWidth = maxOf(distance(tl, tr), distance(bl, br))
        val docHeight = maxOf(distance(tl, bl), distance(tr, br))
        val docArea = docWidth * docHeight
        val totalArea = width * height
        val areaRatio = docArea / totalArea
        
        // Calculate shape regularity (how rectangular)
        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        
        val widthConsistency = 1f - abs(widthTop - widthBottom) / maxOf(widthTop, widthBottom)
        val heightConsistency = 1f - abs(heightLeft - heightRight) / maxOf(heightLeft, heightRight)
        val shapeRegularity = (widthConsistency + heightConsistency) / 2f
        
        // Combined confidence score
        return (areaRatio * 40 + shapeRegularity * 60).coerceIn(0f, 100f)
    }
    
    fun cropAndCorrectPerspective(bitmap: Bitmap, corners: DocumentCorners): Bitmap? {
        return try {
            Log.d(TAG, "Starting perspective correction with corners: TL(${corners.topLeft.x}, ${corners.topLeft.y}) TR(${corners.topRight.x}, ${corners.topRight.y}) BL(${corners.bottomLeft.x}, ${corners.bottomLeft.y}) BR(${corners.bottomRight.x}, ${corners.bottomRight.y})")
            
            // Calculate target dimensions for the corrected document
            val targetWidth = maxOf(
                distance(corners.topLeft, corners.topRight),
                distance(corners.bottomLeft, corners.bottomRight)
            ).toInt()
            
            val targetHeight = maxOf(
                distance(corners.topLeft, corners.bottomLeft),
                distance(corners.topRight, corners.bottomRight)
            ).toInt()
            
            Log.d(TAG, "Target dimensions: ${targetWidth}x${targetHeight}")
            
            if (targetWidth < 50 || targetHeight < 50) {
                Log.w(TAG, "Target dimensions too small, falling back to simple crop")
                return simpleCrop(bitmap, corners)
            }
            
            // Perform perspective transformation
            val corrected = perspectiveTransform(
                bitmap,
                corners.topLeft, corners.topRight, corners.bottomLeft, corners.bottomRight,
                targetWidth, targetHeight
            )
            
            if (corrected != null) {
                Log.d(TAG, "Perspective correction successful, applying OCR enhancement")
                enhanceForOCR(corrected)
            } else {
                Log.w(TAG, "Perspective correction failed, falling back to simple crop")
                simpleCrop(bitmap, corners)?.let { enhanceForOCR(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in perspective correction", e)
            // Fallback to simple cropping
            simpleCrop(bitmap, corners)?.let { enhanceForOCR(it) }
        }
    }
    
    private fun enhanceForOCR(bitmap: Bitmap): Bitmap {
        return try {
            // Create a new bitmap instead of modifying the original
            val width = bitmap.width
            val height = bitmap.height
            
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Invalid bitmap dimensions: ${width}x${height}")
                return bitmap
            }
            
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Apply contrast enhancement
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Increase contrast with safer calculation
                val enhancedR = ((r - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                val enhancedG = ((g - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                val enhancedB = ((b - 128) * 1.2 + 128).toInt().coerceIn(0, 255)
                
                pixels[i] = Color.rgb(enhancedR, enhancedG, enhancedB)
            }
            
            // Create a new bitmap with enhanced pixels
            val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            enhancedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            Log.d(TAG, "OCR enhancement completed successfully")
            enhancedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing image for OCR, returning original", e)
            bitmap // Return original bitmap if enhancement fails
        }
    }
    
    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }
    
    // Helper method to resize image for processing
    private fun resizeImage(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) return bitmap
        
        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    // Scale document corners back to original image size
    private fun scaleDocumentCorners(corners: DocumentCorners, processedBitmap: Bitmap, originalBitmap: Bitmap): DocumentCorners {
        if (processedBitmap.width == originalBitmap.width && processedBitmap.height == originalBitmap.height) {
            return corners
        }
        
        val scaleX = originalBitmap.width.toFloat() / processedBitmap.width
        val scaleY = originalBitmap.height.toFloat() / processedBitmap.height
        
        return DocumentCorners(
            topLeft = PointF(corners.topLeft.x * scaleX, corners.topLeft.y * scaleY),
            topRight = PointF(corners.topRight.x * scaleX, corners.topRight.y * scaleY),
            bottomLeft = PointF(corners.bottomLeft.x * scaleX, corners.bottomLeft.y * scaleY),
            bottomRight = PointF(corners.bottomRight.x * scaleX, corners.bottomRight.y * scaleY),
            confidence = corners.confidence
        )
    }
    
    // Simplified contour detection for better performance
    private fun detectDocumentByContoursSimplified(bitmap: Bitmap): DocumentCorners? {
        Log.d(TAG, "Attempting simplified contour-based detection")
        
        // Use simple edge detection instead of complex morphological operations
        val edges = detectEdges(bitmap)
        
        // Find largest rectangular region directly
        return findLargestRectangle(edges, bitmap.width, bitmap.height)
    }
    
    // Method 1: Advanced contour-based detection
    private fun detectDocumentByContours(bitmap: Bitmap): DocumentCorners? {
        Log.d(TAG, "Attempting contour-based detection")
        
        // Convert to grayscale and apply Gaussian blur to reduce noise
        val grayscale = toGrayscaleEnhanced(bitmap)
        
        // Apply adaptive thresholding to get binary image
        val binary = adaptiveThreshold(grayscale)
        
        // Find contours
        val contours = findContours(binary)
        
        // Filter contours to find document-like shapes
        val documentContour = findLargestDocumentContour(contours, bitmap.width, bitmap.height)
        
        return documentContour?.let { contour ->
            // Approximate contour to quadrilateral
            val quad = approximateToQuadrilateral(contour)
            
            if (quad != null && quad.size == 4) {
                val corners = orderCorners(quad)
                val confidence = calculateContourConfidence(corners, contour, bitmap.width, bitmap.height)
                
                DocumentCorners(
                    topLeft = corners[0],
                    topRight = corners[1],
                    bottomLeft = corners[2],
                    bottomRight = corners[3],
                    confidence = confidence
                )
            } else null
        }
    }
    
    // Method 2: Enhanced edge-based detection
    private fun detectDocumentByEdges(bitmap: Bitmap): DocumentCorners? {
        Log.d(TAG, "Attempting edge-based detection")
        
        // Multi-scale edge detection
        val edges = detectEdgesEnhanced(bitmap)
        
        // Morphological operations to close gaps
        val morphed = morphologicalClose(edges)
        
        // Find rectangular regions
        return findLargestRectangle(morphed, bitmap.width, bitmap.height)
    }
    
    // Method 3: Hough line-based detection
    private fun detectDocumentByLines(bitmap: Bitmap): DocumentCorners? {
        Log.d(TAG, "Attempting line-based detection")
        
        val edges = detectEdgesEnhanced(bitmap)
        val lines = detectLines(edges)
        
        if (lines.size >= 4) {
            return findDocumentFromLines(lines, bitmap.width, bitmap.height)
        }
        
        return null
    }
    
    // Enhanced grayscale conversion with noise reduction
    private fun toGrayscaleEnhanced(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Gaussian blur kernel (3x3)
        val kernel = arrayOf(
            arrayOf(1f, 2f, 1f),
            arrayOf(2f, 4f, 2f),
            arrayOf(1f, 2f, 1f)
        )
        val kernelSum = 16f
        
        val blurred = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in 0..2) {
                    for (kx in 0..2) {
                        val pixel = pixels[(y + ky - 1) * width + (x + kx - 1)]
                        val weight = kernel[ky][kx]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                r /= kernelSum
                g /= kernelSum
                b /= kernelSum
                
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                blurred[y * width + x] = gray
            }
        }
        
        return Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888)
    }
    
    // Adaptive thresholding for better binary conversion
    private fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val binary = IntArray(width * height)
        val blockSize = 21 // Must be odd
        val c = 15 // Constant subtracted from mean
        val half = blockSize / 2
        
        for (y in half until height - half) {
            for (x in half until width - half) {
                var sum = 0
                var count = 0
                
                // Calculate mean in local neighborhood
                for (dy in -half..half) {
                    for (dx in -half..half) {
                        sum += pixels[(y + dy) * width + (x + dx)] and 0xFF
                        count++
                    }
                }
                
                val mean = sum / count
                val threshold = mean - c
                val currentPixel = pixels[y * width + x] and 0xFF
                
                binary[y * width + x] = if (currentPixel > threshold) Color.WHITE else Color.BLACK
            }
        }
        
        return Bitmap.createBitmap(binary, width, height, Bitmap.Config.ARGB_8888)
    }
    
    // Enhanced edge detection with multiple operators
    private fun detectEdgesEnhanced(bitmap: Bitmap): Bitmap {
        val grayscale = toGrayscaleEnhanced(bitmap)
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val edges = IntArray(width * height)
        
        // Improved Sobel operator with better thresholding
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // Sobel X (vertical edges)
                val gx = (-1 * (pixels[(y-1)*width + x-1] and 0xFF) +
                          -2 * (pixels[y*width + x-1] and 0xFF) +
                          -1 * (pixels[(y+1)*width + x-1] and 0xFF) +
                           1 * (pixels[(y-1)*width + x+1] and 0xFF) +
                           2 * (pixels[y*width + x+1] and 0xFF) +
                           1 * (pixels[(y+1)*width + x+1] and 0xFF))
                
                // Sobel Y (horizontal edges)
                val gy = (-1 * (pixels[(y-1)*width + x-1] and 0xFF) +
                          -2 * (pixels[(y-1)*width + x] and 0xFF) +
                          -1 * (pixels[(y-1)*width + x+1] and 0xFF) +
                           1 * (pixels[(y+1)*width + x-1] and 0xFF) +
                           2 * (pixels[(y+1)*width + x] and 0xFF) +
                           1 * (pixels[(y+1)*width + x+1] and 0xFF))
                
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                
                // Dynamic threshold based on local statistics
                val threshold = 80
                edges[idx] = if (magnitude > threshold) Color.WHITE else Color.BLACK
            }
        }
        
        return Bitmap.createBitmap(edges, width, height, Bitmap.Config.ARGB_8888)
    }
    
    // Morphological closing to fill gaps in edges
    private fun morphologicalClose(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val dilated = IntArray(width * height)
        val eroded = IntArray(width * height)
        
        // Dilation (expand white pixels)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var hasWhite = false
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (pixels[(y + dy) * width + (x + dx)] == Color.WHITE) {
                            hasWhite = true
                            break
                        }
                    }
                    if (hasWhite) break
                }
                
                dilated[y * width + x] = if (hasWhite) Color.WHITE else Color.BLACK
            }
        }
        
        // Erosion (shrink white pixels)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var allWhite = true
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dilated[(y + dy) * width + (x + dx)] != Color.WHITE) {
                            allWhite = false
                            break
                        }
                    }
                    if (!allWhite) break
                }
                
                eroded[y * width + x] = if (allWhite) Color.WHITE else Color.BLACK
            }
        }
        
        return Bitmap.createBitmap(eroded, width, height, Bitmap.Config.ARGB_8888)
    }
    
    // Simplified contour finding
    private fun findContours(binary: Bitmap): List<List<PointF>> {
        val width = binary.width
        val height = binary.height
        val pixels = IntArray(width * height)
        binary.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val visited = BooleanArray(width * height)
        val contours = mutableListOf<List<PointF>>()
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                if (pixels[idx] == Color.WHITE && !visited[idx]) {
                    val contour = traceContour(pixels, visited, x, y, width, height)
                    if (contour.size > 10) { // Filter out noise
                        contours.add(contour)
                    }
                }
            }
        }
        
        return contours.sortedByDescending { it.size }
    }
    
    // Simple contour tracing
    private fun traceContour(pixels: IntArray, visited: BooleanArray, startX: Int, startY: Int, width: Int, height: Int): List<PointF> {
        val contour = mutableListOf<PointF>()
        val stack = mutableListOf(Pair(startX, startY))
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val idx = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[idx] || pixels[idx] != Color.WHITE) {
                continue
            }
            
            visited[idx] = true
            contour.add(PointF(x.toFloat(), y.toFloat()))
            
            // Add 8-connected neighbors
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx != 0 || dy != 0) {
                        stack.add(Pair(x + dx, y + dy))
                    }
                }
            }
        }
        
        return contour
    }
    
    // Find largest document-like contour
    private fun findLargestDocumentContour(contours: List<List<PointF>>, width: Int, height: Int): List<PointF>? {
        val imageArea = width * height
        
        return contours.firstOrNull { contour ->
            val area = calculateContourArea(contour)
            val areaRatio = area / imageArea
            
            areaRatio >= MIN_CONTOUR_AREA && areaRatio <= MAX_CONTOUR_AREA
        }
    }
    
    // Calculate contour area using shoelace formula
    private fun calculateContourArea(contour: List<PointF>): Float {
        if (contour.size < 3) return 0f
        
        var area = 0f
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            area += contour[i].x * contour[j].y
            area -= contour[j].x * contour[i].y
        }
        
        return abs(area) / 2f
    }
    
    // Approximate contour to quadrilateral using Douglas-Peucker algorithm
    private fun approximateToQuadrilateral(contour: List<PointF>): List<PointF>? {
        if (contour.size < 4) return null
        
        var epsilon = 0.02f * calculateContourPerimeter(contour)
        var approximated = douglasPeucker(contour, epsilon)
        
        // Try different epsilon values to get exactly 4 points
        var attempts = 0
        while (approximated.size != 4 && attempts < 10) {
            epsilon *= if (approximated.size > 4) 1.5f else 0.7f
            approximated = douglasPeucker(contour, epsilon)
            attempts++
        }
        
        return if (approximated.size == 4) approximated else null
    }
    
    // Calculate contour perimeter
    private fun calculateContourPerimeter(contour: List<PointF>): Float {
        if (contour.size < 2) return 0f
        
        var perimeter = 0f
        for (i in 0 until contour.size - 1) {
            perimeter += distance(contour[i], contour[i + 1])
        }
        perimeter += distance(contour.last(), contour.first())
        
        return perimeter
    }
    
    // Douglas-Peucker algorithm for contour approximation
    private fun douglasPeucker(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size <= 2) return points
        
        val first = points.first()
        val last = points.last()
        
        var maxDistance = 0f
        var maxIndex = 0
        
        for (i in 1 until points.size - 1) {
            val distance = pointToLineDistance(points[i], first, last)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }
        
        if (maxDistance > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIndex + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIndex, points.size), epsilon)
            
            return left.dropLast(1) + right
        } else {
            return listOf(first, last)
        }
    }
    
    // Distance from point to line
    private fun pointToLineDistance(point: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val A = lineEnd.y - lineStart.y
        val B = lineStart.x - lineEnd.x
        val C = lineEnd.x * lineStart.y - lineStart.x * lineEnd.y
        
        return abs(A * point.x + B * point.y + C) / sqrt(A * A + B * B)
    }
    
    // Order corners in consistent manner (top-left, top-right, bottom-left, bottom-right)
    private fun orderCorners(corners: List<PointF>): Array<PointF> {
        val sorted = corners.sortedWith(compareBy { it.x + it.y })
        
        val topLeft = sorted[0]
        val bottomRight = sorted[3]
        
        val remaining = corners.filter { it != topLeft && it != bottomRight }
        val topRight = remaining.minByOrNull { it.y - it.x } ?: remaining[0]
        val bottomLeft = remaining.maxByOrNull { it.y - it.x } ?: remaining[1]
        
        return arrayOf(topLeft, topRight, bottomLeft, bottomRight)
    }
    
    // Calculate confidence for contour-based detection
    private fun calculateContourConfidence(corners: Array<PointF>, contour: List<PointF>, width: Int, height: Int): Float {
        val area = calculateQuadrilateralArea(corners)
        val imageArea = width * height
        val areaRatio = area / imageArea
        
        val rectangularity = calculateRectangularity(corners)
        val edgeStrength = contour.size.toFloat() / (width + height) // Normalized edge strength
        
        return (areaRatio * 30 + rectangularity * 50 + edgeStrength * 20).coerceIn(0f, 100f)
    }
    
    // Calculate area of quadrilateral
    private fun calculateQuadrilateralArea(corners: Array<PointF>): Float {
        val points = corners.toList() + corners[0] // Close the polygon
        return calculateContourArea(points)
    }
    
    // Calculate how rectangular a shape is (0-1)
    private fun calculateRectangularity(corners: Array<PointF>): Float {
        val tl = corners[0]
        val tr = corners[1]
        val bl = corners[2]
        val br = corners[3]
        
        // Calculate angles
        val angle1 = calculateAngle(tl, tr, br)
        val angle2 = calculateAngle(tr, br, bl)
        val angle3 = calculateAngle(br, bl, tl)
        val angle4 = calculateAngle(bl, tl, tr)
        
        // How close to 90 degrees are the angles?
        val angleDiffs = listOf(angle1, angle2, angle3, angle4).map { abs(it - 90) }
        val avgAngleDiff = angleDiffs.average().toFloat()
        
        return (1f - avgAngleDiff / 90f).coerceIn(0f, 1f)
    }
    
    // Calculate angle between three points in degrees
    private fun calculateAngle(p1: PointF, p2: PointF, p3: PointF): Float {
        val v1 = PointF(p1.x - p2.x, p1.y - p2.y)
        val v2 = PointF(p3.x - p2.x, p3.y - p2.y)
        
        val dot = v1.x * v2.x + v1.y * v2.y
        val mag1 = sqrt(v1.x * v1.x + v1.y * v1.y)
        val mag2 = sqrt(v2.x * v2.x + v2.y * v2.y)
        
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        val cos = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }
    
    // Simplified line detection (would need full Hough transform for better results)
    private fun detectLines(edges: Bitmap): List<Pair<PointF, PointF>> {
        // This is a simplified version - a full Hough line transform would be more robust
        val lines = mutableListOf<Pair<PointF, PointF>>()
        
        val width = edges.width
        val height = edges.height
        val pixels = IntArray(width * height)
        edges.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Find long horizontal and vertical lines
        // Horizontal lines
        for (y in 0 until height step 10) {
            var lineStart: PointF? = null
            var lineLength = 0
            
            for (x in 0 until width) {
                if (pixels[y * width + x] == Color.WHITE) {
                    if (lineStart == null) {
                        lineStart = PointF(x.toFloat(), y.toFloat())
                    }
                    lineLength++
                } else {
                    if (lineStart != null && lineLength > width * 0.3) {
                        lines.add(Pair(lineStart, PointF((x - 1).toFloat(), y.toFloat())))
                    }
                    lineStart = null
                    lineLength = 0
                }
            }
            
            if (lineStart != null && lineLength > width * 0.3) {
                lines.add(Pair(lineStart, PointF((width - 1).toFloat(), y.toFloat())))
            }
        }
        
        // Vertical lines
        for (x in 0 until width step 10) {
            var lineStart: PointF? = null
            var lineLength = 0
            
            for (y in 0 until height) {
                if (pixels[y * width + x] == Color.WHITE) {
                    if (lineStart == null) {
                        lineStart = PointF(x.toFloat(), y.toFloat())
                    }
                    lineLength++
                } else {
                    if (lineStart != null && lineLength > height * 0.3) {
                        lines.add(Pair(lineStart, PointF(x.toFloat(), (y - 1).toFloat())))
                    }
                    lineStart = null
                    lineLength = 0
                }
            }
            
            if (lineStart != null && lineLength > height * 0.3) {
                lines.add(Pair(lineStart, PointF(x.toFloat(), (height - 1).toFloat())))
            }
        }
        
        return lines
    }
    
    // Find document from detected lines
    private fun findDocumentFromLines(lines: List<Pair<PointF, PointF>>, width: Int, height: Int): DocumentCorners? {
        // Group lines into horizontal and vertical
        val horizontalLines = lines.filter { line ->
            abs(line.first.y - line.second.y) < 5
        }
        val verticalLines = lines.filter { line ->
            abs(line.first.x - line.second.x) < 5
        }
        
        if (horizontalLines.size < 2 || verticalLines.size < 2) {
            return null
        }
        
        // Find extreme lines
        val topLine = horizontalLines.minByOrNull { it.first.y }
        val bottomLine = horizontalLines.maxByOrNull { it.first.y }
        val leftLine = verticalLines.minByOrNull { it.first.x }
        val rightLine = verticalLines.maxByOrNull { it.first.x }
        
        if (topLine != null && bottomLine != null && leftLine != null && rightLine != null) {
            val topY = topLine.first.y
            val bottomY = bottomLine.first.y
            val leftX = leftLine.first.x
            val rightX = rightLine.first.x
            
            val corners = DocumentCorners(
                topLeft = PointF(leftX, topY),
                topRight = PointF(rightX, topY),
                bottomLeft = PointF(leftX, bottomY),
                bottomRight = PointF(rightX, bottomY),
                confidence = 70f // Static confidence for line-based detection
            )
            
            // Validate the found rectangle
            val rectWidth = rightX - leftX
            val rectHeight = bottomY - topY
            
            if (rectWidth > width * 0.2 && rectHeight > height * 0.2 &&
                rectWidth < width * 0.95 && rectHeight < height * 0.95) {
                return corners
            }
        }
        
        return null
    }
    
    // Simple cropping fallback method
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
    
    // Perspective transformation using bilinear interpolation
    private fun perspectiveTransform(
        bitmap: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomLeft: PointF,
        bottomRight: PointF,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap? {
        return try {
            Log.d(TAG, "Performing perspective transformation to ${outputWidth}x${outputHeight}")
            
            val inputWidth = bitmap.width
            val inputHeight = bitmap.height
            val inputPixels = IntArray(inputWidth * inputHeight)
            bitmap.getPixels(inputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            
            val outputPixels = IntArray(outputWidth * outputHeight)
            
            // Calculate transformation matrix coefficients
            val matrix = calculatePerspectiveMatrix(
                topLeft, topRight, bottomLeft, bottomRight,
                outputWidth.toFloat(), outputHeight.toFloat()
            )
            
            if (matrix == null) {
                Log.e(TAG, "Failed to calculate perspective matrix")
                return null
            }
            
            // Apply perspective transformation
            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    // Map output coordinates to input coordinates
                    val srcPoint = applyPerspectiveTransform(
                        x.toFloat(), y.toFloat(), matrix
                    )
                    
                    // Bilinear interpolation
                    val pixel = bilinearInterpolate(
                        inputPixels, inputWidth, inputHeight,
                        srcPoint.x, srcPoint.y
                    )
                    
                    outputPixels[y * outputWidth + x] = pixel
                }
            }
            
            Bitmap.createBitmap(outputPixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Error in perspective transformation", e)
            null
        }
    }
    
    // Calculate perspective transformation matrix
    private fun calculatePerspectiveMatrix(
        tl: PointF, tr: PointF, bl: PointF, br: PointF,
        outputWidth: Float, outputHeight: Float
    ): FloatArray? {
        try {
            // Source points (quadrilateral corners)
            val src = floatArrayOf(
                tl.x, tl.y,
                tr.x, tr.y,
                bl.x, bl.y,
                br.x, br.y
            )
            
            // Destination points (rectangle)
            val dst = floatArrayOf(
                0f, 0f,
                outputWidth, 0f,
                0f, outputHeight,
                outputWidth, outputHeight
            )
            
            // Calculate the perspective transformation matrix using Android's Matrix class
            val matrix = Matrix()
            if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                val values = FloatArray(9)
                matrix.getValues(values)
                return values
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating perspective matrix", e)
            return null
        }
    }
    
    // Apply perspective transformation to a point
    private fun applyPerspectiveTransform(x: Float, y: Float, matrix: FloatArray): PointF {
        val w = matrix[6] * x + matrix[7] * y + matrix[8]
        val srcX = (matrix[0] * x + matrix[1] * y + matrix[2]) / w
        val srcY = (matrix[3] * x + matrix[4] * y + matrix[5]) / w
        return PointF(srcX, srcY)
    }
    
    // Bilinear interpolation for smooth pixel mapping
    private fun bilinearInterpolate(
        pixels: IntArray, width: Int, height: Int,
        x: Float, y: Float
    ): Int {
        if (x < 0 || x >= width - 1 || y < 0 || y >= height - 1) {
            return Color.BLACK // Return black for out-of-bounds
        }
        
        val x1 = x.toInt()
        val y1 = y.toInt()
        val x2 = (x1 + 1).coerceAtMost(width - 1)
        val y2 = (y1 + 1).coerceAtMost(height - 1)
        
        val fx = x - x1
        val fy = y - y1
        
        val p1 = pixels[y1 * width + x1] // top-left
        val p2 = pixels[y1 * width + x2] // top-right
        val p3 = pixels[y2 * width + x1] // bottom-left
        val p4 = pixels[y2 * width + x2] // bottom-right
        
        // Interpolate each channel separately
        val a1 = Color.alpha(p1)
        val r1 = Color.red(p1)
        val g1 = Color.green(p1)
        val b1 = Color.blue(p1)
        
        val a2 = Color.alpha(p2)
        val r2 = Color.red(p2)
        val g2 = Color.green(p2)
        val b2 = Color.blue(p2)
        
        val a3 = Color.alpha(p3)
        val r3 = Color.red(p3)
        val g3 = Color.green(p3)
        val b3 = Color.blue(p3)
        
        val a4 = Color.alpha(p4)
        val r4 = Color.red(p4)
        val g4 = Color.green(p4)
        val b4 = Color.blue(p4)
        
        val a = ((1 - fx) * (1 - fy) * a1 + fx * (1 - fy) * a2 + (1 - fx) * fy * a3 + fx * fy * a4).toInt()
        val r = ((1 - fx) * (1 - fy) * r1 + fx * (1 - fy) * r2 + (1 - fx) * fy * r3 + fx * fy * r4).toInt()
        val g = ((1 - fx) * (1 - fy) * g1 + fx * (1 - fy) * g2 + (1 - fx) * fy * g3 + fx * fy * g4).toInt()
        val b = ((1 - fx) * (1 - fy) * b1 + fx * (1 - fy) * b2 + (1 - fx) * fy * b3 + fx * fy * b4).toInt()
        
        return Color.argb(
            a.coerceIn(0, 255),
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
    }
}

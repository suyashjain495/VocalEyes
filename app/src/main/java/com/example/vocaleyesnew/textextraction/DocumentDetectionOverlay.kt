package com.example.vocaleyesnew.textextraction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun DocumentDetectionOverlay(
    documentCorners: DocumentCorners?,
    previewWidth: Int,
    previewHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw scan guide frame when no document is detected
        if (documentCorners == null || documentCorners.confidence < 30f) {
            drawScanGuideFrame()
        } else {
            drawDocumentFrame(documentCorners, previewWidth, previewHeight)
        }
    }
}

private fun DrawScope.drawScanGuideFrame() {
    val frameColor = Color.White.copy(alpha = 0.8f)
    val cornerColor = Color.Green
    val strokeWidth = 3.dp.toPx()
    val cornerLength = 40.dp.toPx()
    
    val centerX = size.width / 2
    val centerY = size.height / 2
    val frameWidth = size.width * 0.7f
    val frameHeight = size.height * 0.5f
    
    val left = centerX - frameWidth / 2
    val top = centerY - frameHeight / 2
    val right = centerX + frameWidth / 2
    val bottom = centerY + frameHeight / 2
    
    // Draw corner indicators
    val cornerStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    
    // Top-left corner
    drawLine(
        color = cornerColor,
        start = Offset(left, top),
        end = Offset(left + cornerLength, top),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(left, top),
        end = Offset(left, top + cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Top-right corner
    drawLine(
        color = cornerColor,
        start = Offset(right, top),
        end = Offset(right - cornerLength, top),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(right, top),
        end = Offset(right, top + cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Bottom-left corner
    drawLine(
        color = cornerColor,
        start = Offset(left, bottom),
        end = Offset(left + cornerLength, bottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(left, bottom),
        end = Offset(left, bottom - cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Bottom-right corner
    drawLine(
        color = cornerColor,
        start = Offset(right, bottom),
        end = Offset(right - cornerLength, bottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(right, bottom),
        end = Offset(right, bottom - cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Draw dashed frame
    val dashPattern = floatArrayOf(20f, 10f)
    drawRect(
        color = frameColor,
        topLeft = Offset(left, top),
        size = Size(frameWidth, frameHeight),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(dashPattern)
        )
    )
}

private fun DrawScope.drawDocumentFrame(
    corners: DocumentCorners,
    previewWidth: Int,
    previewHeight: Int
) {
    // Calculate scaling factors to fit the camera preview to the canvas
    val scaleX = size.width / previewWidth
    val scaleY = size.height / previewHeight
    
    // Use the minimum scale to maintain aspect ratio
    val scale = min(scaleX, scaleY)
    val offsetX = (size.width - previewWidth * scale) / 2
    val offsetY = (size.height - previewHeight * scale) / 2
    
    // Transform corner coordinates
    val scaledCorners = listOf(
        Offset(
            corners.topLeft.x * scale + offsetX,
            corners.topLeft.y * scale + offsetY
        ),
        Offset(
            corners.topRight.x * scale + offsetX,
            corners.topRight.y * scale + offsetY
        ),
        Offset(
            corners.bottomRight.x * scale + offsetX,
            corners.bottomRight.y * scale + offsetY
        ),
        Offset(
            corners.bottomLeft.x * scale + offsetX,
            corners.bottomLeft.y * scale + offsetY
        )
    )
    
    // Choose color based on confidence
    val frameColor = when {
        corners.confidence >= 80f -> Color.Green
        corners.confidence >= 60f -> Color.Yellow
        corners.confidence >= 40f -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }.copy(alpha = 0.8f)
    
    val fillColor = frameColor.copy(alpha = 0.2f)
    val strokeWidth = 4.dp.toPx()
    
    // Create path for the document outline
    val path = Path().apply {
        moveTo(scaledCorners[0].x, scaledCorners[0].y)
        lineTo(scaledCorners[1].x, scaledCorners[1].y)
        lineTo(scaledCorners[2].x, scaledCorners[2].y)
        lineTo(scaledCorners[3].x, scaledCorners[3].y)
        close()
    }
    
    // Draw filled area
    drawPath(
        path = path,
        color = fillColor
    )
    
    // Draw border
    drawPath(
        path = path,
        color = frameColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    
    // Draw corner indicators
    val cornerRadius = 8.dp.toPx()
    scaledCorners.forEach { corner ->
        drawCircle(
            color = frameColor,
            radius = cornerRadius,
            center = corner
        )
        drawCircle(
            color = Color.White,
            radius = cornerRadius * 0.5f,
            center = corner
        )
    }
    
    // Draw confidence label
    val confidenceText = "${corners.confidence.toInt()}%"
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14.dp.toPx()
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    
    // Draw at top-center of document
    val centerX = (scaledCorners[0].x + scaledCorners[1].x) / 2
    val labelY = scaledCorners[0].y - 10.dp.toPx()
    
    // Create background for confidence label
    val textRect = android.graphics.Rect()
    paint.getTextBounds(confidenceText, 0, confidenceText.length, textRect)
    val textWidth = textRect.width()
    val textHeight = textRect.height()
    
    // Draw background pill
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(centerX - textWidth / 2 - 12.dp.toPx(), labelY - textHeight - 8.dp.toPx()),
        size = Size(textWidth + 24.dp.toPx(), textHeight + 16.dp.toPx()),
        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    )
    
    // Draw text using native canvas
    drawContext.canvas.nativeCanvas.drawText(
        confidenceText,
        centerX - textWidth / 2,
        labelY,
        paint
    )
}

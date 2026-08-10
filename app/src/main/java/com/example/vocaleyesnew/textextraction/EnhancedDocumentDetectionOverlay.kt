package com.example.vocaleyesnew.textextraction

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.*

/**
 * Enhanced document detection overlay with stability feedback and countdown visualization
 */
@Composable
fun EnhancedDocumentDetectionOverlay(
    documentCorners: DocumentCorners?,
    previewWidth: Int,
    previewHeight: Int,
    isStable: Boolean = false,
    isCountingDown: Boolean = false,
    countdownProgress: Float = 0f, // 0.0 to 1.0
    isFrozen: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Animation for pulsing effect during countdown
    val pulsatingAnimation by animateFloatAsState(
        targetValue = if (isCountingDown) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Animation for stability indicator
    val stabilityAnimation by animateFloatAsState(
        targetValue = if (isStable) 1f else 0f,
        animationSpec = tween(300)
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw scan guide frame when no document is detected
        if (documentCorners == null || documentCorners.confidence < 30f) {
            drawScanGuideFrame(isStable, stabilityAnimation)
        } else {
            drawEnhancedDocumentFrame(
                corners = documentCorners,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                isStable = isStable,
                isCountingDown = isCountingDown,
                countdownProgress = countdownProgress,
                pulsatingScale = pulsatingAnimation,
                isFrozen = isFrozen
            )
        }
    }
}

private fun DrawScope.drawScanGuideFrame(
    isStable: Boolean,
    stabilityAnimation: Float
) {
    val frameColor = Color.White.copy(alpha = 0.8f)
    val stableColor = Color.Green.copy(alpha = stabilityAnimation)
    val cornerColor = if (isStable) stableColor else Color.Green
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
    
    // Draw corner indicators with stability animation
    val cornerStrokeWidth = strokeWidth * (1f + stabilityAnimation * 0.5f)
    
    // Top-left corner
    drawLine(
        color = cornerColor,
        start = Offset(left, top),
        end = Offset(left + cornerLength, top),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(left, top),
        end = Offset(left, top + cornerLength),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    
    // Top-right corner
    drawLine(
        color = cornerColor,
        start = Offset(right, top),
        end = Offset(right - cornerLength, top),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(right, top),
        end = Offset(right, top + cornerLength),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    
    // Bottom-left corner
    drawLine(
        color = cornerColor,
        start = Offset(left, bottom),
        end = Offset(left + cornerLength, bottom),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(left, bottom),
        end = Offset(left, bottom - cornerLength),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    
    // Bottom-right corner
    drawLine(
        color = cornerColor,
        start = Offset(right, bottom),
        end = Offset(right - cornerLength, bottom),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = Offset(right, bottom),
        end = Offset(right, bottom - cornerLength),
        strokeWidth = cornerStrokeWidth,
        cap = StrokeCap.Round
    )
    
    // Draw dashed frame with stability indicator
    val dashPattern = floatArrayOf(20f, 10f)
    val frameAlpha = 0.5f + stabilityAnimation * 0.3f
    drawRect(
        color = frameColor.copy(alpha = frameAlpha),
        topLeft = Offset(left, top),
        size = Size(frameWidth, frameHeight),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(dashPattern)
        )
    )
}

private fun DrawScope.drawEnhancedDocumentFrame(
    corners: DocumentCorners,
    previewWidth: Int,
    previewHeight: Int,
    isStable: Boolean,
    isCountingDown: Boolean,
    countdownProgress: Float,
    pulsatingScale: Float,
    isFrozen: Boolean
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
    
    // Choose color based on state and confidence
    val baseFrameColor = when {
        isFrozen -> Color.Cyan
        isCountingDown -> Color.Green
        isStable -> Color.Green
        corners.confidence >= 80f -> Color.Green
        corners.confidence >= 60f -> Color.Yellow
        corners.confidence >= 40f -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
    
    val frameColor = baseFrameColor.copy(alpha = 0.9f)
    val fillColor = frameColor.copy(alpha = if (isCountingDown) 0.3f else 0.2f)
    val strokeWidth = if (isCountingDown) 6.dp.toPx() * pulsatingScale else 4.dp.toPx()
    
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
    
    // Draw border with animated effect
    drawPath(
        path = path,
        color = frameColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    
    // Draw countdown progress indicator
    if (isCountingDown && countdownProgress > 0f) {
        drawCountdownProgress(scaledCorners, countdownProgress, frameColor)
    }
    
    // Draw corner indicators with enhanced feedback
    val cornerRadius = if (isCountingDown) 12.dp.toPx() * pulsatingScale else 8.dp.toPx()
    val cornerColor = if (isFrozen) Color.Cyan else frameColor
    
    scaledCorners.forEach { corner ->
        // Outer circle
        drawCircle(
            color = cornerColor,
            radius = cornerRadius,
            center = corner
        )
        // Inner circle
        drawCircle(
            color = Color.White,
            radius = cornerRadius * 0.5f,
            center = corner
        )
        
        // Stability indicator ring
        if (isStable) {
            drawCircle(
                color = Color.Green.copy(alpha = 0.6f),
                radius = cornerRadius * 1.5f,
                center = corner,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
    
    // Draw status indicators
    drawStatusIndicators(corners, scaledCorners, isStable, isCountingDown, isFrozen)
}

private fun DrawScope.drawCountdownProgress(
    corners: List<Offset>,
    progress: Float,
    color: Color
) {
    val center = Offset(
        corners.map { it.x }.average().toFloat(),
        corners.map { it.y }.average().toFloat()
    )
    
    val radius = 30.dp.toPx()
    val strokeWidth = 6.dp.toPx()
    
    // Background circle
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = radius + strokeWidth / 2,
        center = center
    )
    
    // Progress arc
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round
        )
    )
    
    // Progress percentage text
    val progressText = "${(progress * 100).toInt()}%"
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = 16.dp.toPx()
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    drawContext.canvas.nativeCanvas.drawText(
        progressText,
        center.x,
        center.y + 6.dp.toPx(),
        paint
    )
}

private fun DrawScope.drawStatusIndicators(
    corners: DocumentCorners,
    scaledCorners: List<Offset>,
    isStable: Boolean,
    isCountingDown: Boolean,
    isFrozen: Boolean
) {
    // Calculate position for status indicators
    val centerX = (scaledCorners[0].x + scaledCorners[1].x) / 2
    val labelY = scaledCorners[0].y - 10.dp.toPx()
    
    // Status text
    val statusText = when {
        isFrozen -> "CAPTURED"
        isCountingDown -> "CAPTURING..."
        isStable -> "READY"
        else -> "${corners.confidence.toInt()}%"
    }
    
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = 14.dp.toPx()
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    // Calculate text dimensions
    val textRect = android.graphics.Rect()
    paint.getTextBounds(statusText, 0, statusText.length, textRect)
    val textWidth = textRect.width()
    val textHeight = textRect.height()
    
    // Background color based on status
    val backgroundColor = when {
        isFrozen -> Color.Cyan.copy(alpha = 0.8f)
        isCountingDown -> Color.Green.copy(alpha = 0.8f)
        isStable -> Color.Green.copy(alpha = 0.6f)
        else -> Color.Black.copy(alpha = 0.6f)
    }
    
    // Draw background pill
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(centerX - textWidth / 2 - 12.dp.toPx(), labelY - textHeight - 8.dp.toPx()),
        size = Size(textWidth + 24.dp.toPx(), textHeight + 16.dp.toPx()),
        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    )
    
    // Draw status text
    drawContext.canvas.nativeCanvas.drawText(
        statusText,
        centerX,
        labelY,
        paint
    )
    
    // Draw stability indicator dots
    if (isStable || isCountingDown) {
        val dotRadius = 3.dp.toPx()
        val dotSpacing = 8.dp.toPx()
        val startX = centerX - dotSpacing
        val dotY = labelY + textHeight + 20.dp.toPx()
        
        repeat(3) { index ->
            val dotColor = if (index < 3) Color.Green else Color.Gray.copy(alpha = 0.5f)
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(startX + index * dotSpacing, dotY)
            )
        }
    }
}

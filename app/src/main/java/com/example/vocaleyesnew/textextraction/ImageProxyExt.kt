package com.example.vocaleyesnew.textextraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

fun ImageProxy.toBitmap(): Bitmap {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
    }

    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}

//package com.example.vocaleyesnew.textextraction
//
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.Matrix
//import androidx.camera.core.ImageProxy
//import java.nio.ByteBuffer
//
//fun ImageProxy.toBitmap(): Bitmap {
//    val buffer: ByteBuffer = planes[0].buffer
//    val bytes = ByteArray(buffer.remaining())
//    buffer.get(bytes)
//
//    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//
//    val matrix = Matrix().apply {
//        postRotate(imageInfo.rotationDegrees.toFloat())
//    }
//
//    return Bitmap.createBitmap(
//        bitmap,
//        0,
//        0,
//        bitmap.width,
//        bitmap.height,
//        matrix,
//        true
//    )
//}
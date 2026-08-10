package com.example.vocaleyesnew.facenet_android.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.vocaleyesnew.facenet_android.data.FaceImageRecord
import com.example.vocaleyesnew.facenet_android.data.ImagesVectorDB
import com.example.vocaleyesnew.facenet_android.data.RecognitionMetrics
import com.example.vocaleyesnew.facenet_android.domain.embeddings.FaceNet
import com.example.vocaleyesnew.facenet_android.domain.face_detection.FaceSpoofDetector
import com.example.vocaleyesnew.facenet_android.domain.face_detection.MediapipeFaceDetector
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class ImageVectorUseCase(
    private val mediapipeFaceDetector: MediapipeFaceDetector,
    private val faceSpoofDetector: FaceSpoofDetector,
    private val imagesVectorDB: ImagesVectorDB,
    private val faceNet: FaceNet
) {

    data class FaceRecognitionResult(
        val personName: String,
        val boundingBox: Rect,
        val spoofResult: FaceSpoofDetector.FaceSpoofResult? = null
    )

    // Add the person's image to the database
    suspend fun addImage(personID: Long, personName: String, imageUri: Uri): Result<Boolean> {
        // Perform face-detection and get the cropped face as a Bitmap
        val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
        if (faceDetectionResult.isSuccess) {
            // Get the embedding for the cropped face, and store it
            // in the database, along with `personId` and `personName`
            val embedding = faceNet.getFaceEmbedding(faceDetectionResult.getOrNull()!!)
            val record = FaceImageRecord(
                personID = personID,
                personName = personName
            )
            record.setEmbeddingFromArray(embedding)
            imagesVectorDB.addFaceImageRecord(record)
            return Result.success(true)
        } else {
            return Result.failure(faceDetectionResult.exceptionOrNull()!!)
        }
    }

    // From the given frame, return the name of the person by performing
    // face recognition
    suspend fun getNearestPersonName(
        frameBitmap: Bitmap
    ): Pair<RecognitionMetrics?, List<FaceRecognitionResult>> {
        // Perform face-detection and get the cropped face as a Bitmap
        val (faceDetectionResult, t1) =
            measureTimedValue { mediapipeFaceDetector.getAllCroppedFaces(frameBitmap) }
        val faceRecognitionResults = ArrayList<FaceRecognitionResult>()
        var avgT2 = 0L
        var avgT3 = 0L
        var avgT4 = 0L

        for (result in faceDetectionResult) {
            // Get the embedding for the cropped face (query embedding)
            val (croppedBitmap, boundingBox) = result
            val (embedding, t2) = measureTimedValue { faceNet.getFaceEmbedding(croppedBitmap) }
            avgT2 += t2.toLong(DurationUnit.MILLISECONDS)
            // Perform nearest-neighbor search
            val (recognitionResult, t3) =
                measureTimedValue { imagesVectorDB.getNearestEmbeddingPersonName(embedding) }
            avgT3 += t3.toLong(DurationUnit.MILLISECONDS)
            if (recognitionResult == null) {
                faceRecognitionResults.add(FaceRecognitionResult("Not recognized", boundingBox))
                continue
            }

            val spoofResult = faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
            avgT4 += spoofResult.timeMillis

            // Calculate cosine similarity between the nearest-neighbor
            // and the query embedding
            val distance = cosineDistance(embedding, recognitionResult.faceEmbedding)
            // If the distance > 0.4, we recognize the person
            // else we conclude that the face does not match enough
            if (distance > 0.4) {
                faceRecognitionResults.add(
                    FaceRecognitionResult(recognitionResult.personName, boundingBox, spoofResult)
                )
            } else {
                faceRecognitionResults.add(
                    FaceRecognitionResult("Not recognized", boundingBox, spoofResult)
                )
            }
        }
        val metrics =
            if (faceDetectionResult.isNotEmpty()) {
                RecognitionMetrics(
                    timeFaceDetection = t1.toLong(DurationUnit.MILLISECONDS),
                    timeFaceEmbedding = avgT2 / faceDetectionResult.size,
                    timeVectorSearch = avgT3 / faceDetectionResult.size,
                    timeFaceSpoofDetection = avgT4 / faceDetectionResult.size
                )
            } else {
                null
            }

        return Pair(metrics, faceRecognitionResults)
    }

    // Calculate cosine similarity between a FloatArray and a List<Float>
    private fun cosineDistance(x1: FloatArray, x2: List<Float>): Float {
        if (x1.size != x2.size) return 0f
        
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        
        for (i in x1.indices) {
            mag1 += x1[i].pow(2)
            mag2 += x2[i].pow(2)
            product += x1[i] * x2[i]
        }
        
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        
        // Avoid division by zero
        if (mag1 <= 0 || mag2 <= 0) return 0f
        
        return product / (mag1 * mag2)
    }

    fun removeImages(personID: Long) {
        imagesVectorDB.removeFaceRecordsWithPersonID(personID)
    }
}

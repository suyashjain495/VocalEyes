package com.example.vocaleyesnew.facenet_android.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Convert
import io.objectbox.converter.PropertyConverter

// List<Float> converter for ObjectBox
class FloatListConverter : PropertyConverter<List<Float>, String> {
    override fun convertToEntityProperty(databaseValue: String?): List<Float> {
        if (databaseValue == null || databaseValue.isEmpty()) return emptyList()
        return databaseValue.split(",").map { it.toFloat() }
    }

    override fun convertToDatabaseValue(entityProperty: List<Float>?): String {
        return entityProperty?.joinToString(",") ?: ""
    }
}

@Entity
data class FaceImageRecord(
    // primary-key of `FaceImageRecord`
    @Id var recordID: Long = 0,

    // personId is derived from `PersonRecord`
    @Index var personID: Long = 0,
    var personName: String = "",

    // the FaceNet-512 model provides a 512-dimensional embedding
    @Convert(converter = FloatListConverter::class, dbType = String::class)
    var faceEmbedding: List<Float> = emptyList()
) {
    // Helper methods to convert between List<Float> and FloatArray
    fun setEmbeddingFromArray(array: FloatArray) {
        faceEmbedding = array.toList()
    }
    
    fun getEmbeddingAsArray(): FloatArray {
        return faceEmbedding.toFloatArray()
    }
    
    // Required for ObjectBox equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as FaceImageRecord
        
        if (recordID != other.recordID) return false
        if (personID != other.personID) return false
        if (personName != other.personName) return false
        if (faceEmbedding != other.faceEmbedding) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = recordID.hashCode()
        result = 31 * result + personID.hashCode()
        result = 31 * result + personName.hashCode()
        result = 31 * result + faceEmbedding.hashCode()
        return result
    }
}

@Entity
data class PersonRecord(
    // primary-key
    @Id var personID: Long = 0,
    var personName: String = "",

    // number of images selected by the user
    // under the name of the person
    var numImages: Long = 0,

    // time when the record was added
    var addTime: Long = 0
)

data class RecognitionMetrics(
    val timeFaceDetection: Long,
    val timeVectorSearch: Long,
    val timeFaceEmbedding: Long,
    val timeFaceSpoofDetection: Long
)
package com.example.vocaleyesnew.facenet_android.data

class ImagesVectorDB {

    private val imagesBox = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)

    fun addFaceImageRecord(record: FaceImageRecord) {
        imagesBox.put(record)
    }

    fun getNearestEmbeddingPersonName(embedding: FloatArray): FaceImageRecord? {
        // Convert FloatArray to List<Float> for search
        val embeddingList = embedding.toList()
        
        // Find all records for manual search (since we can't use nearestNeighbors anymore)
        val allRecords = imagesBox.getAll()
        
        // If no records, return null
        if (allRecords.isEmpty()) return null
        
        // Calculate cosine similarity manually
        return allRecords.maxByOrNull { record -> 
            calculateCosineSimilarity(embeddingList, record.faceEmbedding)
        }
    }
    
    // Calculate cosine similarity between two float lists
    private fun calculateCosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        if (vec1.isEmpty() || vec2.isEmpty() || vec1.size != vec2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        // Avoid division by zero
        if (norm1 <= 0 || norm2 <= 0) return 0f
        
        return dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble())).toFloat()
    }

    fun removeFaceRecordsWithPersonID(personID: Long) {
        imagesBox.removeByIds(
            imagesBox.query(FaceImageRecord_.personID.equal(personID)).build().findIds().toList()
        )
    }
}

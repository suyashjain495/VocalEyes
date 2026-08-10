package com.example.vocaleyesnew.textextraction

/**
 * Sealed class representing different states of the book reading/text extraction process
 */
sealed class BookReadingState {
    /**
     * Camera is scanning for text, ready to capture
     */
    object Scanning : BookReadingState()
    
    /**
     * Currently capturing an image
     */
    object Capturing : BookReadingState()
    
    /**
     * Processing captured image to extract text
     */
    object Processing : BookReadingState()
    
    /**
     * Text has been extracted and is ready for reading
     * @param text The extracted text
     * @param statistics Statistics about the extraction process
     */
    data class Reading(
        val text: String,
        val statistics: TextStatistics
    ) : BookReadingState()
    
    /**
     * An error occurred during processing
     * @param message Error message to display
     * @param throwable Optional throwable for debugging
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : BookReadingState()
}

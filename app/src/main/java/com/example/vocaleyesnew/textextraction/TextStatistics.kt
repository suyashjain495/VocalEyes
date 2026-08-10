package com.example.vocaleyesnew.textextraction

/**
 * Data class containing statistics about text extraction process
 */
data class TextStatistics(
    /**
     * Confidence level of text recognition (0-100)
     */
    val confidence: Int = 0,
    
    /**
     * Number of words extracted
     */
    val wordCount: Int = 0,
    
    /**
     * Number of characters extracted
     */
    val characterCount: Int = 0,
    
    /**
     * Number of lines of text
     */
    val lineCount: Int = 0,
    
    /**
     * Time taken to process the image in milliseconds
     */
    val processingTimeMs: Long = 0
) {
    /**
     * Convert statistics to a human-readable string for TTS
     */
    fun toSpeakableString(): String {
        return buildString {
            append("Text extracted successfully. ")
            append("$wordCount words, ")
            append("$characterCount characters, ")
            append("$lineCount lines. ")
            append("Confidence level: $confidence percent. ")
            append("Processing time: ${processingTimeMs} milliseconds.")
        }
    }
    
    /**
     * Convert statistics to a short summary string
     */
    fun toSummaryString(): String {
        return "$wordCount words • $characterCount chars • $confidence% confidence"
    }
}

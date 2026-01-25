package com.example.thingsappandroid.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtility {
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val outputDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    
    private val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
    
    /**
     * Converts milliseconds timestamp to ISO8601 UTC string
     */
    fun convertUTC(timestamp: Long): String {
        return iso8601Format.format(Date(timestamp))
    }
    
    /**
     * Formats API commencement date (e.g. "2025-12-27" or ISO) to "December 27, 2025".
     * Returns null if input is null or unparseable.
     */
    fun formatCommencementDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return inputFormats.asSequence()
            .mapNotNull { format ->
                try {
                    format.parse(raw.trim())?.let { outputDateFormat.format(it) }
                } catch (_: Exception) { null }
            }
            .firstOrNull()
    }
}

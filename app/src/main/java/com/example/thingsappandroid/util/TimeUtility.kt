package com.example.thingsappandroid.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtility {
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * Converts milliseconds timestamp to ISO8601 UTC string
     */
    fun convertUTC(timestamp: Long): String {
        return iso8601Format.format(Date(timestamp))
    }
}

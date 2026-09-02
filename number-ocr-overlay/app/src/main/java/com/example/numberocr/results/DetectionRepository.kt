package com.example.numberocr.results

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single newly appeared numeric code. */
data class Detection(val code: String, val timestampMillis: Long = System.currentTimeMillis()) {
    fun displayTime(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}

object DetectionRepository {
    private val lock = Any()
    private val items = mutableListOf<Detection>()

    fun addIfNew(code: String): Boolean = synchronized(lock) {
        val normalized = code.filter(Char::isDigit)
        if (normalized.isBlank()) return false
        val last = items.lastOrNull()?.code
        if (last == normalized) return false
        items += Detection(normalized)
        true
    }

    fun snapshot(): List<Detection> = synchronized(lock) { items.toList() }
    fun clear() = synchronized(lock) { items.clear() }
    fun asText(): String = snapshot().joinToString("\n") { "${it.displayTime()}  ${it.code}" }
}

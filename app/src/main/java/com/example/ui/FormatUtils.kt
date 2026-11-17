package com.example.ui

import java.util.Locale

object FormatUtils {
    fun formatSpeed(bytesPerSec: Long): String {
        val bitsPerSec = bytesPerSec * 8.0
        return when {
            bitsPerSec >= 1_000_000.0 -> String.format(Locale.US, "%.1f Мбит/с", bitsPerSec / 1_000_000.0)
            bitsPerSec >= 1_000.0 -> String.format(Locale.US, "%.0f Кбит/с", bitsPerSec / 1_000.0)
            else -> "$bytesPerSec Б/с"
        }
    }

    fun formatDataBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f ГБ", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.1f МБ", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.0f КБ", bytes / 1024.0)
            else -> "$bytes Б"
        }
    }
}

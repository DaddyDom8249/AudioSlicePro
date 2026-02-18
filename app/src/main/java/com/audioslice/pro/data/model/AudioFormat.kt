package com.audioslice.pro.data.model

import androidx.compose.ui.graphics.Color

enum class AudioFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String,
    val isLossless: Boolean,
    val defaultBitrate: Int,
    val supportedBitrates: List<Int>,
    val color: Color
) {
    MP3(
        extension = "mp3",
        mimeType = "audio/mpeg",
        displayName = "MP3",
        description = "Universal compatibility",
        isLossless = false,
        defaultBitrate = 256,
        supportedBitrates = listOf(128, 192, 256, 320),
        color = Color(0xFF6366F1)
    ),
    WAV(
        extension = "wav",
        mimeType = "audio/wav",
        displayName = "WAV",
        description = "Uncompressed quality",
        isLossless = true,
        defaultBitrate = 1411,
        supportedBitrates = listOf(1411),
        color = Color(0xFF10B981)
    ),
    FLAC(
        extension = "flac",
        mimeType = "audio/flac",
        displayName = "FLAC",
        description = "Compressed lossless",
        isLossless = true,
        defaultBitrate = 960,
        supportedBitrates = listOf(576, 960, 1440),
        color = Color(0xFFF59E0B)
    ),
    M4A(
        extension = "m4a",
        mimeType = "audio/mp4",
        displayName = "M4A",
        description = "Apple optimized",
        isLossless = false,
        defaultBitrate = 256,
        supportedBitrates = listOf(128, 192, 256, 320),
        color = Color(0xFFEC4899)
    ),
    OGG(
        extension = "ogg",
        mimeType = "audio/ogg",
        displayName = "OGG",
        description = "Open source format",
        isLossless = false,
        defaultBitrate = 192,
        supportedBitrates = listOf(128, 192, 256, 320),
        color = Color(0xFF8B5CF6)
    );

    companion object {
        fun fromExtension(ext: String): AudioFormat {
            return values().find { it.extension.equals(ext, ignoreCase = true) } ?: MP3
        }
    }
}

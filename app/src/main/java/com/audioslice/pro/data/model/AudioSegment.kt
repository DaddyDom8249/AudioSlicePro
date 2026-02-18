package com.audioslice.pro.data.model

import android.net.Uri

data class AudioSegment(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val fileSize: Long,
    val outputUri: Uri? = null,
    val status: SegmentStatus = SegmentStatus.PENDING
) {
    val durationText: String
        get() = formatDuration(durationMs)
    
    val fileSizeText: String
        get() = formatFileSize(fileSize)
    
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%d:%02d", minutes, seconds % 60)
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb > 0) "${mb} MB" else "${bytes / 1024} KB"
    }
}

enum class SegmentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    ERROR
}

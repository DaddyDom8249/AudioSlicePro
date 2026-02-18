package com.audioslice.pro.data.model

import android.net.Uri
import java.util.UUID

data class ProcessingJob(
    val id: String = UUID.randomUUID().toString(),
    val inputUri: Uri,
    val inputFileName: String,
    val inputFileSize: Long,
    val inputDuration: Long,
    val splitMethod: SplitMethod,
    val splitValue: Float,
    val outputFormat: AudioFormat,
    val bitrate: Int,
    val sampleRate: Int,
    val segments: List<AudioSegment> = emptyList(),
    val status: JobStatus = JobStatus.PENDING,
    val progress: Float = 0f,
    val currentSegment: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class JobStatus {
    PENDING,
    ANALYZING,
    SPLITTING,
    CONVERTING,
    COMPLETED,
    CANCELLED,
    ERROR
}

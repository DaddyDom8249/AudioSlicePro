package com.audioslice.pro.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.audioslice.pro.data.model.AudioFormat
import com.audioslice.pro.data.model.AudioSegment
import com.audioslice.pro.data.model.ProcessingJob
import com.audioslice.pro.data.model.SplitMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val jobs = mutableMapOf<String, ProcessingJob>()
    
    suspend fun getAudioInfo(uri: Uri): Result<AudioInfo> = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown"
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*"
            
            retriever.release()
            
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            
            Result.success(AudioInfo(
                uri = uri,
                title = title,
                artist = artist,
                duration = duration,
                fileSize = fileSize,
                mimeType = mimeType
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun calculateSegments(
        durationMs: Long,
        fileSize: Long,
        splitMethod: SplitMethod,
        splitValue: Float
    ): List<AudioSegment> {
        val segments = mutableListOf<AudioSegment>()
        
        when (splitMethod) {
            is SplitMethod.BySize -> {
                val segmentSizeBytes = (splitValue * 1024 * 1024).toLong()
                val numSegments = (fileSize / segmentSizeBytes).toInt() + if (fileSize % segmentSizeBytes > 0) 1 else 0
                val segmentDuration = durationMs / numSegments
                
                for (i in 0 until numSegments) {
                    val startMs = i * segmentDuration
                    val endMs = if (i == numSegments - 1) durationMs else (i + 1) * segmentDuration
                    val segDuration = endMs - startMs
                    val segSize = (fileSize * segDuration) / durationMs
                    
                    segments.add(AudioSegment(
                        id = i + 1,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        durationMs = segDuration,
                        fileSize = segSize
                    ))
                }
            }
            is SplitMethod.ByDuration -> {
                val segmentDurationMs = (splitValue * 60 * 1000).toLong()
                val numSegments = (durationMs / segmentDurationMs).toInt() + if (durationMs % segmentDurationMs > 0) 1 else 0
                
                for (i in 0 until numSegments) {
                    val startMs = i * segmentDurationMs
                    val endMs = if (i == numSegments - 1) durationMs else (i + 1) * segmentDurationMs
                    val segDuration = endMs - startMs
                    val segSize = (fileSize * segDuration) / durationMs
                    
                    segments.add(AudioSegment(
                        id = i + 1,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        durationMs = segDuration,
                        fileSize = segSize
                    ))
                }
            }
        }
        
        return segments
    }
    
    suspend fun createJob(job: ProcessingJob) {
        jobs[job.id] = job
    }
    
    suspend fun getJob(jobId: String): ProcessingJob? {
        return jobs[jobId]
    }
    
    suspend fun updateJob(job: ProcessingJob) {
        jobs[job.id] = job
    }
    
    data class AudioInfo(
        val uri: Uri,
        val title: String,
        val artist: String,
        val duration: Long,
        val fileSize: Long,
        val mimeType: String
    )
}

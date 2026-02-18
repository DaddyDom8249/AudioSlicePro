package com.audioslice.pro.audioeditor

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEditor @Inject constructor(private val context: Context) {
    data class AudioInfo(val durationMs: Long, val sampleRate: Int, val channels: Int, val bitrate: Int, val format: String, val fileSize: Long)
    data class EditConfig(val startTimeMs: Long = 0, val endTimeMs: Long = 0, val fadeInDurationMs: Long = 0, val fadeOutDurationMs: Long = 0, val speed: Float = 1.0f, val pitch: Float = 1.0f, val volume: Float = 1.0f, val normalize: Boolean = false, val reverse: Boolean = false)
    data class EffectsChain(val eqBands: List<EQBand>? = null, val compressor: CompressorSettings? = null, val reverb: ReverbSettings? = null, val normalize: Boolean = false)
    data class EQBand(val frequency: Int, val width: Double, val gain: Double)
    data class CompressorSettings(val threshold: Double, val ratio: Double, val attack: Double, val release: Double)
    data class ReverbSettings(val delay: Int, val decay: Double)

    sealed class EditProgress {
        object Started : EditProgress()
        data class Progress(val percent: Int) : EditProgress()
        data class Completed(val file: File) : EditProgress()
        data class Error(val message: String) : EditProgress()
    }

    suspend fun getAudioInfo(uri: Uri): AudioInfo = withContext(Dispatchers.IO) {
        val path = getRealPathFromUri(uri) ?: throw IllegalArgumentException("Invalid URI")
        val session = FFprobeKit.execute("-v quiet -print_format json -show_streams -show_format \"$path\"")
        val output = session.output
        AudioInfo(extractDuration(output), extractSampleRate(output), extractChannels(output), extractBitrate(output), extractFormat(output), File(path).length())
    }

    suspend fun cutAudio(inputUri: Uri, outputFile: File, startMs: Long, endMs: Long, config: EditConfig = EditConfig()): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        val inputPath = getRealPathFromUri(inputUri) ?: throw IllegalArgumentException("Invalid URI")
        val filterComplex = buildFilterComplex(config, startMs, endMs)
        val command = buildString {
            append("-y -i \"$inputPath\" ")
            if (filterComplex.isNotEmpty()) append("-filter_complex \"$filterComplex\" ")
            if (config.speed == 1.0f && config.pitch == 1.0f && !config.reverse) {
                append("-ss ${startMs / 1000.0} -t ${(endMs - startMs) / 1000.0} -c copy ")
            } else {
                append("-ss ${startMs / 1000.0} -t ${(endMs - startMs) / 1000.0} ")
            }
            append("-ar 48000 -ac 2 -b:a 256k \"${outputFile.absolutePath}\"")
        }
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Unknown error"))
    }.flowOn(Dispatchers.IO)

    suspend fun mergeAudio(inputUris: List<Uri>, outputFile: File, crossfadeMs: Long = 0): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        if (inputUris.size < 2) { emit(EditProgress.Error("Need at least 2 files")); return@flow }
        val inputs = inputUris.map { getRealPathFromUri(it) ?: "" }
        val filterComplex = if (crossfadeMs > 0) buildCrossfadeFilter(inputs, crossfadeMs) else buildConcatFilter(inputs)
        val inputCmd = inputs.joinToString(" ") { "-i \"$it\"" }
        val command = "$inputCmd -filter_complex \"$filterComplex\" -y \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Merge failed"))
    }.flowOn(Dispatchers.IO)

    suspend fun applyEffects(inputUri: Uri, outputFile: File, effectsChain: EffectsChain): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        val inputPath = getRealPathFromUri(inputUri) ?: throw IllegalArgumentException("Invalid URI")
        val filterChain = buildEffectsFilter(effectsChain)
        val command = "-y -i \"$inputPath\" -filter_complex \"$filterChain\" -ar 48000 -ac 2 \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Effects failed"))
    }.flowOn(Dispatchers.IO)

    suspend fun timeStretch(inputUri: Uri, outputFile: File, ratio: Float): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        val inputPath = getRealPathFromUri(inputUri) ?: throw IllegalArgumentException("Invalid URI")
        val command = "-y -i \"$inputPath\" -af \"atempo=$ratio\" -ar 48000 -ac 2 \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Time stretch failed"))
    }.flowOn(Dispatchers.IO)

    suspend fun pitchShift(inputUri: Uri, outputFile: File, semitones: Float): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        val inputPath = getRealPathFromUri(inputUri) ?: throw IllegalArgumentException("Invalid URI")
        val command = "-y -i \"$inputPath\" -af \"rubberband=pitch=$semitones\" -ar 48000 -ac 2 \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Pitch shift failed"))
    }.flowOn(Dispatchers.IO)

    suspend fun removeSilence(inputUri: Uri, outputFile: File, noiseDb: Int = -50): Flow<EditProgress> = flow {
        emit(EditProgress.Started)
        val inputPath = getRealPathFromUri(inputUri) ?: throw IllegalArgumentException("Invalid URI")
        val command = "-y -i \"$inputPath\" -af \"silenceremove=start_periods=1:start_duration=0.1:start_threshold=${noiseDb}dB,stop_periods=-1:stop_duration=0.1:stop_threshold=${noiseDb}dB\" -ar 48000 -ac 2 \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) emit(EditProgress.Completed(outputFile))
        else emit(EditProgress.Error(session.failStackTrace ?: "Silence removal failed"))
    }.flowOn(Dispatchers.IO)

    private fun buildFilterComplex(config: EditConfig, startMs: Long, endMs: Long): String {
        val filters = mutableListOf<String>()
        if (config.volume != 1.0f) filters.add("volume=${config.volume}")
        if (config.fadeInDurationMs > 0) filters.add("afade=t=in:ss=0:d=${config.fadeInDurationMs / 1000.0}")
        if (config.fadeOutDurationMs > 0) {
            val fadeOutStart = (endMs - startMs - config.fadeOutDurationMs) / 1000.0
            filters.add("afade=t=out:st=$fadeOutStart:d=${config.fadeOutDurationMs / 1000.0}")
        }
        if (config.speed != 1.0f || config.pitch != 1.0f) {
            val atempo = config.speed
            val asetrate = 44100 * config.pitch
            filters.add("asetrate=$asetrate,atempo=$atempo")
        }
        if (config.normalize) filters.add("loudnorm=I=-16:TP=-1.5:LRA=11")
        if (config.reverse) filters.add("areverse")
        return filters.joinToString(",")
    }

    private fun buildConcatFilter(inputs: List<String>): String {
        return inputs.indices.joinToString("", "concat=n=${inputs.size}:v=0:a=1 [out]") { "[$it:a:0]" }
    }

    private fun buildCrossfadeFilter(inputs: List<String>, crossfadeMs: Long): String {
        val crossfadeSec = crossfadeMs / 1000.0
        var filter = ""
        for (i in inputs.indices) filter += "[$i:a:0]"
        filter += "acrossfade=d=$crossfadeSec [out]"
        return filter
    }

    private fun buildEffectsFilter(chain: EffectsChain): String {
        val filters = mutableListOf<String>()
        chain.eqBands?.let { bands ->
            val eqFilter = bands.joinToString(":") { "f=${it.frequency}:w=${it.width}:g=${it.gain}" }
            filters.add("equalizer=$eqFilter")
        }
        chain.compressor?.let { comp ->
            filters.add("acompressor=threshold=${comp.threshold}:ratio=${comp.ratio}:attack=${comp.attack}:release=${comp.release}")
        }
        chain.reverb?.let { rev -> filters.add("aecho=0.8:0.9:${rev.delay}:${rev.decay}") }
        if (chain.normalize) filters.add("loudnorm=I=-16:TP=-1.5:LRA=11")
        return filters.joinToString(",")
    }

    private fun getRealPathFromUri(uri: Uri): String? = uri.path
    private fun extractDuration(output: String): Long = """duration": "([0-9.]+)"""".toRegex().find(output)?.groupValues?.get(1)?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0
    private fun extractSampleRate(output: String): Int = """sample_rate": "([0-9]+)"""".toRegex().find(output)?.groupValues?.get(1)?.toInt() ?: 44100
    private fun extractChannels(output: String): Int = """channels": ([0-9]+)""".toRegex().find(output)?.groupValues?.get(1)?.toInt() ?: 2
    private fun extractBitrate(output: String): Int = """bit_rate": "([0-9]+)"""".toRegex().find(output)?.groupValues?.get(1)?.toInt() ?: 128000
    private fun extractFormat(output: String): String = """format_name": "([^"]+)"""".toRegex().find(output)?.groupValues?.get(1) ?: "unknown"
}

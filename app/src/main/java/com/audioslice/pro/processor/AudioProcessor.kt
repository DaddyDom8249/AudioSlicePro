package com.audioslice.pro.processor

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class AudioProcessor @Inject constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
        const val NS_LEVEL = 2
        const val LOW_CUT_FREQ = 85.0
        const val HIGH_CUT_FREQ = 8000.0
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
    }
    private val spectralProcessor = SpectralVoiceProcessor(SAMPLE_RATE)

    sealed class AudioState {
        object Idle : AudioState()
        data class Recording(val amplitude: Float, val durationMs: Long) : AudioState()
        data class Processing(val progress: Float) : AudioState()
        data class Complete(val filePath: String, val durationMs: Long) : AudioState()
        data class Error(val message: String) : AudioState()
    }

    data class ProcessingConfig(
        val enableNoiseSuppression: Boolean = true,
        val enableVoiceIsolation: Boolean = true,
        val enableAutoGain: Boolean = true,
        val noiseSuppressionLevel: Int = NS_LEVEL,
        val targetLoudness: Float = -16.0f
    )

    fun startRecording(outputFile: File, config: ProcessingConfig = ProcessingConfig()) {
        if (_audioState.value is AudioState.Recording) return
        try {
            initializeAudioRecord()
            recordingJob = CoroutineScope(dispatcher + SupervisorJob()).launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                audioRecord?.startRecording()
                _audioState.value = AudioState.Recording(0f, 0L)
                val startTime = System.currentTimeMillis()
                val outputStream = java.io.FileOutputStream(outputFile)
                val bufferedStream = java.io.BufferedOutputStream(outputStream)
                writeWavHeader(bufferedStream, 0)
                val audioBuffer = ShortArray(bufferSize)
                var totalSamples = 0
                var maxAmplitude = 0
                try {
                    while (isActive && _audioState.value is AudioState.Recording) {
                        val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        if (readCount > 0) {
                            val processedBuffer = processAudioChunk(audioBuffer.copyOfRange(0, readCount), config)
                            maxAmplitude = processedBuffer.maxOf { abs(it.toInt()) }
                            val amplitudeDb = 20 * log10(maxAmplitude / 32768.0 + 1e-10).toFloat()
                            processedBuffer.forEach { sample ->
                                bufferedStream.write(sample.toInt() and 0xFF)
                                bufferedStream.write((sample.toInt() shr 8) and 0xFF)
                            }
                            totalSamples += readCount
                            val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
                            _audioState.value = AudioState.Recording((amplitudeDb + 96) / 96, durationMs)
                        }
                    }
                } finally {
                    val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
                    val finalSize = totalSamples * 2 + 36
                    bufferedStream.flush()
                    updateWavHeader(outputFile, finalSize, totalSamples)
                    bufferedStream.close()
                    if (config.enableVoiceIsolation || config.enableNoiseSuppression) {
                        postProcessFile(outputFile, config)
                    } else {
                        _audioState.value = AudioState.Complete(outputFile.absolutePath, durationMs)
                    }
                }
            }
        } catch (e: Exception) {
            _audioState.value = AudioState.Error("Recording failed: ${e.message}")
            cleanup()
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.stop()
        _audioState.value = AudioState.Processing(0f)
    }

    private fun processAudioChunk(input: ShortArray, config: ProcessingConfig): ShortArray {
        var processed = input
        if (config.enableNoiseSuppression) processed = applyNoiseSuppression(processed, config.noiseSuppressionLevel)
        if (config.enableVoiceIsolation) processed = spectralProcessor.isolateVoice(processed)
        if (config.enableAutoGain) processed = applyAutoGain(processed, config.targetLoudness)
        return processed
    }

    private fun applyNoiseSuppression(input: ShortArray, level: Int): ShortArray {
        return spectralProcessor.aggressiveNoiseGate(input, level)
    }

    private fun applyAutoGain(input: ShortArray, targetLoudness: Float): ShortArray {
        val rms = sqrt(input.map { (it * it).toDouble() }.average()).toFloat()
        val currentDb = 20 * log10(rms / 32768.0 + 1e-10).toFloat()
        val gainDb = targetLoudness - currentDb
        val gain = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0.1f, 10.0f)
        return input.map { (it * gain).toInt().toShort() }.toShortArray()
    }

    private suspend fun postProcessFile(file: File, config: ProcessingConfig) = withContext(dispatcher) {
        _audioState.value = AudioState.Processing(0.3f)
        try {
            val tempFile = File(file.parent, "${file.name}.tmp")
            val samples = readWavFile(file)
            _audioState.value = AudioState.Processing(0.5f)
            val enhanced = if (config.enableVoiceIsolation) spectralProcessor.spectralGating(samples) else samples
            _audioState.value = AudioState.Processing(0.8f)
            val normalized = normalizeAudio(enhanced)
            writeWavFile(tempFile, normalized)
            file.delete()
            tempFile.renameTo(file)
            val durationMs = (normalized.size * 1000L) / SAMPLE_RATE
            _audioState.value = AudioState.Complete(file.absolutePath, durationMs)
        } catch (e: Exception) {
            _audioState.value = AudioState.Error("Processing failed: ${e.message}")
        }
    }

    private fun initializeAudioRecord() {
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) return
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize).apply {
            if (state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord initialization failed")
        }
    }

    private fun writeWavHeader(out: java.io.OutputStream, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (16 * SAMPLE_RATE * 1) / 8
        val header = ByteArray(44)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(totalAudioLen.toInt())
        }
        out.write(header)
    }

    private fun updateWavHeader(file: File, totalSize: Int, totalSamples: Int) {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.writeInt(totalSize - 8)
            raf.seek(40)
            raf.writeInt(totalSamples * 2)
        }
    }

    private fun readWavFile(file: File): ShortArray {
        val bytes = file.readBytes()
        val dataStart = 44
        val sampleCount = (bytes.size - dataStart) / 2
        val samples = ShortArray(sampleCount)
        ByteBuffer.wrap(bytes, dataStart, bytes.size - dataStart).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return samples
    }

    private fun writeWavFile(file: File, samples: ShortArray) {
        java.io.FileOutputStream(file).use { fos ->
            writeWavHeader(fos, (samples.size * 2).toLong())
            val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { buffer.putShort(it) }
            fos.write(buffer.array())
        }
    }

    private fun normalizeAudio(input: ShortArray): ShortArray {
        val maxVal = input.maxOf { abs(it.toInt()) }.toFloat().coerceAtLeast(1f)
        val scale = 32767f / maxVal
        return input.map { (it * scale).toInt().toShort() }.toShortArray()
    }

    fun cleanup() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.release()
        audioRecord = null
        _audioState.value = AudioState.Idle
    }
}

class SpectralVoiceProcessor(private val sampleRate: Int) {
    private val fftSize = 512
    private val hopSize = 256
    private val window = createHannWindow(fftSize)
    private var noiseProfile: FloatArray? = null
    private val noiseUpdateRate = 0.05f

    fun isolateVoice(input: ShortArray): ShortArray = spectralGating(input)
    
    fun noiseReduction(input: ShortArray): ShortArray = spectralGating(input)

    fun aggressiveNoiseGate(input: ShortArray, level: Int): ShortArray {
        val threshold = when(level) { 0 -> 0.1f; 1 -> 0.05f; 2 -> 0.02f; 3 -> 0.01f; else -> 0.05f }
        return input.map { sample -> if (abs(sample) / 32768f < threshold) 0 else sample }.toShortArray()
    }

    fun spectralGating(input: ShortArray): ShortArray {
        if (input.size < fftSize) return input
        val output = FloatArray(input.size)
        val floatInput = input.map { it / 32768f }.toFloatArray()
        var i = 0
        while (i + fftSize <= floatInput.size) {
            val frame = floatInput.copyOfRange(i, i + fftSize)
            val windowed = frame.mapIndexed { idx, v -> v * window[idx] }.toFloatArray()
            val magnitude = windowed.map { abs(it) }.toFloatArray()
            val mask = createVoiceMask(magnitude)
            val processed = magnitude.mapIndexed { idx, mag -> mag * mask[idx] }.toFloatArray()
            for (j in 0 until fftSize) {
                if (i + j < output.size) output[i + j] += processed[j] * window[j]
            }
            i += hopSize
        }
        updateNoiseProfile(magnitude)
        return output.map { (it * 32767f).toInt().toShort() }.toShortArray()
    }

    private fun createVoiceMask(magnitude: FloatArray): FloatArray {
        return magnitude.indices.map { i ->
            val freq = i * sampleRate.toFloat() / fftSize
            val isVoiceFreq = freq in 85f..8000f
            if (isVoiceFreq) 1.0f else 0.01f
        }.toFloatArray()
    }

    private fun updateNoiseProfile(magnitude: FloatArray) {
        if (noiseProfile == null) noiseProfile = magnitude.copyOf()
        else {
            for (i in magnitude.indices) {
                noiseProfile!![i] = noiseProfile!![i] * (1 - noiseUpdateRate) + magnitude[i] * noiseUpdateRate
            }
        }
    }

    private fun createHannWindow(size: Int): FloatArray {
        return FloatArray(size) { i -> 0.5f * (1 - cos(2 * PI * i / (size - 1))).toFloat() }
    }
}

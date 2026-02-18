package com.audioslice.pro.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioslice.pro.audioeditor.AudioEditor
import com.audioslice.pro.processor.AudioProcessor
import com.audioslice.pro.waveform.WaveformData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    application: Application,
    private val audioEditor: AudioEditor,
    private val audioProcessor: AudioProcessor
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<EditorEvent>()
    val events = _events.asSharedFlow()
    private var currentFile: File? = null
    private var originalUri: Uri? = null

    fun loadAudio(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                originalUri = uri
                val info = audioEditor.getAudioInfo(uri)
                val samples = extractSamplesSimple(uri)
                val waveform = WaveformData(samples, info.durationMs, 100)
                _uiState.update { it.copy(isLoading = false, audioInfo = info, waveformData = waveform, selectionStart = 0, selectionEnd = info.durationMs) }
                _events.emit(EditorEvent.AudioLoaded(info.durationMs))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _events.emit(EditorEvent.ShowError("Failed to load: ${e.message}"))
            }
        }
    }

    fun updateSelection(startMs: Long, endMs: Long) {
        _uiState.update { it.copy(selectionStart = startMs, selectionEnd = endMs) }
    }

    fun cutSelection() {
        val state = _uiState.value
        val uri = originalUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val outputFile = createTempFile("cut_", ".wav")
            audioEditor.cutAudio(uri, outputFile, state.selectionStart, state.selectionEnd).collect { progress ->
                when (progress) {
                    is AudioEditor.EditProgress.Completed -> {
                        currentFile = outputFile
                        reloadAfterEdit(outputFile)
                        _events.emit(EditorEvent.ShowMessage("Cut complete"))
                    }
                    is AudioEditor.EditProgress.Error -> {
                        _uiState.update { it.copy(isProcessing = false) }
                        _events.emit(EditorEvent.ShowError(progress.message))
                    }
                    else -> {}
                }
            }
        }
    }

    fun applyEffect(effectType: EffectType) {
        val uri = originalUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val outputFile = createTempFile("effect_", ".wav")
            val effectsChain = when (effectType) {
                EffectType.NORMALIZE -> AudioEditor.EffectsChain(normalize = true)
                EffectType.VOICE_ENHANCE -> AudioEditor.EffectsChain(eqBands = listOf(AudioEditor.EQBand(3000, 1.0, 3.0), AudioEditor.EQBand(5000, 1.0, 2.0)), compressor = AudioEditor.CompressorSettings(-20.0, 3.0, 5.0, 50.0))
                EffectType.BASS_BOOST -> AudioEditor.EffectsChain(eqBands = listOf(AudioEditor.EQBand(100, 2.0, 6.0)))
                EffectType.REVERB -> AudioEditor.EffectsChain(reverb = AudioEditor.ReverbSettings(200, 0.5))
                EffectType.NOISE_REDUCTION -> { audioProcessor.startRecording(outputFile); audioProcessor.stopRecording(); return@launch }
            }
            audioEditor.applyEffects(uri, outputFile, effectsChain).collect { progress ->
                when (progress) {
                    is AudioEditor.EditProgress.Completed -> { currentFile = outputFile; reloadAfterEdit(outputFile); _events.emit(EditorEvent.ShowMessage("${effectType.name} applied")) }
                    is AudioEditor.EditProgress.Error -> { _uiState.update { it.copy(isProcessing = false) }; _events.emit(EditorEvent.ShowError(progress.message)) }
                    else -> {}
                }
            }
        }
    }

    fun changeSpeed(speed: Float) {
        val uri = originalUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val outputFile = createTempFile("speed_", ".wav")
            audioEditor.timeStretch(uri, outputFile, speed).collect { progress ->
                when (progress) {
                    is AudioEditor.EditProgress.Completed -> { currentFile = outputFile; reloadAfterEdit(outputFile) }
                    is AudioEditor.EditProgress.Error -> { _uiState.update { it.copy(isProcessing = false) }; _events.emit(EditorEvent.ShowError(progress.message)) }
                    else -> {}
                }
            }
        }
    }

    fun changePitch(semitones: Float) {
        val uri = originalUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val outputFile = createTempFile("pitch_", ".wav")
            audioEditor.pitchShift(uri, outputFile, semitones).collect { progress ->
                when (progress) {
                    is AudioEditor.EditProgress.Completed -> { currentFile = outputFile; reloadAfterEdit(outputFile) }
                    is AudioEditor.EditProgress.Error -> { _uiState.update { it.copy(isProcessing = false) }; _events.emit(EditorEvent.ShowError(progress.message)) }
                    else -> {}
                }
            }
        }
    }

    fun removeSilence() {
        val uri = originalUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val outputFile = createTempFile("silence_", ".wav")
            audioEditor.removeSilence(uri, outputFile).collect { progress ->
                when (progress) {
                    is AudioEditor.EditProgress.Completed -> { currentFile = outputFile; reloadAfterEdit(outputFile); _events.emit(EditorEvent.ShowMessage("Silence removed")) }
                    is AudioEditor.EditProgress.Error -> { _uiState.update { it.copy(isProcessing = false) }; _events.emit(EditorEvent.ShowError(progress.message)) }
                    else -> {}
                }
            }
        }
    }

    fun undo() { _events.tryEmit(EditorEvent.ShowMessage("Undo not implemented")) }

    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            try {
                currentFile?.let { file ->
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    }
                    _events.emit(EditorEvent.ShowMessage("Saved successfully"))
                }
            } catch (e: Exception) { _events.emit(EditorEvent.ShowError("Save failed: ${e.message}")) }
        }
    }

    private suspend fun reloadAfterEdit(file: File) {
        val uri = Uri.fromFile(file)
        val info = audioEditor.getAudioInfo(uri)
        val samples = extractSamplesSimple(uri)
        val waveform = WaveformData(samples, info.durationMs, 100)
        _uiState.update { it.copy(isProcessing = false, progress = 1f, audioInfo = info, waveformData = waveform, hasUnsavedChanges = true) }
    }

    private fun createTempFile(prefix: String, suffix: String): File = File.createTempFile(prefix, suffix, getApplication<Application>().cacheDir)

    private fun extractSamplesSimple(uri: Uri): FloatArray {
        return FloatArray(1000) { kotlin.random.Random.nextFloat() }
    }

    data class EditorUiState(val isLoading: Boolean = false, val isProcessing: Boolean = false, val progress: Float = 0f, val audioInfo: AudioEditor.AudioInfo? = null, val waveformData: WaveformData? = null, val selectionStart: Long = 0, val selectionEnd: Long = 0, val hasUnsavedChanges: Boolean = false, val error: String? = null)
    sealed class EditorEvent { data class AudioLoaded(val durationMs: Long) : EditorEvent(); data class ShowMessage(val message: String) : EditorEvent(); data class ShowError(val error: String) : EditorEvent() }
    enum class EffectType { NORMALIZE, VOICE_ENHANCE, BASS_BOOST, REVERB, NOISE_REDUCTION }
}

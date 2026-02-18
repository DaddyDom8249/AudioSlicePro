package com.audioslice.pro.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioslice.pro.processor.AudioProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    application: Application,
    private val audioProcessor: AudioProcessor
) : AndroidViewModel(application) {

    val audioState = audioProcessor.audioState
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private var currentRecordingFile: File? = null

    fun startRecording(config: AudioProcessor.ProcessingConfig = AudioProcessor.ProcessingConfig()) {
        val file = File(getApplication<Application>().cacheDir, "recording_${System.currentTimeMillis()}.wav")
        currentRecordingFile = file
        audioProcessor.startRecording(file, config)
    }

    fun stopRecording() {
        audioProcessor.stopRecording()
    }

    fun saveRecording(uri: Uri) {
        viewModelScope.launch {
            try {
                currentRecordingFile?.let { source ->
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                    _events.emit(UiEvent.ShowMessage("Saved successfully"))
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowError("Save failed: ${e.message}"))
            }
        }
    }

    fun cleanup() {
        audioProcessor.cleanup()
        currentRecordingFile?.delete()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    sealed class UiEvent {
        data class ShowMessage(val message: String) : UiEvent()
        data class ShowError(val error: String) : UiEvent()
    }
}

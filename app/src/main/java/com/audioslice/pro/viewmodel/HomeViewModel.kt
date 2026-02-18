package com.audioslice.pro.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioslice.pro.data.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()
    
    private val _audioInfo = MutableStateFlow<AudioRepository.AudioInfo?>(null)
    val audioInfo: StateFlow<AudioRepository.AudioInfo?> = _audioInfo.asStateFlow()
    
    fun onFileSelected(uri: Uri) {
        _selectedUri.value = uri
        _uiState.value = HomeUiState.Loading
        
        viewModelScope.launch {
            audioRepository.getAudioInfo(uri)
                .onSuccess { info ->
                    _audioInfo.value = info
                    _uiState.value = HomeUiState.Success(info)
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to load audio")
                }
        }
    }
    
    fun clearSelection() {
        _selectedUri.value = null
        _audioInfo.value = null
        _uiState.value = HomeUiState.Initial
    }
    
    sealed class HomeUiState {
        object Initial : HomeUiState()
        object Loading : HomeUiState()
        data class Success(val info: AudioRepository.AudioInfo) : HomeUiState()
        data class Error(val message: String) : HomeUiState()
    }
}

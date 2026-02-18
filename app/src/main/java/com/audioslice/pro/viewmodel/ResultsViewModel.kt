package com.audioslice.pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioslice.pro.data.model.AudioSegment
import com.audioslice.pro.data.model.ProcessingJob
import com.audioslice.pro.data.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {
    
    private val _job = MutableStateFlow<ProcessingJob?>(null)
    val job: StateFlow<ProcessingJob?> = _job.asStateFlow()
    
    fun loadJob(jobId: String) {
        viewModelScope.launch {
            _job.value = audioRepository.getJob(jobId)
        }
    }
    
    fun downloadSegment(segment: AudioSegment) {
        // Implementation for downloading single segment
    }
    
    fun downloadAll() {
        // Implementation for creating ZIP and downloading
    }
    
    fun shareSegment(segment: AudioSegment) {
        // Implementation for sharing
    }
}

package com.audioslice.pro.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.audioslice.pro.databinding.ActivityMainBinding
import com.audioslice.pro.processor.AudioProcessor
import com.audioslice.pro.viewmodel.ProcessingViewModel
import com.guolindev.permissionx.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ProcessingViewModel by viewModels()
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> uri?.let { viewModel.saveRecording(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()
        setupUI()
        observeState()
    }

    private fun checkPermissions() {
        PermissionX.init(this).permissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .onExplainRequestReason { scope, deniedList -> scope.showRequestReasonDialog(deniedList, "Microphone access needed", "OK") }
            .request { allGranted, _, _ -> if (!allGranted) { Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show(); finish() } }
    }

    private fun setupUI() {
        binding.recordButton.setOnClickListener {
            when (viewModel.audioState.value) {
                is AudioProcessor.AudioState.Recording -> viewModel.stopRecording()
                else -> startRecording()
            }
        }
        binding.saveButton.setOnClickListener { saveLauncher.launch("recording_${System.currentTimeMillis()}.wav") }
        binding.openEditorButton.setOnClickListener { startActivity(Intent(this, EditorActivity::class.java)) }
    }

    private fun startRecording() {
        val config = AudioProcessor.ProcessingConfig(enableNoiseSuppression = true, enableVoiceIsolation = true, enableAutoGain = true)
        viewModel.startRecording(config)
    }

    private fun observeState() {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.audioState.collect { state -> updateUI(state) } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.events.collect { event -> when (event) { is ProcessingViewModel.UiEvent.ShowMessage -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show(); is ProcessingViewModel.UiEvent.ShowError -> Toast.makeText(this@MainActivity, event.error, Toast.LENGTH_LONG).show() } } } }
    }

    private fun updateUI(state: AudioProcessor.AudioState) {
        when (state) {
            is AudioProcessor.AudioState.Idle -> { binding.recordButton.text = "● Record"; binding.statusText.text = "Ready" }
            is AudioProcessor.AudioState.Recording -> { binding.recordButton.text = "■ Stop"; binding.statusText.text = "Recording: ${formatDuration(state.durationMs)}"; binding.waveformView.addAmplitude(state.amplitude) }
            is AudioProcessor.AudioState.Processing -> { binding.statusText.text = "Processing..." }
            is AudioProcessor.AudioState.Complete -> { binding.recordButton.text = "● Record"; binding.statusText.text = "Complete: ${formatDuration(state.durationMs)}"; binding.saveButton.isEnabled = true }
            is AudioProcessor.AudioState.Error -> { binding.recordButton.text = "● Record"; binding.statusText.text = "Error: ${state.message}" }
        }
    }

    private fun formatDuration(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}

package com.audioslice.pro.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.audioslice.pro.databinding.ActivityEditorBinding
import com.audioslice.pro.viewmodel.EditorViewModel
import com.audioslice.pro.waveform.WaveformEditorView
import com.guolindev.permissionx.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val viewModel: EditorViewModel by viewModels()
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.loadAudio(it) } }
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> uri?.let { viewModel.saveTo(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()
        setupUI()
        observeState()
    }

    private fun checkPermissions() {
        PermissionX.init(this).permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
            .onExplainRequestReason { scope, deniedList -> scope.showRequestReasonDialog(deniedList, "Storage access needed", "OK") }
            .request { allGranted, _, _ -> if (!allGranted) Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show() }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open -> { openDocument.launch(arrayOf("audio/*")); true }
                R.id.action_save -> { createDocument.launch("edited_audio.wav"); true }
                R.id.action_undo -> { viewModel.undo(); true }
                else -> false
            }
        }
        binding.waveformView.selectionListener = object : WaveformEditorView.OnSelectionChangeListener {
            override fun onSelectionChanged(startMs: Long, endMs: Long) { viewModel.updateSelection(startMs, endMs); updateTimeDisplay(startMs, endMs) }
            override fun onPlayheadMoved(positionMs: Long) {}
        }
        binding.zoomInButton.setOnClickListener { binding.waveformView.zoomIn() }
        binding.zoomOutButton.setOnClickListener { binding.waveformView.zoomOut() }
        binding.zoomResetButton.setOnClickListener { binding.waveformView.resetZoom() }
        binding.selectAllButton.setOnClickListener { binding.waveformView.selectAll() }
        binding.cutButton.setOnClickListener { viewModel.cutSelection() }
        binding.normalizeButton.setOnClickListener { viewModel.applyEffect(EditorViewModel.EffectType.NORMALIZE) }
        binding.voiceEnhanceButton.setOnClickListener { viewModel.applyEffect(EditorViewModel.EffectType.VOICE_ENHANCE) }
        binding.noiseReductionButton.setOnClickListener { viewModel.applyEffect(EditorViewModel.EffectType.NOISE_REDUCTION) }
        binding.reverbButton.setOnClickListener { viewModel.applyEffect(EditorViewModel.EffectType.REVERB) }
        binding.bassBoostButton.setOnClickListener { viewModel.applyEffect(EditorViewModel.EffectType.BASS_BOOST) }
        binding.removeSilenceButton.setOnClickListener { viewModel.removeSilence() }
        binding.speedSlider.addOnChangeListener { _, value, _ -> binding.speedValue.text = "${value}x" }
        binding.applySpeedButton.setOnClickListener { viewModel.changeSpeed(binding.speedSlider.value) }
        binding.pitchSlider.addOnChangeListener { _, value, _ -> binding.pitchValue.text = "${value.toInt()} st" }
        binding.applyPitchButton.setOnClickListener { viewModel.changePitch(binding.pitchSlider.value) }
        enableControls(false)
    }

    private fun observeState() {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect { state -> updateUI(state) } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.events.collect { event -> when (event) { is EditorViewModel.EditorEvent.AudioLoaded -> enableControls(true); is EditorViewModel.EditorEvent.ShowMessage -> Toast.makeText(this@EditorActivity, event.message, Toast.LENGTH_SHORT).show(); is EditorViewModel.EditorEvent.ShowError -> Toast.makeText(this@EditorActivity, event.error, Toast.LENGTH_LONG).show() } } } }
    }

    private fun updateUI(state: EditorViewModel.EditorUiState) {
        binding.progressBar.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
        binding.loadingOverlay.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        state.waveformData?.let { binding.waveformView.setWaveformData(it) }
        state.audioInfo?.let { info -> binding.infoText.text = "Duration: ${formatDuration(info.durationMs)} | ${info.sampleRate}Hz | ${info.channels}ch" }
        if (state.isProcessing) binding.progressBar.progress = (state.progress * 100).toInt()
    }

    private fun updateTimeDisplay(startMs: Long, endMs: Long) { binding.selectionText.text = "Selection: ${formatDuration(startMs)} - ${formatDuration(endMs)}" }

    private fun enableControls(enabled: Boolean) {
        binding.cutButton.isEnabled = enabled; binding.normalizeButton.isEnabled = enabled; binding.voiceEnhanceButton.isEnabled = enabled
        binding.noiseReductionButton.isEnabled = enabled; binding.reverbButton.isEnabled = enabled; binding.bassBoostButton.isEnabled = enabled
        binding.removeSilenceButton.isEnabled = enabled; binding.applySpeedButton.isEnabled = enabled; binding.applyPitchButton.isEnabled = enabled
        binding.speedSlider.isEnabled = enabled; binding.pitchSlider.isEnabled = enabled
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000; val minutes = seconds / 60; val hours = minutes / 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60) else String.format("%d:%02d", minutes, seconds % 60)
    }
}

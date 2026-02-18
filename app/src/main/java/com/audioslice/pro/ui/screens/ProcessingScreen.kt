package com.audioslice.pro.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audioslice.pro.data.model.AudioFormat
import com.audioslice.pro.data.model.SplitMethod
import com.audioslice.pro.viewmodel.ProcessingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    audioUri: Uri,
    onNavigateToResults: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    LaunchedEffect(audioUri) {
        viewModel.initialize(audioUri)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val splitMethod by viewModel.splitMethod.collectAsState()
    val splitValue by viewModel.splitValue.collectAsState()
    val outputFormat by viewModel.outputFormat.collectAsState()
    val bitrate by viewModel.bitrate.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentSegment by viewModel.currentSegment.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Processing Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState) {
                is ProcessingViewModel.ProcessingUiState.Configuring -> {
                    ConfigurationContent(
                        splitMethod = splitMethod,
                        splitValue = splitValue,
                        outputFormat = outputFormat,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        segments = segments,
                        onSplitMethodChange = viewModel::setSplitMethod,
                        onSplitValueChange = viewModel::setSplitValue,
                        onFormatChange = viewModel::setOutputFormat,
                        onBitrateChange = viewModel::setBitrate,
                        onSampleRateChange = viewModel::setSampleRate,
                        onStartProcessing = { viewModel.startProcessing(audioUri) }
                    )
                }
                is ProcessingViewModel.ProcessingUiState.Processing -> {
                    ProcessingContent(
                        progress = progress,
                        currentSegment = currentSegment,
                        totalSegments = segments.size,
                        statusMessage = statusMessage
                    )
                }
                is ProcessingViewModel.ProcessingUiState.Completed -> {
                    LaunchedEffect(Unit) {
                        onNavigateToResults((uiState as ProcessingViewModel.ProcessingUiState.Completed).jobId)
                    }
                }
                is ProcessingViewModel.ProcessingUiState.Error -> {
                    ErrorContent(
                        message = (uiState as ProcessingViewModel.ProcessingUiState.Error).message,
                        onRetry = { viewModel.startProcessing(audioUri) },
                        onCancel = onNavigateBack
                    )
                }
                else -> { }
            }
        }
    }
}

@Composable
fun ConfigurationContent(
    splitMethod: SplitMethod,
    splitValue: Float,
    outputFormat: AudioFormat,
    bitrate: Int,
    sampleRate: Int,
    segments: List<com.audioslice.pro.data.model.AudioSegment>,
    onSplitMethodChange: (SplitMethod) -> Unit,
    onSplitValueChange: (Float) -> Unit,
    onFormatChange: (AudioFormat) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onStartProcessing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Split Method",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    SplitMethodButton(
                        text = "By Size",
                        selected = splitMethod is SplitMethod.BySize,
                        onClick = { onSplitMethodChange(SplitMethod.BySize()) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SplitMethodButton(
                        text = "By Duration",
                        selected = splitMethod is SplitMethod.ByDuration,
                        onClick = { onSplitMethodChange(SplitMethod.ByDuration()) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${splitMethod.displayName}: ${splitValue.toInt()} ${splitMethod.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Slider(
                    value = splitValue,
                    onValueChange = onSplitValueChange,
                    valueRange = splitMethod.minValue..splitMethod.maxValue,
                    steps = ((splitMethod.maxValue - splitMethod.minValue) / 10).toInt() - 1
                )
                
                Text(
                    text = "${segments.size} segments will be created",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioFormat.values().take(3).forEach { format ->
                        FormatButton(
                            format = format,
                            selected = outputFormat == format,
                            onClick = { onFormatChange(format) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioFormat.values().drop(3).forEach { format ->
                        FormatButton(
                            format = format,
                            selected = outputFormat == format,
                            onClick = { onFormatChange(format) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                if (!outputFormat.isLossless) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Bitrate: ${bitrate} kbps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        outputFormat.supportedBitrates.forEach { rate ->
                            FilterChip(
                                selected = bitrate == rate,
                                onClick = { onBitrateChange(rate) },
                                label = { Text("${rate}k") }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Sample Rate",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(22050, 44100, 48000).forEach { rate ->
                        FilterChip(
                            selected = sampleRate == rate,
                            onClick = { onSampleRateChange(rate) },
                            label = { Text("${rate / 1000} kHz") }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onStartProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Splitting (${segments.size} segments)")
        }
    }
}

@Composable
fun SplitMethodButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FormatButton(
    format: AudioFormat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) format.color.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) format.color
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = format.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) format.color
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = format.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (format.isLossless) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lossless",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun ProcessingContent(
    progress: Float,
    currentSegment: Int,
    totalSegments: Int,
    statusMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = "${currentSegment + 1} / $totalSegments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Please don't close the app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Processing Error",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

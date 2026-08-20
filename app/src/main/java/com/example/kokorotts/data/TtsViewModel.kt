package com.example.kokorotts.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager(application)
    private val ttsEngine = KokoroTtsEngine(application)
    val audioPlayer = AudioPlayerManager(application)
    val resourceMonitor = SystemResourceMonitor(application)

    val modelStatus: StateFlow<ModelStatus> = modelManager.status
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPositionMs: StateFlow<Int> = audioPlayer.currentPositionMs
    val durationMs: StateFlow<Int> = audioPlayer.durationMs
    val isAudioReady: StateFlow<Boolean> = audioPlayer.isPrepared

    val resourceHistory: StateFlow<List<ResourceDataPoint>> = resourceMonitor.history
    val currentResource: StateFlow<ResourceDataPoint> = resourceMonitor.currentData

    private val _inputText = MutableStateFlow(
        "Welcome to Kokoro TTS on Android! It delivers blazing fast, studio quality text to speech completely on-device."
    )
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedSpeaker = MutableStateFlow(KokoroSpeakerCatalog.speakers.first())
    val selectedSpeaker: StateFlow<KokoroSpeaker> = _selectedSpeaker.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastMetrics = MutableStateFlow<GenerationMetrics?>(null)
    val lastMetrics: StateFlow<GenerationMetrics?> = _lastMetrics.asStateFlow()

    private val _lastWavPath = MutableStateFlow<String?>(null)
    val lastWavPath: StateFlow<String?> = _lastWavPath.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        audioPlayer.attachScope(viewModelScope)
        resourceMonitor.startMonitoring(viewModelScope, intervalMs = 1000L)
        initializeModelAndEngine()
    }

    fun initializeModelAndEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            _errorMessage.value = null
            val ready = modelManager.ensureModelReady()
            if (ready) {
                val modelDir = modelManager.getModelDirectory()
                val ttsReady = ttsEngine.initialize(modelDir)
                if (!ttsReady) {
                    _errorMessage.value = "Failed to initialize Kokoro TTS engine."
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onSpeakerSelected(speaker: KokoroSpeaker) {
        _selectedSpeaker.value = speaker
    }

    fun onSpeedChanged(speed: Float) {
        _speed.value = (speed * 10).toInt() / 10f
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun generateAudio() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _errorMessage.value = "Please enter some text to generate speech."
            return
        }

        if (_isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            val result = ttsEngine.generateSpeech(
                text = text,
                speakerId = _selectedSpeaker.value.id,
                speed = _speed.value
            )

            _isGenerating.value = false

            result.onSuccess { genResult ->
                _lastMetrics.value = genResult.metrics
                _lastWavPath.value = genResult.wavFilePath
                audioPlayer.loadAudio(genResult.wavFilePath, autoPlay = true)
            }.onFailure { error ->
                _errorMessage.value = "Generation failed: ${error.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun togglePlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun seekAudio(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        resourceMonitor.stopMonitoring()
        ttsEngine.release()
    }
}

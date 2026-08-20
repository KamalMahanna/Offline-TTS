package com.example.kokorotts.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TtsViewModel"
    }

    private val modelManager = ModelManager(application)
    private val ttsEngine = KokoroTtsEngine(application)
    val audioPlayer = AudioPlayerManager(application)
    val resourceMonitor = SystemResourceMonitor(application)

    val modelStatus: StateFlow<ModelStatus> = modelManager.status
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPositionMs: StateFlow<Int> = audioPlayer.currentPositionMs
    val durationMs: StateFlow<Int> = audioPlayer.durationMs
    val isAudioReady: StateFlow<Boolean> = audioPlayer.isPrepared
    val isEngineReady: StateFlow<Boolean> = ttsEngine.isEngineReady

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
            Log.i(TAG, "Starting model verification and engine initialization...")
            modelManager.setStatus(ModelStatus.Loading("Preparing Kokoro TTS model files...", -1))
            val ready = modelManager.ensureModelReady()
            if (ready) {
                modelManager.setStatus(ModelStatus.Loading("Initializing neural voice synthesizer...", -1))
                val modelDir = modelManager.getModelDirectory()
                val ttsReady = ttsEngine.initialize(modelDir)
                if (!ttsReady) {
                    val err = "Failed to initialize Kokoro ONNX runtime engine."
                    Log.e(TAG, err)
                    modelManager.setStatus(ModelStatus.Error(err))
                    _errorMessage.value = err
                } else {
                    Log.i(TAG, "Kokoro TTS Engine & Model ready for speech synthesis.")
                    modelManager.setStatus(ModelStatus.Ready)
                }
            } else {
                val currentStatus = modelManager.status.value
                if (currentStatus is ModelStatus.Error) {
                    _errorMessage.value = currentStatus.errorMessage
                }
            }
        }
    }

    fun retryInitialization() {
        initializeModelAndEngine()
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

        if (!ttsEngine.isEngineReady.value) {
            _errorMessage.value = "Kokoro TTS engine is initializing, please wait a moment..."
            initializeModelAndEngine()
            return
        }

        if (_isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            Log.i(TAG, "generateAudio triggered: speaker=${_selectedSpeaker.value.name}, speed=${_speed.value}, text='$text'")
            val result = ttsEngine.generateSpeech(
                text = text,
                speakerId = _selectedSpeaker.value.id,
                speed = _speed.value
            )

            _isGenerating.value = false

            result.onSuccess { genResult ->
                Log.i(TAG, "Audio synthesis succeeded. Duration: ${genResult.metrics.audioDurationSeconds}s, RTF: ${genResult.metrics.rtf}")
                _lastMetrics.value = genResult.metrics
                _lastWavPath.value = genResult.wavFilePath
                audioPlayer.loadAudio(genResult.wavFilePath, autoPlay = true)
            }.onFailure { error ->
                Log.e(TAG, "Audio synthesis failed", error)
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

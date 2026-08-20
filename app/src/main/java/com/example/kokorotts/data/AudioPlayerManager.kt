package com.example.kokorotts.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var scope: CoroutineScope? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()

    private var currentFilePath: String? = null

    fun attachScope(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun loadAudio(filePath: String, autoPlay: Boolean = true) {
        val file = File(filePath)
        if (!file.exists()) return

        currentFilePath = filePath
        releasePlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(filePath)
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration
                    _currentPositionMs.value = 0
                    _isPrepared.value = true
                    if (autoPlay) {
                        mp.start()
                        _isPlaying.value = true
                        startProgressTracker()
                    }
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = durationMs.value
                    stopProgressTracker()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    _isPrepared.value = false
                    stopProgressTracker()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            _isPlaying.value = false
            _isPrepared.value = false
        }
    }

    fun play() {
        mediaPlayer?.let { mp ->
            if (_isPrepared.value && !mp.isPlaying) {
                // If reached end, restart from beginning
                if (_currentPositionMs.value >= _durationMs.value) {
                    mp.seekTo(0)
                    _currentPositionMs.value = 0
                }
                mp.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                stopProgressTracker()
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Int) {
        val target = positionMs.coerceIn(0, _durationMs.value)
        _currentPositionMs.value = target
        mediaPlayer?.let { mp ->
            if (_isPrepared.value) {
                mp.seekTo(target)
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope?.launch(Dispatchers.Main) {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition
                    }
                }
                delay(40L) // 25fps smooth progress update
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        stopProgressTracker()
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        _isPlaying.value = false
        _isPrepared.value = false
    }

    fun release() {
        releasePlayer()
        scope = null
    }
}

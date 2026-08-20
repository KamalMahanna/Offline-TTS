package com.example.kokorotts.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
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

    companion object {
        private const val TAG = "AudioPlayerManager"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
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

    private var isStreamingActive = false
    private var streamedSamplesCount = 0L
    private var streamingSampleRate = 24000

    fun attachScope(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    /**
     * Initializes low-latency streaming output via AudioTrack.
     */
    fun startStreaming(sampleRate: Int = 24000) {
        releasePlayer()
        releaseAudioTrack()

        try {
            streamingSampleRate = sampleRate
            streamedSamplesCount = 0L
            isStreamingActive = true

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 4).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            _isPlaying.value = true
            _isPrepared.value = true
            _currentPositionMs.value = 0
            _durationMs.value = 0

            startStreamingProgressTracker()
            Log.i(TAG, "AudioTrack streaming started at $sampleRate Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack streaming", e)
            isStreamingActive = false
            _isPlaying.value = false
        }
    }

    /**
     * Feeds synthesized sentence PCM samples into the active AudioTrack stream.
     */
    fun streamChunk(samples: FloatArray) {
        if (!isStreamingActive || audioTrack == null || samples.isEmpty()) return

        try {
            val pcmShorts = ShortArray(samples.size)
            for (i in samples.indices) {
                val clamped = samples[i].coerceIn(-1.0f, 1.0f)
                pcmShorts[i] = (clamped * 32767.0f).toInt().toShort()
            }

            audioTrack?.write(pcmShorts, 0, pcmShorts.size)
            streamedSamplesCount += samples.size
            _durationMs.value = ((streamedSamplesCount * 1000L) / streamingSampleRate).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chunk to AudioTrack", e)
        }
    }

    /**
     * Finishes the real-time stream and seamlessly loads the complete WAV file for seekable control.
     */
    fun finishStreaming(finalWavPath: String) {
        scope?.launch(Dispatchers.IO) {
            try {
                // Allow AudioTrack buffer to drain remaining audio
                val track = audioTrack
                if (track != null && isStreamingActive) {
                    val remainingMs = ((track.bufferSizeInFrames.toFloat() / streamingSampleRate.toFloat()) * 1000).toLong()
                    delay(remainingMs.coerceIn(100L, 800L))
                }
            } catch (_: Exception) {}

            isStreamingActive = false
            stopProgressTracker()
            releaseAudioTrack()

            // Load complete WAV file so seekbar and full playback controls work seamlessly
            loadAudio(finalWavPath, autoPlay = false)
        }
    }

    fun loadAudio(filePath: String, autoPlay: Boolean = true) {
        val file = File(filePath)
        if (!file.exists()) return

        releasePlayer()
        releaseAudioTrack()

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
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio file", e)
            _isPlaying.value = false
            _isPrepared.value = false
        }
    }

    fun play() {
        if (isStreamingActive) {
            audioTrack?.play()
            _isPlaying.value = true
            return
        }

        mediaPlayer?.let { mp ->
            if (_isPrepared.value && !mp.isPlaying) {
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
        if (isStreamingActive) {
            audioTrack?.pause()
            _isPlaying.value = false
            return
        }

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
        if (isStreamingActive) return // Seeking is disabled during active real-time streaming

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
                delay(40L)
            }
        }
    }

    private fun startStreamingProgressTracker() {
        stopProgressTracker()
        progressJob = scope?.launch(Dispatchers.Main) {
            while (isActive && isStreamingActive) {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING && streamingSampleRate > 0) {
                        val playbackHeadPosition = track.playbackHeadPosition
                        _currentPositionMs.value = ((playbackHeadPosition.toLong() * 1000L) / streamingSampleRate).toInt()
                    }
                }
                delay(40L)
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
    }

    private fun releaseAudioTrack() {
        audioTrack?.let { track ->
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
        audioTrack = null
    }

    fun release() {
        isStreamingActive = false
        releasePlayer()
        releaseAudioTrack()
        _isPlaying.value = false
        _isPrepared.value = false
        scope = null
    }
}

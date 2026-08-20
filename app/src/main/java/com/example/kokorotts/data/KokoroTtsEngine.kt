package com.example.kokorotts.data

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TtsGenerationResult(
    val wavFilePath: String,
    val metrics: GenerationMetrics,
    val sampleRate: Int,
    val sampleCount: Int
)

class KokoroTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "KokoroTtsEngine"

        /**
         * Automatically detects optimal CPU threads for mobile ARM architecture.
         * Using 2-4 performance threads prevents thermal throttling, UI thread starvation,
         * and CPU lockups.
         */
        fun getOptimalThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores / 2).coerceIn(2, 4)
        }

        fun splitIntoSentences(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            val parts = text.split(Regex("(?<=[.!?\\n])\\s+"))
            val sentences = mutableListOf<String>()
            for (p in parts) {
                val trimmed = p.trim()
                if (trimmed.isNotEmpty()) {
                    sentences.add(trimmed)
                }
            }
            return if (sentences.isEmpty()) listOf(text.trim()) else sentences
        }
    }

    private var tts: OfflineTts? = null
    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    suspend fun initialize(
        modelDir: File,
        numThreads: Int = getOptimalThreadCount()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            release()

            val modelFile = File(modelDir, "model.onnx")
            val voicesFile = File(modelDir, "voices.bin")
            val tokensFile = File(modelDir, "tokens.txt")
            val dataDir = File(modelDir, "espeak-ng-data")

            if (!modelFile.exists() || !voicesFile.exists() || !tokensFile.exists() || !dataDir.exists()) {
                Log.e(TAG, "Required model files missing in ${modelDir.absolutePath}")
                _isEngineReady.value = false
                return@withContext false
            }

            Log.i(TAG, "Initializing Sherpa-ONNX with Kokoro model (using $numThreads CPU threads, detected ${Runtime.getRuntime().availableProcessors()} cores)...")

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = modelFile.absolutePath,
                voices = voicesFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = dataDir.absolutePath,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro = kokoroConfig,
                numThreads = numThreads,
                debug = false,
                provider = "cpu"
            )

            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 2,
                silenceScale = 0.2f
            )

            val createdTts = OfflineTts(assetManager = null, config = ttsConfig)
            tts = createdTts
            _isEngineReady.value = true
            Log.i(TAG, "Kokoro TTS engine initialized successfully. sampleRate=${createdTts.sampleRate()}, numSpeakers=${createdTts.numSpeakers()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Kokoro TTS engine", e)
            _isEngineReady.value = false
            false
        }
    }

    /**
     * Synthesizes text sentence by sentence without JNI callback trampolining.
     * Emits audio chunks as each sentence finishes so AudioTrack starts playing immediately.
     */
    suspend fun generateSpeechStream(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
        onAudioChunk: (FloatArray) -> Unit
    ): Result<TtsGenerationResult> = withContext(Dispatchers.IO) {
        val currentTts = tts
        if (currentTts == null || !_isEngineReady.value) {
            Log.e(TAG, "generateSpeechStream failed: Engine is not initialized")
            return@withContext Result.failure(IllegalStateException("Kokoro TTS Engine not initialized"))
        }

        try {
            val maxSpeakers = currentTts.numSpeakers()
            val validSid = if (maxSpeakers > 0) speakerId.coerceIn(0, maxSpeakers - 1) else 0
            val sentences = splitIntoSentences(text)
            Log.i(TAG, "Starting sentence-level streaming (${sentences.size} sentences), speakerId=$speakerId (clamped=$validSid), speed=$speed")

            val startTime = System.currentTimeMillis()
            var firstChunkLatencyMs = 0L
            var sampleRate = currentTts.sampleRate().takeIf { it > 0 } ?: 24000
            val allSamples = ArrayList<Float>(24000 * 5)

            for ((index, sentence) in sentences.withIndex()) {
                val sentenceStart = System.currentTimeMillis()
                val genAudio = currentTts.generate(
                    text = sentence,
                    sid = validSid,
                    speed = speed
                )
                val sentenceLatency = System.currentTimeMillis() - sentenceStart
                val chunkSamples = genAudio.samples

                if (chunkSamples.isNotEmpty()) {
                    if (firstChunkLatencyMs == 0L) {
                        firstChunkLatencyMs = System.currentTimeMillis() - startTime
                        Log.i(TAG, "Sentence 1 generated in ${firstChunkLatencyMs}ms (TTFA), ${chunkSamples.size} samples")
                    } else {
                        Log.i(TAG, "Sentence ${index + 1}/${sentences.size} generated in ${sentenceLatency}ms, ${chunkSamples.size} samples")
                    }

                    if (genAudio.sampleRate > 0) {
                        sampleRate = genAudio.sampleRate
                    }

                    for (s in chunkSamples) {
                        allSamples.add(s)
                    }

                    // Feed chunk to player
                    onAudioChunk(chunkSamples)
                }
            }

            val endTime = System.currentTimeMillis()
            val totalLatencyMs = endTime - startTime

            val samplesArray = if (allSamples.isNotEmpty()) {
                FloatArray(allSamples.size) { allSamples[it] }
            } else {
                FloatArray(0)
            }

            Log.i(TAG, "Streaming completed in ${totalLatencyMs}ms (TTFA: ${firstChunkLatencyMs}ms). Total samples: ${samplesArray.size}, SampleRate: $sampleRate")

            if (samplesArray.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Engine generated 0 audio samples."))
            }

            val audioDurationSeconds = if (sampleRate > 0) samplesArray.size.toFloat() / sampleRate.toFloat() else 0f
            val rtf = if (audioDurationSeconds > 0) (totalLatencyMs / 1000f) / audioDurationSeconds else 0f

            val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            val chars = text.length

            val metrics = GenerationMetrics(
                latencyMs = totalLatencyMs,
                audioDurationSeconds = audioDurationSeconds,
                sampleRate = sampleRate,
                sampleCount = samplesArray.size,
                rtf = rtf,
                characterCount = chars,
                wordCount = words
            )

            // Save combined WAV file
            val outputWavFile = File(context.cacheDir, "tts_output_${System.currentTimeMillis()}.wav")
            outputWavFile.parentFile?.mkdirs()
            writePcmFloatToWav(outputWavFile, samplesArray, sampleRate)
            val finalWavPath = outputWavFile.absolutePath

            Log.i(TAG, "Saved combined generated WAV to $finalWavPath (${File(finalWavPath).length()} bytes)")

            Result.success(
                TtsGenerationResult(
                    wavFilePath = finalWavPath,
                    metrics = metrics,
                    sampleRate = sampleRate,
                    sampleCount = samplesArray.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Streaming generation error", e)
            Result.failure(e)
        }
    }

    suspend fun generateSpeech(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f
    ): Result<TtsGenerationResult> = generateSpeechStream(text, speakerId, speed) {}

    private fun writePcmFloatToWav(file: File, samples: FloatArray, sampleRate: Int) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())
            // fmt chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // chunk size
            header.putShort(1.toShort()) // PCM format
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            // data chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Convert Float32 [-1.0, 1.0] to PCM16
            val pcmBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val pcmShort = (clamped * 32767.0f).toInt().toShort()
                pcmBuffer.putShort(pcmShort)
            }
            fos.write(pcmBuffer.array())
            fos.flush()
        }
    }

    fun release() {
        try {
            tts?.release()
        } catch (_: Exception) {}
        tts = null
        _isEngineReady.value = false
    }
}

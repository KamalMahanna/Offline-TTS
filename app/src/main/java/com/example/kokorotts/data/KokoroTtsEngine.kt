package com.example.kokorotts.data

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
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
         * Automatically detects the total number of available CPU cores on the device.
         */
        fun getOptimalThreadCount(): Int {
            return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
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

    suspend fun generateSpeech(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f
    ): Result<TtsGenerationResult> = withContext(Dispatchers.IO) {
        val currentTts = tts
        if (currentTts == null || !_isEngineReady.value) {
            Log.e(TAG, "generateSpeech failed: Engine is not initialized")
            return@withContext Result.failure(IllegalStateException("Kokoro TTS Engine not initialized"))
        }

        try {
            val maxSpeakers = currentTts.numSpeakers()
            val validSid = if (maxSpeakers > 0) speakerId.coerceIn(0, maxSpeakers - 1) else 0
            Log.i(TAG, "Starting speech generation: length=${text.length}, speakerId=$speakerId (clamped=$validSid, totalSpeakers=$maxSpeakers), speed=$speed")
            val startTime = System.currentTimeMillis()

            // Run Kokoro TTS inference
            val generatedAudio: GeneratedAudio = currentTts.generate(
                text = text,
                sid = validSid,
                speed = speed
            )

            val endTime = System.currentTimeMillis()
            val latencyMs = endTime - startTime

            val samples = generatedAudio.samples
            val sampleRate = generatedAudio.sampleRate
            Log.i(TAG, "Inference completed in ${latencyMs}ms. Samples=${samples?.size}, SampleRate=$sampleRate")

            if (samples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Engine generated 0 audio samples."))
            }

            val audioDurationSeconds = if (sampleRate > 0) samples.size.toFloat() / sampleRate.toFloat() else 0f
            val rtf = if (audioDurationSeconds > 0) (latencyMs / 1000f) / audioDurationSeconds else 0f

            val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            val chars = text.length

            val metrics = GenerationMetrics(
                latencyMs = latencyMs,
                audioDurationSeconds = audioDurationSeconds,
                sampleRate = sampleRate,
                sampleCount = samples.size,
                rtf = rtf,
                characterCount = chars,
                wordCount = words
            )

            // Save WAV file to app cache directory
            val outputWavFile = File(context.cacheDir, "tts_output_${System.currentTimeMillis()}.wav")
            outputWavFile.parentFile?.mkdirs()
            val savedSuccessfully = generatedAudio.save(outputWavFile.absolutePath)

            val finalWavPath = if (savedSuccessfully && outputWavFile.exists() && outputWavFile.length() > 44) {
                outputWavFile.absolutePath
            } else {
                // Fallback manual WAV writer
                writePcmFloatToWav(outputWavFile, samples, sampleRate)
                outputWavFile.absolutePath
            }

            Log.i(TAG, "Saved generated WAV to $finalWavPath (${File(finalWavPath).length()} bytes)")

            Result.success(
                TtsGenerationResult(
                    wavFilePath = finalWavPath,
                    metrics = metrics,
                    sampleRate = sampleRate,
                    sampleCount = samples.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Speech generation error", e)
            Result.failure(e)
        }
    }

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

package com.example.kokorotts.data

import android.content.Context
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TtsGenerationResult(
    val wavFilePath: String,
    val metrics: GenerationMetrics,
    val sampleRate: Int,
    val sampleCount: Int
)

class KokoroTtsEngine(private val context: Context) {

    private var tts: OfflineTts? = null
    private var isInitialized = false

    suspend fun initialize(modelDir: File, numThreads: Int = 4): Boolean = withContext(Dispatchers.IO) {
        try {
            release()

            val modelPath = File(modelDir, "model.onnx").absolutePath
            val voicesPath = File(modelDir, "voices.bin").absolutePath
            val tokensPath = File(modelDir, "tokens.txt").absolutePath
            val dataDirPath = File(modelDir, "espeak-ng-data").absolutePath

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = modelPath,
                voices = voicesPath,
                tokens = tokensPath,
                dataDir = dataDirPath,
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

            tts = OfflineTts(assetManager = null, config = ttsConfig)
            isInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            false
        }
    }

    suspend fun generateSpeech(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f
    ): Result<TtsGenerationResult> = withContext(Dispatchers.IO) {
        val currentTts = tts
        if (currentTts == null || !isInitialized) {
            return@withContext Result.failure(IllegalStateException("Kokoro TTS Engine not initialized"))
        }

        try {
            val startTime = System.currentTimeMillis()

            // Run Kokoro TTS inference
            val generatedAudio: GeneratedAudio = currentTts.generate(
                text = text,
                sid = speakerId,
                speed = speed
            )

            val endTime = System.currentTimeMillis()
            val latencyMs = endTime - startTime

            val samples = generatedAudio.samples
            val sampleRate = generatedAudio.sampleRate
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
            val savedSuccessfully = generatedAudio.save(outputWavFile.absolutePath)

            val finalWavPath = if (savedSuccessfully && outputWavFile.exists() && outputWavFile.length() > 44) {
                outputWavFile.absolutePath
            } else {
                // Fallback manual WAV writer
                writePcmFloatToWav(outputWavFile, samples, sampleRate)
                outputWavFile.absolutePath
            }

            Result.success(
                TtsGenerationResult(
                    wavFilePath = finalWavPath,
                    metrics = metrics,
                    sampleRate = sampleRate,
                    sampleCount = samples.size
                )
            )
        } catch (e: Exception) {
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
        isInitialized = false
    }
}

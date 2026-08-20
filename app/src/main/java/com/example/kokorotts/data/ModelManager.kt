package com.example.kokorotts.data

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelStatus {
    object Idle : ModelStatus()
    data class Loading(val message: String, val progressPercent: Int = -1) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val errorMessage: String) : ModelStatus()
}

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_DIR_NAME = "kokoro-en-v0_19"
        private const val MIN_MODEL_SIZE = 50_000_000L  // ~345 MB expected, min 50 MB
        private const val MIN_VOICES_SIZE = 1_000_000L  // ~5.7 MB expected, min 1 MB
        private const val MIN_TOKENS_SIZE = 500L        // ~1 KB expected, min 500 B
        private const val MIN_ESPEAK_FILES = 10         // Directory contains >100 files
    }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun setStatus(newStatus: ModelStatus) {
        _status.value = newStatus
    }

    fun getModelDirectory(): File {
        return File(context.filesDir, MODEL_DIR_NAME)
    }

    /**
     * Verifies that all required files exist and meet minimum size requirements.
     */
    fun validateModelFiles(targetDir: File): Boolean {
        val modelFile = File(targetDir, "model.onnx")
        val voicesFile = File(targetDir, "voices.bin")
        val tokensFile = File(targetDir, "tokens.txt")
        val espeakDir = File(targetDir, "espeak-ng-data")

        val modelValid = modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE
        val voicesValid = voicesFile.exists() && voicesFile.length() >= MIN_VOICES_SIZE
        val tokensValid = tokensFile.exists() && tokensFile.length() >= MIN_TOKENS_SIZE
        val espeakValid = espeakDir.exists() && espeakDir.isDirectory && (espeakDir.listFiles()?.size ?: 0) >= MIN_ESPEAK_FILES

        return modelValid && voicesValid && tokensValid && espeakValid
    }

    /**
     * Cleans up corrupted or truncated files to prevent invalid engine states.
     */
    private fun cleanCorruptedFiles(targetDir: File) {
        val modelFile = File(targetDir, "model.onnx")
        if (modelFile.exists() && modelFile.length() < MIN_MODEL_SIZE) {
            Log.w(TAG, "Deleting corrupt/incomplete model.onnx (${modelFile.length()} bytes)")
            modelFile.delete()
        }

        val voicesFile = File(targetDir, "voices.bin")
        if (voicesFile.exists() && voicesFile.length() < MIN_VOICES_SIZE) {
            Log.w(TAG, "Deleting corrupt/incomplete voices.bin (${voicesFile.length()} bytes)")
            voicesFile.delete()
        }

        val tokensFile = File(targetDir, "tokens.txt")
        if (tokensFile.exists() && tokensFile.length() < MIN_TOKENS_SIZE) {
            Log.w(TAG, "Deleting corrupt/incomplete tokens.txt (${tokensFile.length()} bytes)")
            tokensFile.delete()
        }

        val espeakDir = File(targetDir, "espeak-ng-data")
        if (espeakDir.exists() && (espeakDir.listFiles()?.size ?: 0) < MIN_ESPEAK_FILES) {
            Log.w(TAG, "Deleting incomplete espeak-ng-data directory")
            espeakDir.deleteRecursively()
        }
    }

    suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = getModelDirectory()

        // 1. Check if valid model files already exist in local app storage
        if (validateModelFiles(targetDir)) {
            Log.i(TAG, "Model files verified in local storage.")
            _status.value = ModelStatus.Ready
            return@withContext true
        }

        // Clean up partial/corrupted files before proceeding
        cleanCorruptedFiles(targetDir)

        // 2. Check if assets contain the model files and extract
        if (hasModelInAssets()) {
            _status.value = ModelStatus.Loading("Extracting Kokoro TTS model from APK assets...", 0)
            val success = copyModelFromAssets(targetDir)
            if (success && validateModelFiles(targetDir)) {
                Log.i(TAG, "Extracted and verified model from assets.")
                _status.value = ModelStatus.Ready
                return@withContext true
            }
        }

        // 3. Fallback: Download missing model files directly from repository
        _status.value = ModelStatus.Loading("Downloading Kokoro TTS model...", 0)
        val downloadSuccess = downloadModelFiles(targetDir)
        if (downloadSuccess && validateModelFiles(targetDir)) {
            Log.i(TAG, "Downloaded and verified model files.")
            _status.value = ModelStatus.Ready
            return@withContext true
        }

        _status.value = ModelStatus.Error("Kokoro model files are missing or corrupted. Please check network connection.")
        false
    }

    private fun hasModelInAssets(): Boolean {
        return try {
            val list = context.assets.list(MODEL_DIR_NAME) ?: emptyArray()
            list.contains("model.onnx") &&
            list.contains("voices.bin") &&
            list.contains("tokens.txt") &&
            list.contains("espeak-ng-data")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking assets", e)
            false
        }
    }

    private suspend fun copyModelFromAssets(targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            copyAssetFolder(context.assets, MODEL_DIR_NAME, targetDir.absolutePath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract assets", e)
            _status.value = ModelStatus.Error("Failed to extract assets: ${e.localizedMessage}")
            cleanCorruptedFiles(targetDir)
            false
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String) {
        val files = assetManager.list(fromAssetPath) ?: return
        val targetDir = File(toPath)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        for (file in files) {
            val srcPath = if (fromAssetPath.isEmpty()) file else "$fromAssetPath/$file"
            val dstPath = "$toPath/$file"
            val subFiles = assetManager.list(srcPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(assetManager, srcPath, dstPath)
            } else {
                copyAssetFile(assetManager, srcPath, dstPath)
            }
        }
    }

    private fun copyAssetFile(assetManager: AssetManager, srcAssetPath: String, dstPath: String) {
        val destFile = File(dstPath)
        if (destFile.exists() && destFile.length() > 0) return

        destFile.parentFile?.mkdirs()
        val tmpFile = File("${dstPath}.tmp")

        assetManager.open(srcAssetPath).use { inStream ->
            FileOutputStream(tmpFile).use { outStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
            }
        }

        if (tmpFile.exists() && tmpFile.length() > 0) {
            tmpFile.renameTo(destFile)
        }
    }

    private suspend fun downloadModelFiles(targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            val baseUrl = "https://huggingface.co/csukuangfj/kokoro-en-v0_19/resolve/main"

            // Core model files
            val filesToDownload = mutableListOf(
                "tokens.txt" to File(targetDir, "tokens.txt"),
                "voices.bin" to File(targetDir, "voices.bin"),
                "model.onnx" to File(targetDir, "model.onnx")
            )

            // Essential espeak-ng-data phoneme files
            val espeakDir = File(targetDir, "espeak-ng-data")
            if (!espeakDir.exists()) espeakDir.mkdirs()

            val espeakFiles = listOf(
                "phondata", "phondata-manifest", "phonindex", "phontab",
                "intonations", "en_dict"
            )
            for (ef in espeakFiles) {
                filesToDownload.add("espeak-ng-data/$ef" to File(espeakDir, ef))
            }

            for ((remoteRelativePath, localFile) in filesToDownload) {
                if (localFile.exists() && localFile.length() > 0) continue

                localFile.parentFile?.mkdirs()
                val tmpFile = File(localFile.parentFile, "${localFile.name}.tmp")

                _status.value = ModelStatus.Loading("Downloading ${localFile.name}...", -1)
                val url = URL("$baseUrl/$remoteRelativePath")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    Log.e(TAG, "HTTP error $responseCode while downloading $remoteRelativePath")
                    conn.disconnect()
                    tmpFile.delete()
                    continue
                }

                val totalBytes = conn.contentLengthLong
                var downloadedBytes = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                _status.value = ModelStatus.Loading("Downloading ${localFile.name} ($percent%)...", percent)
                            }
                        }
                        output.flush()
                    }
                }

                if (tmpFile.exists() && tmpFile.length() > 0) {
                    tmpFile.renameTo(localFile)
                }
            }

            validateModelFiles(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model files", e)
            false
        }
    }
}

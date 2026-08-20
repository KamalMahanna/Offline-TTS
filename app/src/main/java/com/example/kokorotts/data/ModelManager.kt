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
     * Verifies that all required Kokoro files, espeak-ng tables, language files, and voice profiles exist.
     */
    fun validateModelFiles(targetDir: File): Boolean {
        val modelFile = File(targetDir, "model.onnx")
        val voicesFile = File(targetDir, "voices.bin")
        val tokensFile = File(targetDir, "tokens.txt")
        val espeakDir = File(targetDir, "espeak-ng-data")

        val modelValid = modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE
        val voicesValid = voicesFile.exists() && voicesFile.length() >= MIN_VOICES_SIZE
        val tokensValid = tokensFile.exists() && tokensFile.length() >= MIN_TOKENS_SIZE
        val espeakValid = espeakDir.exists() && espeakDir.isDirectory &&
                File(espeakDir, "phondata").exists() &&
                File(espeakDir, "phonindex").exists() &&
                File(espeakDir, "phontab").exists() &&
                File(espeakDir, "en_dict").exists() &&
                File(espeakDir, "lang/gmw/en-US").exists() &&
                File(espeakDir, "lang/gmw/en").exists() &&
                File(espeakDir, "voices").exists()

        val isValid = modelValid && voicesValid && tokensValid && espeakValid
        Log.i(
            TAG,
            "Validation check in ${targetDir.absolutePath} -> model: $modelValid (${modelFile.length()} B), voices: $voicesValid (${voicesFile.length()} B), tokens: $tokensValid (${tokensFile.length()} B), espeak: $espeakValid -> Overall: $isValid"
        )
        return isValid
    }

    /**
     * Cleans up corrupted, truncated, or incomplete files to force a clean re-extraction.
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
        if (espeakDir.exists()) {
            val isEspeakComplete = File(espeakDir, "phondata").exists() &&
                    File(espeakDir, "phonindex").exists() &&
                    File(espeakDir, "phontab").exists() &&
                    File(espeakDir, "en_dict").exists() &&
                    File(espeakDir, "lang/gmw/en-US").exists() &&
                    File(espeakDir, "lang/gmw/en").exists() &&
                    File(espeakDir, "voices").exists()

            if (!isEspeakComplete) {
                Log.w(TAG, "Deleting incomplete espeak-ng-data directory to force clean re-extraction")
                espeakDir.deleteRecursively()
            }
        }
    }

    suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = getModelDirectory()

        // 1. Check if valid and complete model files already exist in local app storage
        if (validateModelFiles(targetDir)) {
            Log.i(TAG, "Model files verified and complete in local storage.")
            return@withContext true
        }

        // Clean up partial/corrupted files before proceeding
        cleanCorruptedFiles(targetDir)

        // 2. Check if assets contain the model files and extract
        if (hasModelInAssets()) {
            _status.value = ModelStatus.Loading("Extracting complete Kokoro TTS neural model...", 0)
            val success = copyModelFromAssets(targetDir)
            if (success && validateModelFiles(targetDir)) {
                Log.i(TAG, "Extracted and verified complete model from assets.")
                return@withContext true
            } else {
                Log.w(TAG, "Asset extraction finished but validation failed, falling back to download.")
            }
        } else {
            Log.w(TAG, "Model not found in assets, proceeding to download fallback.")
        }

        // 3. Fallback: Download missing model files directly from repository
        _status.value = ModelStatus.Loading("Downloading Kokoro TTS model...", 0)
        val downloadSuccess = downloadModelFiles(targetDir)
        if (downloadSuccess && validateModelFiles(targetDir)) {
            Log.i(TAG, "Downloaded and verified model files.")
            return@withContext true
        }

        _status.value = ModelStatus.Error("Kokoro model files are missing or corrupted. Please check network connection.")
        false
    }

    private fun hasModelInAssets(): Boolean {
        return try {
            val rootList = context.assets.list(MODEL_DIR_NAME) ?: emptyArray()
            val hasRootFiles = rootList.contains("model.onnx") &&
                    rootList.contains("voices.bin") &&
                    rootList.contains("tokens.txt")

            val espeakList = context.assets.list("$MODEL_DIR_NAME/espeak-ng-data") ?: emptyArray()
            val hasEspeak = espeakList.isNotEmpty()

            Log.i(TAG, "Asset check - hasRootFiles: $hasRootFiles, espeakFilesCount: ${espeakList.size}")
            hasRootFiles && hasEspeak
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
            _status.value = ModelStatus.Loading("Extracting Kokoro TTS model...", 0)
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
        val items = assetManager.list(fromAssetPath) ?: return
        val targetDir = File(toPath)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        for (item in items) {
            val srcPath = if (fromAssetPath.isEmpty()) item else "$fromAssetPath/$item"
            val dstPath = "$toPath/$item"

            var isFile = false
            try {
                // In Android AssetManager, open() succeeds on files and throws on directories
                assetManager.open(srcPath).use {
                    isFile = true
                }
            } catch (_: Exception) {
                isFile = false
            }

            if (isFile) {
                copyAssetFile(assetManager, srcPath, dstPath)
            } else {
                copyAssetFolder(assetManager, srcPath, dstPath)
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
                val buffer = ByteArray(256 * 1024)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
            }
        }

        if (tmpFile.exists() && tmpFile.length() > 0) {
            if (destFile.exists()) destFile.delete()
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

            // Essential espeak-ng-data phoneme files, language files, and voice profiles
            val espeakDir = File(targetDir, "espeak-ng-data")
            if (!espeakDir.exists()) espeakDir.mkdirs()

            val espeakFiles = listOf(
                "phondata", "phondata-manifest", "phonindex", "phontab",
                "intonations", "en_dict"
            )
            for (ef in espeakFiles) {
                filesToDownload.add("espeak-ng-data/$ef" to File(espeakDir, ef))
            }

            // Add essential English language and voice files
            val langDir = File(espeakDir, "lang/gmw")
            langDir.mkdirs()
            filesToDownload.add("espeak-ng-data/lang/gmw/en" to File(langDir, "en"))
            filesToDownload.add("espeak-ng-data/lang/gmw/en-US" to File(langDir, "en-US"))
            filesToDownload.add("espeak-ng-data/lang/gmw/en-GB-x-rp" to File(langDir, "en-GB-x-rp"))

            val voicesVDir = File(espeakDir, "voices/!v")
            voicesVDir.mkdirs()
            filesToDownload.add("espeak-ng-data/voices/!v/en-us" to File(voicesVDir, "en-us"))

            for ((remoteRelativePath, localFile) in filesToDownload) {
                if (localFile.exists() && localFile.length() > 0) continue

                localFile.parentFile?.mkdirs()
                val tmpFile = File(localFile.parentFile, "${localFile.name}.tmp")

                _status.value = ModelStatus.Loading("Downloading ${localFile.name}...", -1)

                var currentUrl = "$baseUrl/$remoteRelativePath"
                var redirectCount = 0
                var conn: HttpURLConnection? = null
                var inputStream: BufferedInputStream? = null

                while (redirectCount < 5) {
                    val url = URL(currentUrl)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 45000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "KokoroTTS/1.0 (Android; OnDevice)")
                    }

                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        val newUrl = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (newUrl != null) {
                            currentUrl = newUrl
                            redirectCount++
                            continue
                        }
                    }

                    if (code in 200..299) {
                        inputStream = BufferedInputStream(conn.inputStream)
                        break
                    } else {
                        Log.e(TAG, "HTTP error $code while downloading $remoteRelativePath")
                        conn.disconnect()
                        break
                    }
                }

                if (inputStream == null || conn == null) {
                    tmpFile.delete()
                    continue
                }

                val totalBytes = conn.contentLengthLong
                var downloadedBytes = 0L

                inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(128 * 1024)
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

                conn.disconnect()

                if (tmpFile.exists() && tmpFile.length() > 0) {
                    if (localFile.exists()) localFile.delete()
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

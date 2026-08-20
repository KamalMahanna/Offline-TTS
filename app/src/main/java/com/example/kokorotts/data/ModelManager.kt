package com.example.kokorotts.data

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelStatus {
    object Idle : ModelStatus()
    data class Loading(val message: String, val progressPercent: Int = -1) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val errorMessage: String) : ModelStatus()
}

class ModelManager(private val context: Context) {

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val modelDirName = "kokoro-en-v0_19"

    fun getModelDirectory(): File {
        return File(context.filesDir, modelDirName)
    }

    suspend fun ensureModelReady(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = getModelDirectory()
        val modelFile = File(targetDir, "model.onnx")
        val voicesFile = File(targetDir, "voices.bin")
        val tokensFile = File(targetDir, "tokens.txt")
        val espeakDir = File(targetDir, "espeak-ng-data")

        if (modelFile.exists() && modelFile.length() > 50_000_000 &&
            voicesFile.exists() && tokensFile.exists() &&
            espeakDir.exists() && (espeakDir.listFiles()?.isNotEmpty() == true)
        ) {
            _status.value = ModelStatus.Ready
            return@withContext true
        }

        // 1. Check if assets contain the model files
        if (hasModelInAssets()) {
            _status.value = ModelStatus.Loading("Extracting Kokoro model from assets...", 0)
            val success = copyModelFromAssets(targetDir)
            if (success) {
                _status.value = ModelStatus.Ready
                return@withContext true
            }
        }

        // 2. Download missing model files directly from HuggingFace / GitHub if not in assets
        _status.value = ModelStatus.Loading("Downloading Kokoro ONNX model...", 0)
        val downloadSuccess = downloadModelFiles(targetDir)
        if (downloadSuccess) {
            _status.value = ModelStatus.Ready
            return@withContext true
        }

        _status.value = ModelStatus.Error("Kokoro model files not found. Please check your network connection.")
        false
    }

    private fun hasModelInAssets(): Boolean {
        return try {
            val list = context.assets.list(modelDirName) ?: emptyArray()
            list.contains("model.onnx") && list.contains("voices.bin")
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun copyModelFromAssets(targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            copyAssetFolder(context.assets, modelDirName, targetDir.absolutePath)
            true
        } catch (e: Exception) {
            _status.value = ModelStatus.Error("Failed to extract assets: ${e.localizedMessage}")
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
        assetManager.open(srcAssetPath).use { inStream ->
            FileOutputStream(destFile).use { outStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
            }
        }
    }

    private suspend fun downloadModelFiles(targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            val baseUrl = "https://huggingface.co/csukuangfj/kokoro-en-v0_19/resolve/main"
            val filesToDownload = listOf(
                "tokens.txt" to File(targetDir, "tokens.txt"),
                "voices.bin" to File(targetDir, "voices.bin"),
                "model.onnx" to File(targetDir, "model.onnx")
            )

            for ((remoteName, localFile) in filesToDownload) {
                if (localFile.exists() && localFile.length() > 0) continue

                _status.value = ModelStatus.Loading("Downloading $remoteName...", -1)
                val url = URL("$baseUrl/$remoteName")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                val totalBytes = conn.contentLengthLong
                var downloadedBytes = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(localFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                _status.value = ModelStatus.Loading("Downloading $remoteName ($percent%)...", percent)
                            }
                        }
                        output.flush()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

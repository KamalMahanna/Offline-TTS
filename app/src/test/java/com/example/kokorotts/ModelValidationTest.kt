package com.example.kokorotts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelValidationTest {

    @Test
    fun testBundledAssetFilesIntegrity() {
        val assetsDir = File("src/main/assets/kokoro-en-v0_19")
        assertTrue("Assets directory must exist in workspace", assetsDir.exists())

        val modelFile = File(assetsDir, "model.onnx")
        assertTrue("model.onnx must exist in assets", modelFile.exists())
        assertTrue("model.onnx must be >= 50MB (actual: ${modelFile.length()})", modelFile.length() >= 50_000_000L)

        val voicesFile = File(assetsDir, "voices.bin")
        assertTrue("voices.bin must exist in assets", voicesFile.exists())
        assertTrue("voices.bin must be >= 1MB (actual: ${voicesFile.length()})", voicesFile.length() >= 1_000_000L)

        val tokensFile = File(assetsDir, "tokens.txt")
        assertTrue("tokens.txt must exist in assets", tokensFile.exists())
        assertTrue("tokens.txt must be >= 500B (actual: ${tokensFile.length()})", tokensFile.length() >= 500L)

        val espeakDir = File(assetsDir, "espeak-ng-data")
        assertTrue("espeak-ng-data must exist in assets", espeakDir.exists() && espeakDir.isDirectory)
        assertTrue("phondata must exist", File(espeakDir, "phondata").exists())
        assertTrue("phonindex must exist", File(espeakDir, "phonindex").exists())
        assertTrue("phontab must exist", File(espeakDir, "phontab").exists())
        assertTrue("en_dict must exist", File(espeakDir, "en_dict").exists())
        assertTrue("lang/gmw/en-US must exist", File(espeakDir, "lang/gmw/en-US").exists())
        assertTrue("lang/gmw/en must exist", File(espeakDir, "lang/gmw/en").exists())
        assertTrue("voices must exist", File(espeakDir, "voices").exists() && File(espeakDir, "voices").isDirectory)
    }

    @Test
    fun testValidationLogic() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kokoro_test_dir_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // Missing all files -> invalid
            assertFalse(isValidModelDirectory(tempDir))

            // Create model file with enough size
            val modelFile = File(tempDir, "model.onnx")
            val voicesFile = File(tempDir, "voices.bin")
            val tokensFile = File(tempDir, "tokens.txt")
            val espeakDir = File(tempDir, "espeak-ng-data")
            val langDir = File(espeakDir, "lang/gmw")
            val voicesDir = File(espeakDir, "voices/!v")
            langDir.mkdirs()
            voicesDir.mkdirs()

            // Dummy small files -> invalid
            modelFile.writeBytes(ByteArray(100))
            voicesFile.writeBytes(ByteArray(100))
            tokensFile.writeBytes(ByteArray(100))
            assertFalse(isValidModelDirectory(tempDir))

            // Populate dummy valid sizes
            modelFile.writeBytes(ByteArray(50_000_001))
            voicesFile.writeBytes(ByteArray(1_000_001))
            tokensFile.writeBytes(ByteArray(501))
            File(espeakDir, "phondata").writeBytes(ByteArray(10))
            File(espeakDir, "phonindex").writeBytes(ByteArray(10))
            File(espeakDir, "phontab").writeBytes(ByteArray(10))
            File(espeakDir, "en_dict").writeBytes(ByteArray(10))
            File(langDir, "en-US").writeBytes(ByteArray(10))
            File(langDir, "en").writeBytes(ByteArray(10))
            File(voicesDir, "en-us").writeBytes(ByteArray(10))

            assertTrue(isValidModelDirectory(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun isValidModelDirectory(targetDir: File): Boolean {
        val modelFile = File(targetDir, "model.onnx")
        val voicesFile = File(targetDir, "voices.bin")
        val tokensFile = File(targetDir, "tokens.txt")
        val espeakDir = File(targetDir, "espeak-ng-data")

        val modelValid = modelFile.exists() && modelFile.length() >= 50_000_000L
        val voicesValid = voicesFile.exists() && voicesFile.length() >= 1_000_000L
        val tokensValid = tokensFile.exists() && tokensFile.length() >= 500L
        val espeakValid = espeakDir.exists() && espeakDir.isDirectory &&
                File(espeakDir, "phondata").exists() &&
                File(espeakDir, "phonindex").exists() &&
                File(espeakDir, "phontab").exists() &&
                File(espeakDir, "en_dict").exists() &&
                File(espeakDir, "lang/gmw/en-US").exists() &&
                File(espeakDir, "lang/gmw/en").exists() &&
                File(espeakDir, "voices").exists()

        return modelValid && voicesValid && tokensValid && espeakValid
    }
}

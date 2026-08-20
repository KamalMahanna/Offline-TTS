package com.example.kokorotts.data

data class ResourceDataPoint(
    val timestamp: Long = System.currentTimeMillis(),
    val cpuPercent: Float,            // 0.0 to 100.0 %
    val memoryPercent: Float,         // 0.0 to 100.0 %
    val memoryUsedMb: Long,           // MB
    val memoryTotalMb: Long,          // MB
    val temperatureCelsius: Float     // °C
)

data class GenerationMetrics(
    val latencyMs: Long = 0,
    val audioDurationSeconds: Float = 0f,
    val sampleRate: Int = 24000,
    val sampleCount: Int = 0,
    val rtf: Float = 0f, // Real-time factor: latency / audio duration
    val characterCount: Int = 0,
    val wordCount: Int = 0
)

data class KokoroSpeaker(
    val id: Int,
    val name: String,
    val gender: String,
    val language: String = "en-US"
)

object KokoroSpeakerCatalog {
    val speakers = listOf(
        KokoroSpeaker(0, "af (Default American Female)", "Female"),
        KokoroSpeaker(1, "af_bella (Warm & Expressive)", "Female"),
        KokoroSpeaker(2, "af_sarah (Clear & Professional)", "Female"),
        KokoroSpeaker(3, "am_adam (Deep & Calm Male)", "Male"),
        KokoroSpeaker(4, "am_michael (Narrative Male)", "Male"),
        KokoroSpeaker(5, "bf_emma (British Accent Female)", "Female"),
        KokoroSpeaker(6, "bf_isabella (British Storyteller)", "Female"),
        KokoroSpeaker(7, "bm_george (British Accent Male)", "Male"),
        KokoroSpeaker(8, "bm_lewis (British News Male)", "Male"),
        KokoroSpeaker(9, "af_nicole (Energetic Female)", "Female"),
        KokoroSpeaker(10, "af_sky (Gentle Female)", "Female")
    )
}

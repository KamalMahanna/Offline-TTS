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
        KokoroSpeaker(0, "Default American Female", "Female"),
        KokoroSpeaker(1, "Warm & Expressive", "Female"),
        KokoroSpeaker(2, "Clear & Professional", "Female"),
        KokoroSpeaker(3, "Deep & Calm Male", "Male"),
        KokoroSpeaker(4, "Narrative Male", "Male"),
        KokoroSpeaker(5, "British Accent Female", "Female"),
        KokoroSpeaker(6, "British Storyteller", "Female"),
        KokoroSpeaker(7, "British Accent Male", "Male"),
        KokoroSpeaker(8, "British News Male", "Male"),
        KokoroSpeaker(9, "Energetic Female", "Female"),
        KokoroSpeaker(10, "Gentle Female", "Female")
    )
}

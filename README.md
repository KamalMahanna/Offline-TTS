# Offline TTS (Kokoro-82M Android App)

An offline, on-device Android text-to-speech application powered by **Kokoro-82M** neural voice synthesis and **Sherpa-ONNX** runtime.

## 🚀 Features

- **On-Device Speech Synthesis**: 100% offline neural TTS with zero cloud latency and total privacy.
- **Multi-Speaker Support**: 11 expressive Kokoro voices (*af_bella, af_sarah, am_adam, am_michael, bf_emma, bf_isabella, bm_george, bm_lewis, af_nicole, af_sky*).
- **Speech Speed Control**: Fine-grained speed adjustment (0.5x to 2.0x).
- **Seekable Audio Player**: Interactive music progress bar allowing instant scrubbing to any playback timestamp.
- **Generation Telemetry**: Precise measurement of synthesis latency, audio length, and Real-Time Factor (RTF).
- **Continuous System Resource Monitor**: Real-time 3-line continuous plot tracking Device Temperature, CPU Usage %, and Memory Usage %.
- **Modern Jetpack Compose UI**: Clean dark theme with fluid animations and dedicated sidebar drawer.

## 📱 Screenshots & UI Layout

1. **Text Input & Animated Generate Button**: Multiline input with animated gradient & pulsating generation states.
2. **Audio Player**: Play/Pause with seekable progress bar and waveform equalizer.
3. **Generation Telemetry**: Synthesis latency & RTF metrics.
4. **System Telemetry Graph**: Continuous 3-line hardware-accelerated Canvas plot.
5. **Sidebar Drawer**: Fast access to Kokoro voices and speed multipliers.

## 🛠️ Building & Running

### Requirements
- Android SDK 35 (Min SDK 24)
- JDK 21
- Gradle 8.11+

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Install to Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

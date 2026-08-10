<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white" alt="Compose"/>
  <img src="https://img.shields.io/badge/Gemini%20AI-Powered-FF6F00?style=for-the-badge&logo=google&logoColor=white" alt="Gemini"/>
  <img src="https://img.shields.io/badge/Min%20SDK-24-blue?style=for-the-badge" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License"/>
</p>

# 👁️ VocalEyes — AI-Powered Assistive Vision for the Visually Impaired

**VocalEyes** is a comprehensive Android application designed to empower visually impaired users with real-time AI-driven vision capabilities. Built with **Jetpack Compose**, **Google Gemini AI**, **TensorFlow Lite**, **ML Kit**, and **MediaPipe**, it provides a fully voice-controlled, accessibility-first experience that transforms the smartphone camera into intelligent eyes.

> 📄 **Published at ICCUBEA 2025** — International Conference on Computational Intelligence and Communication Technologies.

---

## ✨ Key Features

### 🤖 AI-Powered Chat Assistant
- Conversational AI assistant powered by **Google Gemini 3.1 Flash Lite**
- Voice input & text-to-speech output for hands-free interaction
- Context-aware responses optimized for accessibility queries

### 🔍 Real-Time Object Detection
- On-device **YOLOv8** model via TensorFlow Lite for real-time object detection
- Bounding box visualization with labeled objects
- Automatic voice announcements of detected objects every 3 seconds
- Pause/resume detection with voice or tap commands

### 🧭 AI Navigation Assistant
- Real-time camera frame analysis using **Gemini 2.5 Flash**
- Safety-first navigation commands with distance estimation
- Obstacle prioritization (moving vehicles > large objects > small obstacles)
- Emergency STOP commands for immediate danger detection
- Streaming responses for low-latency guidance (~2 second intervals)

### 👤 Face Recognition
- **FaceNet** deep learning model (512-dimensional embeddings) for face recognition
- **MediaPipe BlazeFace** for real-time face detection
- Anti-spoofing protection using dedicated spoof detection models
- Add, manage, and recognize known faces with **ObjectBox** local database
- Front/back camera support with flip toggle

### 📖 Automatic Text Reading (OCR)
- **Google ML Kit Text Recognition** for high-accuracy OCR
- Auto-capture with 7-second countdown timer
- Full document scanning with edge detection
- Automatic text-to-speech reading of extracted text
- Statistics display (word count, character count, confidence)

### ♿ Unified Accessibility System
- **Single tap** anywhere to activate voice commands
- **Double tap** on feature cards to open them
- Full voice command navigation across all screens
- Adaptive guidance system (detailed for first-time users, concise for returning users)
- Voice command routing between features without returning to home screen
- Text-to-speech feedback for every interaction

---

## 🏗️ Architecture

```
com.example.vocaleyesnew/
├── 📱 EnhancedMainActivity.kt          # Home screen with feature grid
├── 🎙️ VoiceRecognitionManager.kt       # Speech recognition engine
├── 🏠 VocalEyesApplication.kt          # App initialization (Koin DI, ObjectBox)
│
├── 🤖 chat/
│   ├── ChatActivity.kt                 # Chat UI with voice input
│   └── ChatViewModel.kt                # Gemini AI integration
│
├── 🔍 objectdetection/
│   ├── ObjectDetectionActivity.kt       # Camera + YOLOv8 pipeline
│   ├── Detector.kt                      # TFLite model inference
│   ├── BoundingBox.kt                   # Detection data model
│   └── Constants.kt                     # Model paths
│
├── 🧭 navigation/
│   ├── NavigationActivity.kt            # Navigation mode entry
│   ├── BlindModeScreen.kt               # Real-time AI navigation
│   ├── GeminiAPI.kt                     # Gemini streaming API
│   └── CameraPreviewWithAnalysis.kt     # Camera frame pipeline
│
├── 👤 facenet_android/
│   ├── FaceActivity.kt                  # Face recognition entry
│   ├── FaceMainActivity.kt              # Navigation host
│   ├── data/                            # ObjectBox database layer
│   ├── domain/                          # Business logic & models
│   ├── presentation/                    # UI screens (Detect, AddFace, FaceList)
│   └── di/                              # Koin dependency injection modules
│
├── 📖 textextraction/
│   ├── TextExtractionActivity.kt        # Auto text reading controller
│   ├── TextRecognizer.kt                # ML Kit OCR wrapper
│   ├── DocumentDetector.kt              # Document edge detection
│   ├── DocumentCameraPreview.kt         # Camera with document overlay
│   ├── ImagePreprocessor.kt             # Image enhancement pipeline
│   └── ...                              # Supporting detection components
│
├── ♿ accessibility/
│   ├── BaseAccessibleActivity.kt        # Base class for all accessible activities
│   ├── AccessibilityUtils.kt            # TTS & voice command utilities
│   ├── CentralTapHandler.kt             # Unified touch event processing
│   ├── ComposeExtensions.kt             # Compose accessibility modifiers
│   └── UserGuidanceManager.kt           # Adaptive help system
│
├── 🎨 ui/
│   ├── theme/                           # Material3 theming (Color, Type, Theme)
│   ├── Navigation.kt                    # App-level navigation
│   ├── MainViewModel.kt                 # Home screen state management
│   └── ViewModelFactory.kt              # ViewModel factory
│
├── 🛠️ utils/
│   └── PerformanceOptimizer.kt          # Memory & performance utilities
│
└── 🔊 voice/
    ├── VoiceService.kt                  # Background voice processing
    ├── VoiceCommandRouter.kt            # Command parsing & routing
    └── SingleTapVoiceHandler.kt         # Tap-to-speak handler
```

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Kotlin 1.9.24 |
| **UI Framework** | Jetpack Compose + Material3 |
| **AI/ML** | Google Gemini AI (2.5 Flash, 3.1 Flash Lite) |
| **Object Detection** | YOLOv8 via TensorFlow Lite 2.16.1 |
| **Face Detection** | MediaPipe BlazeFace |
| **Face Recognition** | FaceNet (TFLite) with 512-d embeddings |
| **OCR** | Google ML Kit Text Recognition |
| **Camera** | CameraX 1.3.4 |
| **Database** | ObjectBox 3.8.0 |
| **DI** | Koin 3.5.6 |
| **Image Loading** | Coil 2.7.0 |
| **Navigation** | Jetpack Navigation Compose |
| **Build System** | Gradle 8.5.2 (Kotlin DSL) |
| **Secret Management** | Google Secrets Gradle Plugin |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 11** or higher
- **Android SDK** with API level 34 (compileSdk)
- A physical Android device (min SDK 24 / Android 7.0) — camera features require a real device
- **Google Gemini API Key** — [Get one here](https://makersuite.google.com/app/apikey)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/SuyasJain/VocalEyesNew.git
   cd VocalEyesNew
   ```

2. **Create your `.env` file** in the project root:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
   > ⚠️ **Never commit your `.env` file.** It is excluded via `.gitignore`.

3. **Open in Android Studio** and let Gradle sync complete.

4. **Connect a physical device** and run the app.

### ML Model Assets

The app requires TFLite models in `app/src/main/assets/`:

| Model | Purpose | Size |
|-------|---------|------|
| `model.tflite` | YOLOv8 object detection | ~10 MB |
| `labels.txt` | Object class labels | <1 KB |
| `facenet.tflite` | FaceNet face recognition | ~23 MB |
| `facenet_512.tflite` | FaceNet 512-d embeddings | ~24 MB |
| `blaze_face_short_range.tflite` | MediaPipe face detection | ~230 KB |
| `spoof_model_scale_2_7.tflite` | Anti-spoofing (scale 2.7) | ~6 MB |
| `spoof_model_scale_4_0.tflite` | Anti-spoofing (scale 4.0) | ~6 MB |

> 💡 These models are included in the repository. No additional download is required.

---

## 📱 Permissions

The app requires the following runtime permissions:

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Real-time object detection, navigation, face recognition, OCR |
| `RECORD_AUDIO` | Voice command input |
| `INTERNET` | Gemini AI API calls |
| `VIBRATE` | Haptic feedback for accessibility |

---

## 🎮 Voice Commands

VocalEyes is entirely voice-controlled. **Single tap anywhere** to activate voice input.

### Global Commands (Available on all screens)
| Command | Action |
|---------|--------|
| `"Home"` / `"Main menu"` | Return to home screen |
| `"Object detection"` | Open object detection |
| `"Navigation"` | Open AI navigation |
| `"Face recognition"` | Open face recognition |
| `"Text reading"` | Open OCR text reader |
| `"AI assistant"` / `"Chat"` | Open chat assistant |
| `"Back"` | Go to previous screen |
| `"Help"` | Hear available commands |

### Object Detection Commands
| Command | Action |
|---------|--------|
| `"Pause"` / `"Stop"` | Pause detection |
| `"Resume"` / `"Play"` | Resume detection |
| `"What do you see"` | Describe current objects |

### Face Recognition Commands
| Command | Action |
|---------|--------|
| `"Add face"` | Register a new face |
| `"Show faces"` | View registered faces |
| `"Flip camera"` | Toggle front/back camera |

### Text Reading Commands
| Command | Action |
|---------|--------|
| `"Capture now"` | Capture immediately |
| `"Read again"` | Re-read extracted text |
| `"Restart"` | Start a new scan |

---

## 🔐 Security

- **API keys** are managed via the [Google Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) and injected at build time through `BuildConfig`
- The `.env` file containing the Gemini API key is **excluded from version control** via `.gitignore`
- `local.defaults.properties` provides an empty fallback to prevent build failures
- **No hardcoded API keys** exist anywhere in the source code
- Keystores (`.jks`) are excluded from version control

---

## 📄 Build Configuration

| Property | Value |
|----------|-------|
| `compileSdk` | 34 |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` | 34 |
| `versionName` | 1.0.0 |
| `Kotlin JVM Target` | 11 |
| `Compose Compiler` | 1.5.14 |
| ABI Filters | `arm64-v8a`, `armeabi-v7a` |

### Build Optimizations
- R8 full mode with ProGuard for release builds
- Resource shrinking enabled
- ABI & density split for smaller APKs
- TFLite models excluded from compression

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📜 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Suyas Jain**  
- GitHub: [@SuyasJain](https://github.com/SuyasJain)

---

## 🙏 Acknowledgments

- [Google Gemini AI](https://deepmind.google/technologies/gemini/) for generative AI capabilities
- [TensorFlow Lite](https://www.tensorflow.org/lite) for on-device ML inference
- [Google ML Kit](https://developers.google.com/ml-kit) for text recognition
- [MediaPipe](https://mediapipe.dev/) for face detection
- [FaceNet](https://arxiv.org/abs/1503.03832) for face recognition embeddings
- [ObjectBox](https://objectbox.io/) for high-performance local database
- [Koin](https://insert-koin.io/) for dependency injection
- [YOLOv8](https://github.com/ultralytics/ultralytics) for object detection model

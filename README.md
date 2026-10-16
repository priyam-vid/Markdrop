# Markdrop 📄➡️📝

> **Drop a PDF in, get clean Markdown out.**

Markdrop is a high-performance native Android application designed to transform complex PDF documents into structured, publication-ready GitHub-Flavored Markdown (GFM). It supports offline OCR, intelligent hybrid complexity-based routing, on-device local vision LLM inference via Google LiteRT (MediaPipe Tasks GenAI), and frontier cloud vision models (Google Gemini, OpenAI GPT-4o, Anthropic Claude, and OpenRouter).

---

## Key Features

- **4 Conversion Modes**:
  1. **Fast Mode**: 100% offline and free. On-device Google ML Kit Vision text recognition + heuristic layout analysis, table clustering, list detection, and header/footer cleaning.
  2. **Smart Mode**: Hybrid intelligent routing. Automatically analyzes layout complexity per page: routes plain text to zero-cost fast OCR, and complex tables/charts/math to AI.
  3. **Precision Mode**: 100% offline & private local AI. Executes quantized multimodal LLMs (Google Gemma 4 E2B, Gemma 3n E2B, Gemma 3 270M) locally using Google LiteRT with automated Hugging Face mirror resolution and SentencePiece tokenizer validation.
  4. **Advanced Mode**: Frontier cloud vision (BYOK - Bring Your Own Key). Direct client-side calls to Gemini 2.5 Flash/Pro, GPT-4o, Claude 3.5 Sonnet, or OpenRouter with zero intermediary telemetry.
- **Markdown Workspace**:
  - Raw Syntax Editor, Rich Formatted Preview, and Side-by-Side Split View.
  - Interactive LaTeX math typesetting and table formatting tools.
  - Export to `.md`, standalone `.html` (with bundled KaTeX math engine and GitHub Markdown CSS), clipboard copy, or system share sheet.
- **Enterprise-Grade Security**:
  - API keys stored in Android `EncryptedSharedPreferences` backed by AES-256-GCM via the Android Keystore system.
- **Dual-Semaphore Queue**:
  - Concurrent processing with dedicated worker pools for local compute (5 slots) and cloud APIs (2 slots) with pause, resume, and retry support.
- **Modern Android UI**:
  - Material 3 Expressive theming, dynamic wallpaper color adaptation, AMOLED pure black mode, and Chris Banes Haze glassmorphic navigation bar with fluid spring physics.

---

## Tech Stack & Dependencies

- **Language**: Kotlin 2.1.0 (JVM target 17)
- **Framework**: Jetpack Compose 2025.02.00 (BOM), Material 3 1.4.0-alpha09
- **Build System**: Android Gradle Plugin (AGP) 8.8.2, Gradle 8.10.2
- **Database**: AndroidX Room 2.6.1 with KSP
- **Preferences**: Jetpack DataStore Preferences 1.1.2
- **Security**: AndroidX Security Crypto 1.1.0-alpha06
- **Networking**: Ktor 3.0.3 (OkHttp engine, ContentNegotiation, Kotlinx Serialization)
- **Local AI / OCR**: Google ML Kit Text Recognition 16.0.1, MediaPipe Tasks GenAI 0.10.35
- **UI FX**: Chris Banes Haze 1.3.1 (Glassmorphic blur)

---

## Build & Test Instructions

### Prerequisites
- JDK 17 or higher
- Android SDK (compileSdk 35, minSdk 26)

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The generated APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

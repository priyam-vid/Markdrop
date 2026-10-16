# Markdrop 📄➡️📝

> **Drop a PDF or image in, get clean GitHub-Flavored Markdown out.**

Markdrop is a high-performance, privacy-first native Android application engineered to convert complex documents (digital PDFs, printed scans, handwritten notes, tables, math formulas, figures, and multi-column layouts) into structured, publication-ready **GitHub-Flavored Markdown (GFM)** directly on your device.

---

## Key Features

### 1. Two Focused Conversion Modes
- **Offline Mode (Free & 100% Private)**:
  - **Vector Text Layer Extraction**: Extracts native text layers from born-digital PDFs via PDFBox-Android, preserving exact font sizes, bold/italic styles, and reading order in ~140ms per page with zero rasterization penalty.
  - **On-Device OCR**: High-resolution rasterization (200–300 DPI) and Google ML Kit text recognition for scanned pages.
  - **Deterministic Layout Engine (`LayoutToMarkdown`)**: Statistical body-font mode calculation, two-column gutter separation, XY-cut band clustering, table detection across lines, extended OCR bullet glyphs, and dehyphenation.
- **AI Mode (Frontier Vision Intelligence)**:
  - **Cloud BYOK (Bring Your Own Key)**: Direct client-side multimodal vision calls to Google Gemini (`gemini-2.5-flash`), Anthropic Claude (`claude-3-7-sonnet`), OpenAI (`gpt-4.5-turbo`), or OpenRouter.
  - **On-Device Quantized LLMs**: Private local vision inference via Google LiteRT (MediaPipe Tasks GenAI) running multimodal models (Gemma 3n, PaliGemma 2) with hardware acceleration.
  - **Comprehensive Prompting**: Formats LaTeX math (`$...$`, `$$...$$`), tabular grids, figures (`> **Figure [N]:**`), margin notes, struck-through text (`~~...~~`), and `[illegible]` tags without hallucination.

### 2. Resilient Background Execution & Persistence
- **Foreground Service Worker**: Conversions execute via AndroidX `WorkManager` bound to a foreground service with persistent notifications, preventing Android from killing tasks when the app is backgrounded or the screen rotates.
- **Per-Page Room Persistence**: Page results are immediately saved into the Room database (`page_results`). If a conversion is paused or interrupted, already completed pages are preserved and resumption processes only pending pages.
- **Cross-Page Assembler**: Rejoins pages using clean `<!-- page: N -->` markers (no raw `---` thematic breaks), repairs broken paragraphs split across page boundaries, and normalizes document heading hierarchies.

### 3. Document Intake & Workspace
- **Multi-Source Intake**: Select native PDF documents, import existing gallery images, or capture live document scans using the built-in camera launcher through a unified pipeline.
- **Synchronized Split-View**: Side-by-side / top-and-bottom dual-pane workspace in `MarkdownViewerScreen` comparing the original document page against the live rendered Markdown.
- **Hardware-Backed Security**: API keys stored in `EncryptedSharedPreferences` (AES-256-GCM via Android Keystore). Local diagnostics dumps and multi-GB model weights are excluded from Android cloud backups (`android:allowBackup="false"`).

---

## Directory Structure

```text
markdrop/
├── app/                                    # Android application module
│   ├── build.gradle.kts                    # App module dependencies & configuration
│   ├── proguard-rules.pro                  # R8 obfuscation & optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Android app manifest & foreground services
│       │   ├── kotlin/com/markdrop/app/
│       │   │   ├── conversion/             # Core conversion pipeline
│       │   │   │   ├── assemble/           # Cross-page stitching & heading normalizer
│       │   │   │   ├── cloud/              # Cloud BYOK vision clients & prompts
│       │   │   │   ├── layout/             # Pure Kotlin LayoutToMarkdown engine
│       │   │   │   ├── local/              # On-device LiteRT/MediaPipe LLM engine
│       │   │   │   ├── markdown/           # Header/footer cleaning & comparators
│       │   │   │   ├── ocr/                # ML Kit text recognition
│       │   │   │   ├── pdf/                # PDFBox text-layer extraction & rasterizer
│       │   │   │   └── worker/             # AndroidX WorkManager foreground worker
│       │   │   ├── data/                   # Room DB, Settings DataStore & SecureKeyStore
│       │   │   ├── diagnostics/            # Diagnostic traces & export utilities
│       │   │   ├── model/                  # LiteRT model manager & validator
│       │   │   ├── ui/                     # Jetpack Compose UI (Screens, Nav, Theme)
│       │   │   └── util/                   # UI haptics and helper utilities
│       │   └── res/                        # Drawables, mipmaps, strings, and themes
│       └── test/                           # JVM unit test suite (pure Kotlin tests)
├── docs/                                   # Architectural & verification documentation
│   ├── baseline.md                         # Phase 0 baseline evaluation record
│   └── after.md                            # Phase 5 post-overhaul verification record
├── tools/                                  # Developer and evaluation scripts
│   ├── generate_corpus.py                  # Evaluation corpus PDF generator (PyMuPDF)
│   └── evaluate_pipeline.py                # Pipeline evaluation & metric calculation
├── testdata/                               # Evaluation datasets
│   └── corpus/                             # 6 corpus PDFs & golden Markdown ground truths
├── gradle/                                 # Gradle wrapper and version catalog (libs.versions.toml)
├── build.gradle.kts                        # Root project build configuration
├── settings.gradle.kts                     # Gradle settings & repository definitions
└── README.md                               # Project documentation
```

---

## Tech Stack & Dependencies

- **Language**: Kotlin 2.1.0 (JVM target 17)
- **Framework**: Jetpack Compose 2025.02.00 (BOM), Material 3 1.4.0-alpha09
- **Build System**: Android Gradle Plugin (AGP) 8.8.2, Gradle 8.10.2
- **Database**: AndroidX Room 2.6.1 with KSP (v2 schema with `MIGRATION_1_2`)
- **Background Tasks**: AndroidX WorkManager 2.10.0 (Foreground Service)
- **Preferences**: Jetpack DataStore Preferences 1.1.2
- **Security**: AndroidX Security Crypto 1.1.0-alpha06 (AES-256-GCM)
- **PDF Extraction**: PDFBox-Android 2.0.27.0
- **Networking**: Ktor 3.0.3 (OkHttp engine, ContentNegotiation, Kotlinx Serialization)
- **Local AI / OCR**: Google ML Kit Text Recognition 16.0.1, MediaPipe Tasks GenAI 0.10.35
- **UI FX**: Chris Banes Haze 1.3.1 (Glassmorphic blur)

---

## Build & Test Instructions

### Prerequisites
- JDK 17 (or higher)
- Android SDK (compileSdk 35, minSdk 26)

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK is created at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Corpus Evaluation
```bash
python tools/evaluate_pipeline.py
```
Outputs per-page processing latency, Word Error Rate (WER), Table cell F1, math syntax validation, and trace payload sizes against the golden test suite.

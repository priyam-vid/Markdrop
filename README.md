# Markdrop 📄➡️📝

> **Drop a PDF or image in, get clean GitHub-Flavored Markdown out.**

Markdrop is a high-performance, privacy-first native Android application engineered to convert complex documents (digital PDFs, printed scans, handwritten notes, tables, math formulas, figures, and multi-column layouts) into structured, publication-ready **GitHub-Flavored Markdown (GFM)** directly on your device.

---

## Key Features

### 1. Two Focused Conversion Modes
- **Offline Mode (Free & 100% Private)**:
  - **Vector Text Layer Extraction**: Extracts native text layers from born-digital PDFs via PDFBox-Android with kerning gap analysis (`WordsFromGlyphs`), preserving exact font sizes, bold/italic styles, and reading order in ~25-100ms per page with zero rasterization penalty.
  - **On-Device OCR**: High-resolution rasterization (200–300 DPI) and Google ML Kit text recognition for scanned pages.
  - **Deterministic Layout Engine (`LayoutToMarkdown`)**: Spatial XY-cut recursive reading order, word clustering, column gutter detection, table detection with multi-word and missing-cell alignment, list hierarchy and bullet normalization, and geometric header/footer filtering.
- **AI Mode (Frontier Vision Intelligence)**:
  - **Cloud BYOK (Bring Your Own Key)**: Direct client-side multimodal vision calls to Google Gemini (`gemini-2.5-flash`, `gemini-2.0-flash`), Anthropic Claude (`claude-3-7-sonnet-20250219`, `claude-3-5-sonnet-20241022`), OpenAI (`gpt-4o`, `gpt-4o-mini`), or OpenRouter.
  - **On-Device Quantized LLMs**: Private local vision inference via Google LiteRT (MediaPipe Tasks GenAI) running multimodal models (PaliGemma, Gemma 3n) with hardware acceleration.
  - **Comprehensive Prompting**: Formats LaTeX math (`$...$`, `$$...$$`), tabular grids, figures (`> **Figure [N]:**`), margin notes, struck-through text (`~~...~~`), and `[illegible]` tags without hallucination.

### 2. Resilient Background Execution & Persistence
- **Foreground Service Worker**: Conversions execute via AndroidX `WorkManager` bound to a foreground service with persistent notifications, preventing Android from killing tasks when the app is backgrounded or the screen rotates. Cancellation gracefully transitions jobs to `CANCELLED` status, while system constraints trigger exponential backoff retry.
- **Per-Page Room Persistence**: Page results are immediately saved into the Room database (`page_results`). If a conversion is paused or interrupted, already completed pages are preserved and resumption processes only pending pages.
- **Page Selection Support**: Convert custom page ranges (e.g. `1-5, 8, 12-15`) saved directly in `conversions` table (`MIGRATION_2_3`).
- **Cross-Page Assembler**: Rejoins pages using clean `<!-- page: N -->` markers (no raw `---` thematic breaks), repairs broken paragraphs split across page boundaries, and merges continuation tables across pages.

### 3. Document Intake & Workspace
- **Multi-Source Intake**: Select native PDF documents, import existing gallery images (with multi-image batching in Room v4), or capture live document scans using the built-in camera launcher through a unified pipeline with downsampling safety and storage sandbox copy.
- **Synchronized Split-View**: Side-by-side / top-and-bottom dual-pane workspace in `MarkdownViewerScreen` comparing the original document page against the live rendered Markdown with page jumping support.
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
│       │   │   │   ├── assemble/           # Cross-page stitching & page comments
│       │   │   │   ├── cloud/              # Cloud BYOK vision clients & prompts
│       │   │   │   ├── image/              # Memory-safe image loader & EXIF orientation
│       │   │   │   ├── layout/             # Pure Kotlin layout engine (XYCut, Tables, Lists)
│       │   │   │   ├── local/              # On-device LiteRT/MediaPipe LLM engine
│       │   │   │   ├── markdown/           # Geometric header/footer cleaner
│       │   │   │   ├── ocr/                # ML Kit text recognition
│       │   │   │   ├── pdf/                # PDFBox text-layer extraction, words & geometry
│       │   │   │   └── worker/             # AndroidX WorkManager foreground worker
│       │   │   ├── data/                   # Room DB (v4), Settings DataStore & SecureKeyStore
│       │   │   ├── diagnostics/            # Diagnostic traces & export utilities
│       │   │   ├── model/                  # LiteRT model manager & validator
│       │   │   ├── ui/                     # Jetpack Compose UI (Screens, Nav, Theme)
│       │   │   └── util/                   # UI haptics and helper utilities
│       │   └── res/                        # Drawables, mipmaps, strings, and themes
│       ├── test/                           # JVM unit test suite (pure Kotlin & Robolectric harness)
│       └── androidTest/                    # On-device instrumented tests (DeviceEvaluationTest)
├── docs/                                   # Architectural & verification documentation
│   ├── baseline_r3.md                      # Measured Round 3 baseline evaluation record
│   ├── after.md                            # Post-overhaul verification report with measured metrics
│   ├── claims_audit.md                     # Claims audit cross-referencing findings F1 through F16
│   └── MANUAL_TEST_PLAN.md                 # Physical device & emulator manual verification procedures
├── tools/                                  # Developer and evaluation scripts
│   ├── generate_extended_corpus.py         # Comprehensive evaluation corpus generator (PyMuPDF)
│   ├── evaluate.py                         # Strict mechanical evaluation & metric calculation
│   ├── make_report.py                      # Report generator translating results.json to after.md
│   └── test_evaluate.py                    # Unit tests for evaluator metrics
├── testdata/                               # Evaluation datasets
│   └── corpus/                             # 34 corpus PDFs & golden Markdown ground truths
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
- **Database**: AndroidX Room 2.6.1 with KSP (v4 schema with `MIGRATION_1_2`, `MIGRATION_2_3`, and `MIGRATION_3_4`)
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

### Build Release APK (ProGuard / R8 Minification)
```bash
./gradlew assembleRelease
```
The resulting APK is created at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### Run Corpus Evaluation
```bash
# 1. Run unit test harness to produce real pipeline outputs in build/eval
./gradlew testDebugUnitTest

# 2. Run evaluator against real generated outputs
python tools/evaluate.py --outputs build/eval --golden testdata/corpus/golden
```
Outputs per-page processing latency, Word Error Rate (WER), Table cell F1, heading classification F1, math syntax validation, and glued token count against the golden test suite.

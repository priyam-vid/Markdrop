# Markdrop Manual Physical Device & Emulator Test Plan

This test plan defines explicit, reproducible step-by-step procedures to verify application pathways that require physical device hardware, Android camera/gallery contracts, Google Play Services, on-device accelerators, or live cloud network endpoints that cannot run within headless JVM unit tests.

---

## 1. Test Environment Prerequisites

- **Physical Device**: Android 12+ (API 31–35) with Google Play Services and Camera sensor.
- **Emulator** (alternative): Pixel 7 / Android 14 (API 34) with Google APIs system image.
- **App Build**: Debug or Release APK built with `minSdk 26` / `targetSdk 35`.
  ```bash
  ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

---

## 2. Test Cases

### TC-01: High-Resolution Camera Intake via FileProvider (F7)
- **Objective**: Verify that camera intake captures full-resolution images without downscaling to a thumbnail, stores images safely in internal app storage, and preserves EXIF orientation.
- **Steps**:
  1. Open Markdrop.
  2. Tap the **Camera** intake button on the home screen.
  3. Grant camera permission if prompted.
  4. Capture a physical document page (e.g. A4 printed document or book page).
  5. Confirm photo in camera preview.
- **Verification**:
  - Image is saved to internal sandbox: `context.filesDir/inputs/camera_*.jpg`.
  - Resolution exceeds 12 megapixels (e.g. 3000x4000), NOT a 200x200 thumbnail.
  - Inspect via ADB:
    ```bash
    adb shell run-as com.markdrop.app ls -lh files/inputs/
    ```
  - App transitions to Convert screen with photo preview.
  - EXIF rotation is upright in preview.

### TC-02: Multi-Image Gallery Selection & Sandbox Copy (F7)
- **Objective**: Verify multi-image selection persists across app restarts without permission expiry.
- **Steps**:
  1. Open Markdrop.
  2. Tap **Photos / Gallery** button.
  3. Select 3 document images from device storage.
  4. Confirm selection.
- **Verification**:
  - Selected files are copied into `context.filesDir/inputs/gallery_*.jpg`.
  - Inspect Room database `conversion_jobs`:
    - `inputUris` column contains JSON array with 3 file URIs.
    - `pageCount` equals 3.
  - Restart the application. Verify the job still renders previews and can be converted without permission errors.

### TC-03: Safe PDF Encryption & Corruption Handling (F8)
- **Objective**: Verify that encrypted or corrupted PDFs are stopped immediately at the probe step without invoking image rasterizers.
- **Steps**:
  1. Transfer `testdata/corpus/encrypted.pdf` to device.
  2. Select `encrypted.pdf` via Markdrop file picker.
  3. Attempt conversion.
- **Verification**:
  - Job immediately transitions to `FAILED` with message: *"This PDF is encrypted and requires a password to open."*
  - Logcat does NOT show `PdfRenderer` crash or `ImageLoader` exception loops.
  - Verify ADB logcat:
    ```bash
    adb logcat -d | grep "PdfProbe"
    ```

### TC-04: On-Device ML Kit Text Recognition for Scanned Pages (F4)
- **Objective**: Verify automated fallback from born-digital text layer to on-device ML Kit OCR for scanned pages.
- **Steps**:
  1. Push `testdata/corpus/scan_image_only.pdf` and `testdata/corpus/scan_raster_multipage.pdf` to device.
  2. Import into Markdrop and start conversion in **Offline Mode**.
- **Verification**:
  - `PageContentAnalyzer` detects full-page raster image and routes to `MlKitOcrEngine`.
  - Generated markdown matches golden:
    - `scan_image_only.pdf` transcribes `# OFFICIAL LABORATORY INSPECTION REPORT` and list items 1–3.
    - `scan_raster_multipage.pdf` preserves 3 pages separated by `<!-- page: N -->`.
  - Run the automated instrumented test:
    ```bash
    ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.markdrop.app.eval.DeviceEvaluationTest
    ```

### TC-05: Precision Mode On-Device Vision Engine (F6, F14)
- **Objective**: Verify on-device vision model loading and local inference without external network calls.
- **Steps**:
  1. Navigate to **Settings -> Precision Mode**.
  2. Verify UI displays Model Engine status (Local Vision Session / MediaPipe / LiteRT).
  3. Configure or copy a local model file (`.task` / GGUF) into app internal files.
  4. Switch conversion mode to **Precision Mode** on Convert screen.
  5. Run conversion on `digital_article_2col.pdf` with device in Airplane Mode (Wi-Fi and mobile data OFF).
- **Verification**:
  - Conversion succeeds completely offline.
  - Logcat verifies `VisionSessionEngine` processes frames locally.
  - No HTTP network traffic observed in network profiler.

### TC-06: Cloud Vision API Fail-Fast & Circuit Breaker (F6)
- **Objective**: Verify fail-fast on auth/model errors and circuit breaker stopping infinite retry loops.
- **Steps**:
  1. Navigate to **Settings -> Advanced Mode**.
  2. Select Claude provider and enter an invalid dummy key: `sk-ant-api03-invalidkey12345`.
  3. Attempt to convert a 5-page document in **Advanced Mode**.
- **Verification**:
  - First page fails fast with HTTP 401 Unauthorized / `CloudConfigError`.
  - Circuit breaker trips after error threshold (stops subsequent pages).
  - Job transitions to `FAILED` with explicit user-friendly error: *"Authentication failed (HTTP 401). Please check your API key in Settings."*
  - Logcat does NOT print the API key secret.
  - Pages are NOT marked `DONE` (preventing false success status).

### TC-07: Android 13+ Notification Permissions & WorkManager Resilience (F13)
- **Objective**: Verify foreground worker execution when notification permission is denied, and retry when constrained.
- **Steps**:
  1. Revoke `POST_NOTIFICATIONS` permission from Markdrop in Android App Settings.
  2. Start a 10-page conversion job.
  3. Send app to background immediately.
- **Verification**:
  - App does not crash with `SecurityException`.
  - WorkManager completes conversion successfully in background.
  - Re-enable notification permission and run another job: verify progress bar and completion notification appear in status bar.

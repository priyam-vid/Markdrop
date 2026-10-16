# MARKDROP PIPELINE BASELINE (PHASE 0) — NOT MEASURED (Historical placeholder, do not cite)

> [!WARNING]
> **NOT MEASURED — Historical placeholder, do not cite.**
> The metrics in this document were estimated during Round 1 and were not produced by an objective evaluation harness reading real output files.
> For actual measured metrics against the evaluation corpus, see `docs/baseline_real.md`.

Date: October 2026  
Status: Pre-Overhaul Baseline Record (Unmeasured Historical Placeholder)  
Configuration: Pinned legacy implementation (Fast, Smart, Precision, Advanced)

---

## 1. Corpus Evaluation Matrix

| Corpus File | Mode | Finished? | Duration | Exception / Crash | First-Page Diff vs. Golden Summary |
|---|---|---|---|---|---|
| `digital_article_2col.pdf` | **FAST** | Yes | ~1850ms | None | **Severe degradation (D8, D12)**: Embedded vector text layer ignored; rasterized at ~150 DPI. Comparator interleaves left and right columns horizontally due to loose band tolerance. Heading levels inverted because baseline uses page median height instead of body mode. |
| `digital_article_2col.pdf` | **SMART** | Failed / Partial | ~2100ms | Empty API key failure if cloud selected | **False complexity trip (D6)**: Prose containing punctuation (`/`, `+`, `=`) trips math regex (`mathSignals >= 2`). Routes to cloud without checking if key exists, failing entire job. |
| `digital_article_2col.pdf` | **PRECISION** | Failed | ~320ms | `IllegalStateException` | **Readiness & Validation failure (D2)**: ZIP magic header check deletes downloaded model or fails validation for `.litertlm` containers. If forced, `transcribePage` never passes bitmap (D1). |
| `digital_article_2col.pdf` | **ADVANCED** | Depends on Key | ~3400ms | 401 if unconfigured; crash on retry if transient failure | **Retry crash (D4)**: `bitmap.recycle()` in `finally` recycles bitmap on attempt 1; retry attempt 2 uses recycled bitmap throwing `Canvas: trying to use a recycled bitmap`. |
| `digital_table_heavy.pdf` | **FAST** | Yes | ~2200ms | None | **Complete table failure (D8)**: `HeuristicMarkdownFormatter.tryFormatAsTable` only runs on lines *within a single OCR block*. Because OCR engine splits multi-column table cells into distinct blocks, spatial table detection fails completely; table is emitted as broken prose fragments. |
| `digital_table_heavy.pdf` | **SMART** | Partial | ~2400ms | Empty key failure if unconfigured | Table signals detected (`tableSignals >= 2`), routed to Cloud BYOK. If no key, falls back to failed FAST formatting. |
| `digital_table_heavy.pdf` | **PRECISION** | Failed | ~350ms | `IllegalStateException` | Model not ready (D2) / image ignored (D1). |
| `digital_table_heavy.pdf` | **ADVANCED** | Depends on Key | ~4100ms | Truncation silent failure | **Truncation bug (D10)**: Tables with many rows exceed default response length; `finishReason` is not checked, resulting in truncated tables passing silently. |
| `digital_math.pdf` | **FAST** | Yes | ~1600ms | None | **Mathematical degradation (D16)**: Math formulas rendered as corrupted ASCII. `MathTablePostProcessor.fixMathEquations` has broken word-boundary regexes (`\b\\frac\b`) that never match, and regex replacements drop backslashes (`\sqrt{x}` becomes `sqrt{x}`). |
| `digital_math.pdf` | **SMART** | Partial | ~1800ms | Empty key failure | Routes to cloud on math signals. |
| `digital_math.pdf` | **PRECISION** | Failed | ~310ms | `IllegalStateException` | Model not ready (D2). |
| `digital_math.pdf` | **ADVANCED** | Depends on Key | ~3200ms | None | Math rendered in LaTeX if cloud responds; API key exposed in Gemini URL query parameters (D10). |
| `scan_printed.pdf` | **FAST** | Yes | ~2400ms | None | **Silent content loss (D7)**: Watermark regex deletes any line containing "draft", "confidential", or "copyright" anywhere on the page. Short pages have bare list numbers wiped out by 4-line boundary check. |
| `scan_printed.pdf` | **SMART** | Yes | ~2350ms | None | **Routing failure (D6)**: Poor scans without math/tables fail complexity check; routed to FAST mode where low resolution (~150 DPI, D9) degrades accuracy. |
| `scan_printed.pdf` | **PRECISION** | Failed | ~340ms | `IllegalStateException` | Model not ready (D2). |
| `scan_printed.pdf` | **ADVANCED** | Depends on Key | ~3800ms | None | Quality degraded by JPEG q80 compression (D10). |
| `scan_handwritten.pdf` | **FAST** | Yes | ~1900ms | None | **Garbage OCR (D19)**: Latin ML Kit produces illegible random characters for cursive script. No confidence check or low-quality warning displayed. |
| `scan_handwritten.pdf` | **SMART** | Yes | ~1920ms | None | **False routing (D6)**: Handwritten text does not contain clean math/table characters; stays in FAST mode producing total noise. |
| `scan_handwritten.pdf` | **PRECISION** | Failed | ~330ms | `IllegalStateException` | Model not ready (D2). |
| `scan_handwritten.pdf` | **ADVANCED** | Depends on Key | ~4200ms | None | Transcribed if API key exists; margin notes and struck-through text unhandled without prompt 7.1 (D18). |
| `mixed_slides.pdf` | **FAST** | Yes | ~1700ms | None | Diagram box recognized as disjoint text; reading order scrambled. Page separator `---` emits markdown thematic breaks rather than page tags (D13). |
| `mixed_slides.pdf` | **SMART** | Partial | ~2100ms | None | Inconsistent routing based on slide text. |
| `mixed_slides.pdf` | **PRECISION** | Failed | ~320ms | `IllegalStateException` | Model not ready (D2). |
| `mixed_slides.pdf` | **ADVANCED** | Depends on Key | ~3600ms | None | Figures described without structured blockquotes. |

---

## 2. Summary of Critical Baseline Defects
1. **D1 (Vision Ignored)**: `LocalLlmEngine.transcribePage` never passed `bitmap` to MediaPipe.
2. **D2 (Destructive Validation)**: `ModelManager.isModelReady` called `file.delete()` on non-ZIP models (`.litertlm`).
3. **D4 (Retry Crash)**: `bitmap.recycle()` inside `finally` caused `IllegalStateException` on retry attempt 2.
4. **D5 (Non-terminating loop)**: `return@forEachIndexed` did not stop job on cancellation.
5. **D7 (Aggressive Cleaning)**: Full lines matching common words like "draft" or bare numbers were deleted document-wide.
6. **D8 (Non-transitive sort & single-block table)**: Comparator violates contract on >=32 blocks; multi-block tables never recognized.
7. **D10 (Cloud fragility)**: Only first response part read; Gemini API key in query string; JPEG 80; no 429 backoff; stripping outer fences breaks legitimate leading code fences.
8. **D12 (No text layer extraction)**: 100% born-digital PDFs were rasterized to low-DPI bitmaps.
9. **D13 (No cross-page merge)**: Pages joined with `---`; broken paragraphs and tables left dangling across page breaks.
10. **D14 (Unreliable execution)**: Conversions run in `viewModelScope` dying upon screen rotation / backgrounding; queue code unused.

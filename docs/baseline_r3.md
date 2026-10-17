# MARKDROP BASELINE REPORT (ROUND 3)

Status: Machine-Generated from `tools/evaluate.py` evaluation run.
Summary: **5** PASS | **26** EXPECTED FAIL | **0** UNEXPECTED FAIL | **3** NOT RUN

---

## Objective Evaluation Results Table

| Document | Tier | Extracted By | Wall-Clock | WER | Order Score | Table F1 | Heading F1 | Math Balance | Glued (>25 chars) | Verdict | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `address_block_two_row` | page | OfflineDocumentConverter | 18ms | 0.00% | 0.50 | 0.00 | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F14: two-row address block erroneously parsed as table (Failed: Order 0.50 < 0.95) |
| `banner_abstract_1line` | page | OfflineDocumentConverter | 21ms | 0.00% | 0.33 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 1-line abstract (Failed: Order 0.33 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_abstract_6line` | page | OfflineDocumentConverter | 23ms | 0.00% | 0.50 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 6-line abstract (Failed: Order 0.50 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_12pt` | page | OfflineDocumentConverter | 34ms | 0.00% | 0.56 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 12pt gap (Failed: Order 0.56 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_18pt` | page | OfflineDocumentConverter | 27ms | 0.00% | 0.56 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 18pt gap (Failed: Order 0.56 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_22pt` | page | OfflineDocumentConverter | 32ms | 0.00% | 0.56 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 22pt gap (Failed: Order 0.56 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_30pt` | page | OfflineDocumentConverter | 29ms | 0.00% | 0.56 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 30pt gap (Failed: Order 0.56 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_40pt` | page | OfflineDocumentConverter | 31ms | 0.00% | 0.56 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 40pt gap (Failed: Order 0.56 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_gap_8pt` | page | OfflineDocumentConverter | 33ms | 37.78% | 0.33 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 8pt gap (Failed: WER 37.78% > 5.00%; Order 0.33 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_with_footnote` | page | OfflineDocumentConverter | 22ms | 15.79% | 0.60 | 0.00 | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with footnote (Failed: WER 15.79% > 5.00%; Order 0.60 < 0.95; Heading F1 0.67 < 0.90) |
| `banner_with_sidebar` | page | OfflineDocumentConverter | 35ms | 0.00% | 0.50 | n/a | 0.80 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with sidebar (Failed: Order 0.50 < 0.95; Heading F1 0.80 < 0.90) |
| `blank_middle_page` | document | OfflineDocumentConverter | 23ms | 21.43% | 0.50 | n/a | n/a | VALID | 0 | **EXPECTED_FAIL** | F10: assembler does not emit blank page comments or page 1 marker (Failed: WER 21.43% > 5.00%; Order 0.50 < 0.95; Markers [2] != expected [1, 2, 3]) |
| `digital_article_2col` | page | OfflineDocumentConverter | 67ms | 0.00% | 0.67 | n/a | 0.57 | VALID | 0 | **EXPECTED_FAIL** | F1: banner over two columns causes reading order failure with 22.2pt gap (Failed: Order 0.67 < 0.95; Heading F1 0.57 < 0.90) |
| `digital_math` | page | OfflineDocumentConverter | 25ms | 2.70% | 0.25 | n/a | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F15: reading order and alignment on sub/superscript math formulas (Failed: Order 0.25 < 0.95) |
| `digital_multipage_table` | document | OfflineDocumentConverter | 56ms | 1.31% | 0.97 | 1.00 | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F10: assembler does not emit page 1 marker (Failed: Markers [2, 3] != expected [1, 2, 3]) |
| `digital_nospace_kerning` | page | OfflineDocumentConverter | 680ms | 20.93% | 0.75 | n/a | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F15: word segmentation on kerned and negative-spaced text (Failed: WER 20.93% > 5.00%; Order 0.75 < 0.95) |
| `digital_right_aligned_numbers_table` | page | OfflineDocumentConverter | 24ms | 0.00% | 1.00 | 1.00 | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_table_heavy` | page | OfflineDocumentConverter | 37ms | 0.00% | 1.00 | 1.00 | 0.50 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_table_multiword` | page | OfflineDocumentConverter | 35ms | 0.00% | 1.00 | 1.00 | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_two_column_real` | page | OfflineDocumentConverter | 107ms | 0.00% | 0.36 | n/a | 0.33 | VALID | 0 | **EXPECTED_FAIL** | F1: sentence-level reading order across two columns (Failed: Order 0.36 < 0.95) |
| `digital_with_figures` | page | OfflineDocumentConverter | 21ms | 22.73% | 0.80 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F3: vector figure text ordering and placement (Failed: WER 22.73% > 5.00%; Order 0.80 < 0.95) |
| `digital_with_raster_figure` | page | OfflineDocumentConverter | 51ms | 22.73% | 0.60 | n/a | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F11: raster figure box placed at hardcoded coordinates (Failed: WER 22.73% > 5.00%; Order 0.60 < 0.95) |
| `mixed_slides` | page | OfflineDocumentConverter | 17ms | 50.82% | 0.62 | n/a | 0.33 | VALID | 0 | **EXPECTED_FAIL** | F3: slide box layout ordering across multi-column slide items (Failed: WER 50.82% > 5.00%; Order 0.62 < 0.95) |
| `page_selection_sample` | document | OfflineDocumentConverter | 25ms | 144.74% | 1.00 | n/a | 0.57 | VALID | 0 | **EXPECTED_FAIL** | F10: assembler does not preserve original page numbers for selected pages (Failed: WER 144.74% > 5.00%; Heading F1 0.57 < 0.90; Markers [2, 3, 4, 5] != expected [2, 4]) |
| `question_bank_pages` | document | OfflineDocumentConverter | 71ms | 4.06% | 0.99 | n/a | 0.00 | VALID | 0 | **EXPECTED_FAIL** | F9: 5-page document headings stripped by line-index cleaner (Failed: Heading F1 0.00 < 0.90; Contains forbidden text: 'Confidential · Examination Board Assessment Materials') |
| `rotated_page` | page | OfflineDocumentConverter | 18ms | 0.00% | 1.00 | n/a | 0.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `scan_image_only` | page | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `scan_image_with_stamp` | page | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `scan_raster_multipage` | document | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `signature_line` | page | OfflineDocumentConverter | 15ms | 0.00% | 0.50 | 0.00 | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F14: two-row signature line erroneously parsed as table (Failed: Order 0.50 < 0.95) |
| `three_column_banner` | page | OfflineDocumentConverter | 25ms | 44.19% | 0.25 | 0.00 | 0.40 | VALID | 0 | **EXPECTED_FAIL** | F1: three-column layout under banner causes reading order failure (Failed: WER 44.19% > 5.00%; Order 0.25 < 0.95; Heading F1 0.40 < 0.90) |
| `toc_dot_leaders` | page | OfflineDocumentConverter | 16ms | 0.00% | 0.50 | n/a | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F14: table of contents dot leaders ordering / format (Failed: Order 0.50 < 0.95) |
| `typed_notes_sample` | page | OfflineDocumentConverter | 18ms | 0.00% | 0.22 | n/a | 1.00 | VALID | 0 | **EXPECTED_FAIL** | F1: margin note box layout reading order (Failed: Order 0.22 < 0.95) |
| `typed_printed_sample` | page | OfflineDocumentConverter | 19ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |

---

### Metric Definitions
- **WER**: Word Error Rate on normalized token sequences vs golden.
- **Order Score**: Sentence-level Longest Common Subsequence (LCS) ratio over normalized sentences.
- **Table F1**: Structural cell alignment F1 matching `(table_idx, row_idx, col_idx, text)` triples. Evaluated as `n/a` when neither document contains tables.
- **Heading F1**: Precision/Recall F1 over heading level and title text.
- **Math Balance**: Delimiter parity verification for `$...$` and `$$...$$`.
- **Glued (>25 chars)**: Count of continuous unpunctuated tokens exceeding 25 characters without space.


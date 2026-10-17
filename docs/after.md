# MARKDROP VERIFICATION REPORT — ROUND 3

Status: Machine-Generated from `tools/evaluate.py` evaluation run.
Summary: **28** PASS | **3** EXPECTED FAIL | **0** UNEXPECTED FAIL | **3** NOT RUN

---

## Objective Evaluation Results Table

| Document | Tier | Extracted By | Wall-Clock | WER | Order Score | Table F1 | Heading F1 | Math Balance | Glued (>25 chars) | Verdict | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `address_block_two_row` | page | OfflineDocumentConverter | 19ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_abstract_1line` | page | OfflineDocumentConverter | 21ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_abstract_6line` | page | OfflineDocumentConverter | 29ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_12pt` | page | OfflineDocumentConverter | 29ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_18pt` | page | OfflineDocumentConverter | 29ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_22pt` | page | OfflineDocumentConverter | 34ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_30pt` | page | OfflineDocumentConverter | 29ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_40pt` | page | OfflineDocumentConverter | 48ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_gap_8pt` | page | OfflineDocumentConverter | 37ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_with_footnote` | page | OfflineDocumentConverter | 25ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `banner_with_sidebar` | page | OfflineDocumentConverter | 22ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `blank_middle_page` | document | OfflineDocumentConverter | 36ms | 0.00% | 1.00 | n/a | n/a | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_article_2col` | page | OfflineDocumentConverter | 80ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_math` | page | OfflineDocumentConverter | 44ms | 2.70% | 0.42 | n/a | 0.60 | VALID | 0 | **EXPECTED_FAIL** | F15: reading order and alignment on sub/superscript math formulas (Failed: Order 0.42 < 0.95; Heading F1 0.60 < 0.90) |
| `digital_multipage_table` | document | OfflineDocumentConverter | 117ms | 0.00% | 1.00 | 1.00 | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_nospace_kerning` | page | OfflineDocumentConverter | 689ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_right_aligned_numbers_table` | page | OfflineDocumentConverter | 32ms | 0.00% | 1.00 | 1.00 | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_table_heavy` | page | OfflineDocumentConverter | 36ms | 0.00% | 1.00 | 1.00 | 0.50 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_table_multiword` | page | OfflineDocumentConverter | 36ms | 0.00% | 1.00 | 1.00 | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_two_column_real` | page | OfflineDocumentConverter | 108ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `digital_with_figures` | page | OfflineDocumentConverter | 31ms | 22.73% | 0.80 | n/a | 0.67 | VALID | 0 | **EXPECTED_FAIL** | F3: vector figure text ordering and placement (Failed: WER 22.73% > 5.00%; Order 0.80 < 0.95) |
| `digital_with_raster_figure` | page | OfflineDocumentConverter | 57ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `mixed_slides` | page | OfflineDocumentConverter | 20ms | 50.82% | 0.62 | n/a | 0.33 | VALID | 0 | **EXPECTED_FAIL** | F3: slide box layout ordering across multi-column slide items (Failed: WER 50.82% > 5.00%; Order 0.62 < 0.95) |
| `page_selection_sample` | document | OfflineDocumentConverter | 31ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `question_bank_pages` | document | OfflineDocumentConverter | 87ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `rotated_page` | page | OfflineDocumentConverter | 24ms | 0.00% | 1.00 | n/a | 0.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `scan_image_only` | page | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `scan_image_with_stamp` | page | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `scan_raster_multipage` | document | None | 0ms | n/a | n/a | n/a | n/a | n/a | n/a | **NOT RUN (needs on-device ML Kit / vision runtime)** | needs on-device ML Kit / vision runtime |
| `signature_line` | page | OfflineDocumentConverter | 18ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `three_column_banner` | page | OfflineDocumentConverter | 21ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `toc_dot_leaders` | page | OfflineDocumentConverter | 17ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `typed_notes_sample` | page | OfflineDocumentConverter | 20ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |
| `typed_printed_sample` | page | OfflineDocumentConverter | 16ms | 0.00% | 1.00 | n/a | 1.00 | VALID | 0 | **PASS** | Thresholds satisfied |

---

### Metric Definitions
- **WER**: Word Error Rate on normalized token sequences vs golden.
- **Order Score**: Sentence-level Longest Common Subsequence (LCS) ratio over normalized sentences.
- **Table F1**: Structural cell alignment F1 matching `(table_idx, row_idx, col_idx, text)` triples. Evaluated as `n/a` when neither document contains tables.
- **Heading F1**: Precision/Recall F1 over heading level and title text.
- **Math Balance**: Delimiter parity verification for `$...$` and `$$...$$`.
- **Glued (>25 chars)**: Count of continuous unpunctuated tokens exceeding 25 characters without space.


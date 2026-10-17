# Markdrop Evaluation Corpus (Round 3)

This directory contains standardized benchmark PDFs and ground-truth golden Markdown documents used to verify layout extraction, table parsing, heading hierarchy, reading order, and multi-page document assembly.

## Corpus Inventory

### 1. Page-Tier Born-Digital Fixtures (`golden/<name>.page1.md`)

| Document | Type | Verification Target |
|---|---|---|
| `digital_nospace_kerning.pdf` | Digital PDF | E1: Kerning offset analysis without explicit space glyphs |
| `digital_two_column_real.pdf` | Digital PDF | E2: 2-column reading order preservation with title & footnote |
| `digital_article_2col.pdf` | Digital PDF | F1: Spanning banner/abstract over two columns with 22pt gap |
| `digital_table_heavy.pdf` | Digital PDF | E3, E4: Multi-row, multi-column tables with headers and numbers |
| `digital_table_multiword.pdf` | Digital PDF | E3, E4: Multi-word table cells, empty cells, and number alignment |
| `digital_right_aligned_numbers_table.pdf` | Digital PDF | F14: Right-aligned numerical table columns |
| `digital_math.pdf` | Digital PDF | Math delimiter balance (`$...$` and `$$...$$`) and formatting |
| `digital_with_figures.pdf` | Digital PDF | Text flow around vector diagrams |
| `digital_with_raster_figure.pdf` | Digital PDF | F11: Real embedded raster image placeholder emission |
| `mixed_slides.pdf` | Digital PDF | Landscape slide layout with bullet lists and headers |
| `three_column_banner.pdf` | Digital PDF | 3-column layout under a spanning banner |
| `typed_printed_sample.pdf` | Digital PDF | Formatted printed document sample |
| `typed_notes_sample.pdf` | Digital PDF | Structured notes sample with checklists and margin notes |
| `rotated_page.pdf` | Digital PDF | 90-degree clockwise rotated page geometry |
| `encrypted.pdf` | Digital PDF | F8: AES-256 password-protected PDF probe |

### 2. Negative Table Fixtures (Must NOT become tables)

| Document | Target Behavior |
|---|---|
| `address_block_two_row.pdf` | 2-row address block parsed as regular paragraphs, not a table |
| `signature_line.pdf` | Signature and date lines parsed as plain text, not a table |
| `toc_dot_leaders.pdf` | Table of contents with dot leaders parsed as plain text, not a table |

### 3. Article Banner Matrix (`article_banner_matrix/*.pdf`)

A systematic grid of 2-column academic papers varying banner-to-column gaps (8pt to 40pt), abstract heights (1, 3, 6 lines), footnotes, and sidebars to verify recursive XY-cut boundary isolation.

### 4. Document-Tier Multi-Page Fixtures (`golden/<name>.doc.md`)

| Document | Page Count | Target Behavior |
|---|---|---|
| `question_bank_pages.pdf` | 5 | F9: Retains `Question N` headings and removes repeating confidential footers |
| `digital_multipage_table.pdf` | 3 | F10, F14: Assembles pages with `<!-- page: N -->` markers and merges tables |
| `blank_middle_page.pdf` | 3 | F10: Emits `<!-- blank page -->` for page 2 |
| `page_selection_sample.pdf` | 5 | F10: Supports page subset selection (e.g. pages 2 & 4) |

### 5. Raster Scans & Unmeasured Fixtures (NOT RUN on JVM)

| Document | Type | Status & Reason |
|---|---|---|
| `scan_image_only.pdf` | Scanned Image | `NOT RUN` (Requires on-device ML Kit / vision runtime) |
| `scan_image_with_stamp.pdf` | Scanned Image + Stamp | `NOT RUN` (Requires on-device ML Kit / vision runtime) |
| `scan_raster_multipage.pdf` | 3-page Scanned Image | `NOT RUN` (Requires on-device ML Kit / vision runtime) |

# Markdrop Evaluation Corpus

This folder holds evaluation PDFs and ground-truth golden Markdown documents used to measure conversion fidelity and regression resistance.

## Files

| File | Type | Source / Status | Description |
|---|---|---|---|
| `digital_article_2col.pdf` | Digital PDF | Synthetic / Generated | 2-column academic style article with title, abstract, two columns of body text, headings, and bibliography. |
| `digital_table_heavy.pdf` | Digital PDF | Synthetic / Generated | Multi-row, multi-column tables with headers, numbers, and text cells. |
| `digital_math.pdf` | Digital PDF | Synthetic / Generated | Document with mathematical formulas (fractions, square roots, summation, inline variables). |
| `scan_printed.pdf` | Scanned PDF | Needs user file | Scanned printed document (photocopied or flatbed scan at ~150-300 DPI) with noise, slight skew, headers/footers. |
| `scan_handwritten.pdf` | Scanned PDF | Needs user file | Scanned handwritten notes, equations, or annotations. |
| `mixed_slides.pdf` | Digital / Mixed | Synthetic / Generated | Landscape presentation slides with bullet points, figures, and callout boxes. |

## Golden Truths (`golden/`)

For page 1 of each corpus file, `golden/<name>.page1.md` contains the hand-verified, target GitHub-Flavored Markdown.
Baseline and after-run tests compare the generated page 1 Markdown against these golden references.

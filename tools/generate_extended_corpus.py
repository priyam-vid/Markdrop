#!/usr/bin/env python3
"""
Generates the extended evaluation corpus for Markdrop Round 3.
Produces realistic PDF files designed to expose defects F1-F16, along with verified golden Markdown files.
"""

import os
import random
import pymupdf as fitz
from PIL import Image, ImageDraw, ImageFilter

base_dir = os.path.dirname(__file__)
corpus_dir = os.path.abspath(os.path.join(base_dir, "..", "testdata", "corpus"))
golden_dir = os.path.join(corpus_dir, "golden")
matrix_dir = os.path.join(corpus_dir, "article_banner_matrix")
matrix_golden_dir = os.path.join(golden_dir, "article_banner_matrix")

os.makedirs(corpus_dir, exist_ok=True)
os.makedirs(golden_dir, exist_ok=True)
os.makedirs(matrix_dir, exist_ok=True)
os.makedirs(matrix_golden_dir, exist_ok=True)

def write_golden(path: str, content: str):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content.strip() + "\n")

# ---------------------------------------------------------------------------
# 1. digital_nospace_kerning.pdf
# ---------------------------------------------------------------------------
def make_nospace_kerning():
    stream = """
BT
/helv 18 Tf
50 780 Td
[(Neural) -250 (Document) -250 (Understanding) -250 (Without) -250 (Spaces)] TJ
ET
BT
/helv 11 Tf
50 740 Td
[(Word) -300 (segmentation) -300 (must) -300 (rely) -300 (on) -300 (geometric) -300 (gap) -300 (analysis)] TJ
0 -20 Td
[(when) -300 (typesetting) -300 (systems) -300 (emit) -300 (kerning) -300 (offsets) -300 (without) -300 (space) -300 (glyphs.)] TJ
0 -30 Td
[(In) -300 (modern) -300 (documents,) -300 (inter-word) -300 (spacing) -300 (is) -300 (frequently) -300 (encoded) -300 (via) -300 (font) -300 (shifts.)] TJ
ET
"""
    pdf_bytes = f"""%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj
4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
5 0 obj << /Length {len(stream)} >>
stream{stream}
endstream
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000227 00000 n 
0000000305 00000 n 
trailer << /Size 6 /Root 1 0 R >>
startxref
{320 + len(stream)}
%%EOF
"""
    with open(os.path.join(corpus_dir, "digital_nospace_kerning.pdf"), "wb") as f:
        f.write(pdf_bytes.encode('latin1'))

    golden_md = """# Neural Document Understanding Without Spaces

Word segmentation must rely on geometric gap analysis when typesetting systems emit kerning offsets without space glyphs.

In modern documents, inter-word spacing is frequently encoded via font shifts.
"""
    write_golden(os.path.join(golden_dir, "digital_nospace_kerning.page1.md"), golden_md)
    write_golden(os.path.join(golden_dir, "digital_nospace_kerning.doc.md"), f"<!-- page: 1 -->\n\n{golden_md.strip()}")

# ---------------------------------------------------------------------------
# 2. digital_two_column_real.pdf
# ---------------------------------------------------------------------------
def make_two_column_real():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 60), "Scalable Layout Analysis for Mobile Devices", fontsize=18, fontname="helv")
    page.insert_text(fitz.Point(50, 85), "An Empirical Investigation into Deterministic Reconstruction", fontsize=11, fontname="times-italic")
    page.draw_line(fitz.Point(50, 95), fitz.Point(545, 95), color=(0.8, 0.8, 0.8))

    r_left = fitz.Rect(50, 115, 280, 700)
    page.insert_textbox(r_left,
        "1. Introduction\n\n"
        "Document reconstruction on mobile devices requires robust spatial partitioning. When reading a two-column layout, content must proceed down the entire left column before transitioning to the right column.\n\n"
        "2. Left Column Details\n\n"
        "Without proper bounding box geometry, naïve parsers interleave horizontal rows between adjacent columns, resulting in scrambled sentences and unreadable output.",
        fontsize=10, fontname="times-roman")

    r_right = fitz.Rect(315, 115, 545, 700)
    page.insert_textbox(r_right,
        "3. Right Column Progress\n\n"
        "The right column continues the primary narrative thread. Spatial XY-cut algorithms project horizontal and vertical white-space gutters across the page to isolate column blocks cleanly.\n\n"
        "4. Conclusion\n\n"
        "Evaluating spatial separation ensures that column order is preserved under all zoom levels and font resolutions.",
        fontsize=10, fontname="times-roman")

    page.draw_line(fitz.Point(50, 735), fitz.Point(200, 735), color=(0.5, 0.5, 0.5))
    page.insert_text(fitz.Point(50, 755), "* Correspondence should be directed to the Mobile Systems Research Laboratory.", fontsize=8, fontname="times-italic")

    doc.save(os.path.join(corpus_dir, "digital_two_column_real.pdf"))
    doc.close()

    golden_md = """# Scalable Layout Analysis for Mobile Devices

*An Empirical Investigation into Deterministic Reconstruction*

## 1. Introduction

Document reconstruction on mobile devices requires robust spatial partitioning. When reading a two-column layout, content must proceed down the entire left column before transitioning to the right column.

## 2. Left Column Details

Without proper bounding box geometry, naïve parsers interleave horizontal rows between adjacent columns, resulting in scrambled sentences and unreadable output.

## 3. Right Column Progress

The right column continues the primary narrative thread. Spatial XY-cut algorithms project horizontal and vertical white-space gutters across the page to isolate column blocks cleanly.

## 4. Conclusion

Evaluating spatial separation ensures that column order is preserved under all zoom levels and font resolutions.

* Correspondence should be directed to the Mobile Systems Research Laboratory.
"""
    write_golden(os.path.join(golden_dir, "digital_two_column_real.page1.md"), golden_md)
    write_golden(os.path.join(golden_dir, "digital_two_column_real.doc.md"), f"<!-- page: 1 -->\n\n{golden_md.strip()}")

# ---------------------------------------------------------------------------
# 3. digital_article_2col.pdf (F1: Abstract over two columns with 22pt gap)
# ---------------------------------------------------------------------------
def make_article_2col():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)

    page.insert_text(fitz.Point(50, 60), "Neural Document Understanding: A Two-Column Study", fontsize=18, fontname="helv", color=(0.1, 0.2, 0.5))
    page.insert_text(fitz.Point(50, 85), "Abstract", fontsize=12, fontname="helv", color=(0.2, 0.2, 0.2))
    rect_abstract = fitz.Rect(50, 95, 545, 148)
    page.insert_textbox(rect_abstract, 
        "This paper presents an evaluation of layout analysis and text reconstruction techniques for complex document architectures. We demonstrate that column separation and reading order preservation are essential for accurate downstream processing.",
        fontsize=10, fontname="times-roman")

    # Column 1 (Left: x=50 to x=280, top=170.2 -> gap is 170.2 - 148 = 22.2pt)
    rect_col1 = fitz.Rect(50, 170.2, 280, 500)
    page.insert_textbox(rect_col1,
        "1. Introduction\n\n"
        "Modern document understanding pipelines face significant challenges when transcribing multi-column layouts. Without proper spatial reasoning, reading order across columns can easily become corrupted.\n\n"
        "When reading a two-column page, a human reader naturally proceeds from the top of the left column to the bottom, before continuing to the top of the right column.",
        fontsize=10, fontname="times-roman")

    # Column 2 (Right: x=315 to x=545, top=170.2)
    rect_col2 = fitz.Rect(315, 170.2, 545, 500)
    page.insert_textbox(rect_col2,
        "2. Methodology\n\n"
        "Our approach groups tokens into lines and uses recursive XY-cut partitioning to identify column gutters and margins.\n\n"
        "This ensures that independent columns are transcribed sequentially rather than interleaved horizontally.\n\n"
        "3. Experimental Setup\n\n"
        "We evaluated the pipeline across a diverse set of synthetic and real-world benchmark documents containing varied fonts and densities.",
        fontsize=10, fontname="times-roman")

    doc.save(os.path.join(corpus_dir, "digital_article_2col.pdf"))
    doc.close()

    golden_md = """# Neural Document Understanding: A Two-Column Study

## Abstract
This paper presents an evaluation of layout analysis and text reconstruction techniques for complex document architectures. We demonstrate that column separation and reading order preservation are essential for accurate downstream processing.

## 1. Introduction
Modern document understanding pipelines face significant challenges when transcribing multi-column layouts. Without proper spatial reasoning, reading order across columns can easily become corrupted.

When reading a two-column page, a human reader naturally proceeds from the top of the left column to the bottom, before continuing to the top of the right column.

## 2. Methodology
Our approach groups tokens into lines and uses recursive XY-cut partitioning to identify column gutters and margins.

This ensures that independent columns are transcribed sequentially rather than interleaved horizontally.

## 3. Experimental Setup
We evaluated the pipeline across a diverse set of synthetic and real-world benchmark documents containing varied fonts and densities.
"""
    write_golden(os.path.join(golden_dir, "digital_article_2col.page1.md"), golden_md)
    write_golden(os.path.join(golden_dir, "digital_article_2col.doc.md"), f"<!-- page: 1 -->\n\n{golden_md.strip()}")

# ---------------------------------------------------------------------------
# 4. article_banner_matrix generator (F1 sweep)
# ---------------------------------------------------------------------------
def make_banner_matrix():
    gaps = [8, 12, 18, 22, 30, 40]
    for gap in gaps:
        name = f"banner_gap_{gap}pt"
        doc = fitz.open()
        page = doc.new_page(width=595, height=842)
        page.insert_text(fitz.Point(50, 50), f"Academic Study with Banner Gap {gap}pt", fontsize=16, fontname="helv")
        page.insert_text(fitz.Point(50, 75), "Abstract", fontsize=12, fontname="helv")
        
        # Abstract height: 3 lines (~35pt)
        r_abs = fitz.Rect(50, 85, 545, 120)
        page.insert_textbox(r_abs, 
            "This study investigates multi-column segmentation under varying vertical separation intervals. "
            "We systematically evaluate whether recursive projection profile analysis cleanly isolates spanning banners "
            "from underlying columnar blocks.", fontsize=9, fontname="times-roman")
        
        col_top = 120 + gap
        r_left = fitz.Rect(50, col_top, 280, col_top + 300)
        page.insert_textbox(r_left,
            "1. Left Column Analysis\n\n"
            "The primary hypothesis asserts that banner boundaries must be established before column gutter analysis is performed.\n\n"
            "Failing to separate banners causes spanning lines to block column detection.",
            fontsize=9, fontname="times-roman")

        r_right = fitz.Rect(315, col_top, 545, col_top + 300)
        page.insert_textbox(r_right,
            "2. Right Column Results\n\n"
            "Experimental findings confirm that adaptive band grouping completely resolves line interleaving.\n\n"
            "Sequential reading order is preserved across all tested separation intervals.",
            fontsize=9, fontname="times-roman")

        doc.save(os.path.join(matrix_dir, f"{name}.pdf"))
        doc.close()

        golden_md = f"""# Academic Study with Banner Gap {gap}pt

## Abstract
This study investigates multi-column segmentation under varying vertical separation intervals. We systematically evaluate whether recursive projection profile analysis cleanly isolates spanning banners from underlying columnar blocks.

## 1. Left Column Analysis
The primary hypothesis asserts that banner boundaries must be established before column gutter analysis is performed.

Failing to separate banners causes spanning lines to block column detection.

## 2. Right Column Results
Experimental findings confirm that adaptive band grouping completely resolves line interleaving.

Sequential reading order is preserved across all tested separation intervals.
"""
        write_golden(os.path.join(matrix_golden_dir, f"{name}.page1.md"), golden_md)
        write_golden(os.path.join(matrix_golden_dir, f"{name}.doc.md"), f"<!-- page: 1 -->\n\n{golden_md.strip()}")

    # Abstract 1-line and 6-line variants
    for abs_lines, abs_name, abs_h in [(1, "banner_abstract_1line", 15), (6, "banner_abstract_6line", 70)]:
        name = abs_name
        doc = fitz.open()
        page = doc.new_page(width=595, height=842)
        page.insert_text(fitz.Point(50, 50), f"Academic Study with Abstract ({abs_lines} Lines)", fontsize=16, fontname="helv")
        page.insert_text(fitz.Point(50, 75), "Abstract", fontsize=12, fontname="helv")
        
        r_abs = fitz.Rect(50, 85, 545, 85 + abs_h)
        text = "Short single line abstract summary." if abs_lines == 1 else (
            "This extended abstract occupies multiple printed lines spanning across the entire width of the page. "
            "It deliberately increases the vertical footprint of the banner block to test whether projection algorithms "
            "mistakenly categorize spanning sentences as non-column blocks or interleave them with adjacent text. "
            "Rigorous spatial partitioning guarantees that such spanning headers remain unified."
        )
        page.insert_textbox(r_abs, text, fontsize=9, fontname="times-roman")
        
        col_top = 85 + abs_h + 22
        r_left = fitz.Rect(50, col_top, 280, col_top + 300)
        page.insert_textbox(r_left,
            "1. Left Column Exploration\n\n"
            "Column text starts below the spanning abstract regardless of how many lines the abstract contains.",
            fontsize=9, fontname="times-roman")

        r_right = fitz.Rect(315, col_top, 545, col_top + 300)
        page.insert_textbox(r_right,
            "2. Right Column Verification\n\n"
            "The right column reads continuously after the entire left column has been transcribed.",
            fontsize=9, fontname="times-roman")

        doc.save(os.path.join(matrix_dir, f"{name}.pdf"))
        doc.close()

        golden_md = f"""# Academic Study with Abstract ({abs_lines} Lines)

## Abstract
{text}

## 1. Left Column Exploration
Column text starts below the spanning abstract regardless of how many lines the abstract contains.

## 2. Right Column Verification
The right column reads continuously after the entire left column has been transcribed.
"""
        write_golden(os.path.join(matrix_golden_dir, f"{name}.page1.md"), golden_md)
        write_golden(os.path.join(matrix_golden_dir, f"{name}.doc.md"), f"<!-- page: 1 -->\n\n{golden_md.strip()}")

    # Footnote variant
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 50), "Banner Document with Footnote Band", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 75), "Abstract", fontsize=12, fontname="helv")
    r_abs = fitz.Rect(50, 85, 545, 120)
    page.insert_textbox(r_abs, "A study of multi-column layouts containing both a top banner and a bottom footnote.", fontsize=9, fontname="times-roman")
    r_left = fitz.Rect(50, 142, 280, 500)
    page.insert_textbox(r_left, "1. Left Section\n\nLeft column discussion.", fontsize=9, fontname="times-roman")
    r_right = fitz.Rect(315, 142, 545, 500)
    page.insert_textbox(r_right, "2. Right Section\n\nRight column discussion.", fontsize=9, fontname="times-roman")
    page.draw_line(fitz.Point(50, 750), fitz.Point(200, 750), color=(0.6, 0.6, 0.6))
    page.insert_text(fitz.Point(50, 765), "* Institutional Review Board Approval #49281.", fontsize=8, fontname="times-italic")
    doc.save(os.path.join(matrix_dir, "banner_with_footnote.pdf"))
    doc.close()
    golden_fn = """# Banner Document with Footnote Band

## Abstract
A study of multi-column layouts containing both a top banner and a bottom footnote.

## 1. Left Section
Left column discussion.

## 2. Right Section
Right column discussion.

* Institutional Review Board Approval #49281.
"""
    write_golden(os.path.join(matrix_golden_dir, "banner_with_footnote.page1.md"), golden_fn)
    write_golden(os.path.join(matrix_golden_dir, "banner_with_footnote.doc.md"), f"<!-- page: 1 -->\n\n{golden_fn.strip()}")

    # Sidebar variant
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 50), "Banner Document with Sidebar", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 75), "Abstract", fontsize=12, fontname="helv")
    r_abs = fitz.Rect(50, 85, 545, 120)
    page.insert_textbox(r_abs, "A study of layouts featuring an explanatory sidebar alongside main text columns.", fontsize=9, fontname="times-roman")
    r_left = fitz.Rect(50, 142, 340, 500)
    page.insert_textbox(r_left, "1. Main Narrative\n\nThe primary technical content flows in the main section.", fontsize=9, fontname="times-roman")
    r_right = fitz.Rect(380, 142, 545, 500)
    page.insert_textbox(r_right, "Sidebar Note\n\nKey definitions and contextual remarks.", fontsize=9, fontname="times-roman")
    doc.save(os.path.join(matrix_dir, "banner_with_sidebar.pdf"))
    doc.close()
    golden_sb = """# Banner Document with Sidebar

## Abstract
A study of layouts featuring an explanatory sidebar alongside main text columns.

## 1. Main Narrative
The primary technical content flows in the main section.

Sidebar Note
Key definitions and contextual remarks.
"""
    write_golden(os.path.join(matrix_golden_dir, "banner_with_sidebar.page1.md"), golden_sb)
    write_golden(os.path.join(matrix_golden_dir, "banner_with_sidebar.doc.md"), f"<!-- page: 1 -->\n\n{golden_sb.strip()}")

# ---------------------------------------------------------------------------
# 5. three_column_banner.pdf
# ---------------------------------------------------------------------------
def make_three_column_banner():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 50), "Three-Column Journal Overview", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 75), "Abstract summary across all three columns.", fontsize=10, fontname="times-roman")
    
    col_w = 150
    gap = 22
    for i, title in enumerate(["Col 1 Alpha", "Col 2 Beta", "Col 3 Gamma"]):
        left = 50 + i * (col_w + gap)
        rect = fitz.Rect(left, 110, left + col_w, 400)
        page.insert_textbox(rect, f"### {title}\n\nContent for {title} detailing technical progress.", fontsize=9, fontname="times-roman")
    
    doc.save(os.path.join(corpus_dir, "three_column_banner.pdf"))
    doc.close()

    golden = """# Three-Column Journal Overview

Abstract summary across all three columns.

### Col 1 Alpha

Content for Col 1 Alpha detailing technical progress.

### Col 2 Beta

Content for Col 2 Beta detailing technical progress.

### Col 3 Gamma

Content for Col 3 Gamma detailing technical progress.
"""
    write_golden(os.path.join(golden_dir, "three_column_banner.page1.md"), golden)
    write_golden(os.path.join(golden_dir, "three_column_banner.doc.md"), f"<!-- page: 1 -->\n\n{golden.strip()}")

# ---------------------------------------------------------------------------
# 6. digital_right_aligned_numbers_table.pdf
# ---------------------------------------------------------------------------
def make_right_aligned_table():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 60), "Quarterly Financial Performance", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 85), "Detailed ledger containing right-aligned numerical quantities.", fontsize=10, fontname="times-roman")

    headers = ["Product Line", "Units Sold", "Unit Price", "Total Revenue"]
    # Right-aligned x coordinates for numeric columns: col 1 left at 50, col 2 right at 260, col 3 right at 380, col 4 right at 540
    data = [
        ["Hardware Alpha", "1,420", "$15.50", "$22,010.00"],
        ["Software Beta", "850", "$42.00", "$35,700.00"],
        ["Services Gamma", "3,100", "$8.25", "$25,575.00"]
    ]

    col_x = [50, 180, 300, 420, 545]
    row_y = [110, 135, 160, 185, 210]

    for i, h in enumerate(headers):
        page.insert_text(fitz.Point(col_x[i] + 5, 128), h, fontsize=9, fontname="helv")

    for r_idx, row in enumerate(data):
        y = row_y[r_idx + 1]
        for c_idx, val in enumerate(row):
            page.insert_text(fitz.Point(col_x[c_idx] + 5, y + 18), val, fontsize=9, fontname="times-roman")

    for y in row_y:
        page.draw_line(fitz.Point(50, y), fitz.Point(545, y), color=(0.7, 0.7, 0.7))
    for x in col_x:
        page.draw_line(fitz.Point(x, 110), fitz.Point(x, 210), color=(0.7, 0.7, 0.7))

    doc.save(os.path.join(corpus_dir, "digital_right_aligned_numbers_table.pdf"))
    doc.close()

    golden = """# Quarterly Financial Performance

Detailed ledger containing right-aligned numerical quantities.

| Product Line | Units Sold | Unit Price | Total Revenue |
| --- | --- | --- | --- |
| Hardware Alpha | 1,420 | $15.50 | $22,010.00 |
| Software Beta | 850 | $42.00 | $35,700.00 |
| Services Gamma | 3,100 | $8.25 | $25,575.00 |
"""
    write_golden(os.path.join(golden_dir, "digital_right_aligned_numbers_table.page1.md"), golden)
    write_golden(os.path.join(golden_dir, "digital_right_aligned_numbers_table.doc.md"), f"<!-- page: 1 -->\n\n{golden.strip()}")

# ---------------------------------------------------------------------------
# 7. Negative Table Fixtures
# ---------------------------------------------------------------------------
def make_negative_table_fixtures():
    # 1. address_block_two_row.pdf
    doc1 = fitz.open()
    page1 = doc1.new_page(width=595, height=842)
    page1.insert_text(fitz.Point(50, 60), "Official Corporate Correspondence", fontsize=16, fontname="helv")
    page1.insert_text(fitz.Point(50, 100), "Acme Corporation                     123 Industrial Parkway", fontsize=10, fontname="times-roman")
    page1.insert_text(fitz.Point(50, 118), "Billing Operations                   Suite 400, Metro City", fontsize=10, fontname="times-roman")
    page1.insert_text(fitz.Point(50, 160), "Please remit payment in accordance with the agreed schedule.", fontsize=10, fontname="times-roman")
    doc1.save(os.path.join(corpus_dir, "address_block_two_row.pdf"))
    doc1.close()
    golden1 = """# Official Corporate Correspondence

Acme Corporation 123 Industrial Parkway
Billing Operations Suite 400, Metro City

Please remit payment in accordance with the agreed schedule.
"""
    write_golden(os.path.join(golden_dir, "address_block_two_row.page1.md"), golden1)
    write_golden(os.path.join(golden_dir, "address_block_two_row.doc.md"), f"<!-- page: 1 -->\n\n{golden1.strip()}")

    # 2. signature_line.pdf
    doc2 = fitz.open()
    page2 = doc2.new_page(width=595, height=842)
    page2.insert_text(fitz.Point(50, 60), "Contract Execution Acceptance", fontsize=16, fontname="helv")
    page2.insert_text(fitz.Point(50, 100), "The undersigned hereby executes this agreement.", fontsize=10, fontname="times-roman")
    page2.insert_text(fitz.Point(50, 140), "Name: ____________________       Date: ______________", fontsize=10, fontname="courier")
    page2.insert_text(fitz.Point(50, 160), "Title: ___________________       Sign: ______________", fontsize=10, fontname="courier")
    doc2.save(os.path.join(corpus_dir, "signature_line.pdf"))
    doc2.close()
    golden2 = """# Contract Execution Acceptance

The undersigned hereby executes this agreement.

Name: ____________________ Date: ______________
Title: ___________________ Sign: ______________
"""
    write_golden(os.path.join(golden_dir, "signature_line.page1.md"), golden2)
    write_golden(os.path.join(golden_dir, "signature_line.doc.md"), f"<!-- page: 1 -->\n\n{golden2.strip()}")

    # 3. toc_dot_leaders.pdf
    doc3 = fitz.open()
    page3 = doc3.new_page(width=595, height=842)
    page3.insert_text(fitz.Point(50, 60), "Table of Contents", fontsize=16, fontname="helv")
    page3.insert_text(fitz.Point(50, 100), "Chapter 1: Theoretical Foundations .................................... 1", fontsize=10, fontname="courier")
    page3.insert_text(fitz.Point(50, 120), "Chapter 2: System Architecture ....................................... 15", fontsize=10, fontname="courier")
    page3.insert_text(fitz.Point(50, 140), "Chapter 3: Experimental Evaluation ................................... 42", fontsize=10, fontname="courier")
    doc3.save(os.path.join(corpus_dir, "toc_dot_leaders.pdf"))
    doc3.close()
    golden3 = """# Table of Contents

Chapter 1: Theoretical Foundations .................................... 1
Chapter 2: System Architecture ....................................... 15
Chapter 3: Experimental Evaluation ................................... 42
"""
    write_golden(os.path.join(golden_dir, "toc_dot_leaders.page1.md"), golden3)
    write_golden(os.path.join(golden_dir, "toc_dot_leaders.doc.md"), f"<!-- page: 1 -->\n\n{golden3.strip()}")

# ---------------------------------------------------------------------------
# 8. question_bank_pages.pdf (5 pages x >= 12 non-empty lines, repeating footer)
# ---------------------------------------------------------------------------
def make_question_bank_5pages():
    doc = fitz.open()
    topics = [
        ("Question 1: Differential Equations", [
            "Consider a second-order linear ordinary differential equation with constant coefficients.",
            "Let y'' + 4y' + 5y = 0 subject to initial conditions y(0) = 1 and y'(0) = -2.",
            "Part A: Determine the characteristic roots of the auxiliary algebraic equation.",
            "Part B: Write the fundamental set of real-valued solutions using Euler's formula.",
            "Part C: Apply the initial conditions to evaluate the arbitrary integration constants.",
            "Part D: Analyze the long-term asymptotic behavior as t approaches infinity.",
            "Part E: State whether the system is underdamped, critically damped, or overdamped.",
            "Part F: Calculate the quasi-frequency of the decaying oscillatory response curve.",
            "Part G: Sketch the phase portrait trajectory in the (y, y') coordinate state space.",
            "Part H: Compute the Laplace transform of the inhomogeneous system with forcing function f(t) = exp(-t).",
            "Part I: Find the steady-state transfer function poles and zeros.",
            "Part J: Justify why uniqueness holds according to the Picard-Lindelof existence theorem."
        ]),
        ("Question 2: Linear Algebra and Matrix Theory", [
            "Let A be a real symmetric matrix of order 4 with known trace and determinant.",
            "Part A: Prove that all eigenvalues of matrix A are strictly real numbers.",
            "Part B: Demonstrate that eigenvectors corresponding to distinct eigenvalues are mutually orthogonal.",
            "Part C: Construct an orthonormal basis for R^4 using the Gram-Schmidt orthogonalization procedure.",
            "Part D: Compute the spectral decomposition A = Q Lambda Q^T explicitly.",
            "Part E: Evaluate the matrix exponential exp(At) using diagonalization techniques.",
            "Part F: State the Cayley-Hamilton theorem and compute the characteristic polynomial p(lambda).",
            "Part G: Express the inverse of matrix A as a linear combination of lower powers.",
            "Part H: Determine whether the associated quadratic form x^T A x is positive definite.",
            "Part I: Calculate the singular value decomposition (SVD) of the rank-deficient variant.",
            "Part J: Compute the Moore-Penrose pseudoinverse for the minimum-norm least squares solution.",
            "Part K: Derive the condition number kappa(A) under the Euclidean matrix 2-norm."
        ]),
        ("Question 3: Multivariable Calculus and Field Theory", [
            "Let F(x, y, z) = (2xy + z^2) i + (x^2 + 2yz) j + (y^2 + 2xz) k be a three-dimensional vector field.",
            "Part A: Compute the curl of vector field F and determine if F is irrotational everywhere.",
            "Part B: Find a scalar potential function phi(x, y, z) such that F equals the gradient of phi.",
            "Part C: Evaluate the line integral of F along a smooth path from (0,0,0) to (1,2,3).",
            "Part D: State the fundamental theorem of line integrals and verify path independence.",
            "Part E: Compute the divergence of F and find the locations of source or sink behavior.",
            "Part F: Let S be the surface of the closed unit sphere centered at the coordinate origin.",
            "Part G: Apply the Gauss Divergence Theorem to evaluate the net outward flux across S.",
            "Part H: Compute the circulation along the boundary curve C bounding the upper hemisphere.",
            "Part I: Verify Stokes' Theorem by directly calculating both the surface and contour integrals.",
            "Part J: Formulate Green's Theorem as a two-dimensional special case in the xy-plane.",
            "Part K: Determine the differential geometric curvature of the resulting streamline curves."
        ]),
        ("Question 4: Quantum Mechanics and Wave Equations", [
            "Consider a quantum particle of mass m confined within an infinite one-dimensional square well potential.",
            "Let the potential V(x) = 0 for 0 < x < L and V(x) = infinity elsewhere.",
            "Part A: Write down the time-independent Schrodinger wave equation for the interior region.",
            "Part B: Apply Dirichlet boundary conditions psi(0) = 0 and psi(L) = 0 to quantize energy levels.",
            "Part C: Derive the normalized stationary state eigenfunctions psi_n(x).",
            "Part D: Calculate the discrete quantized energy eigenvalues E_n as a function of quantum number n.",
            "Part E: Formulate a general superposition state wave packet at initial time t = 0.",
            "Part F: Determine the time evolution operator and evaluate the expectation value of position <x>.",
            "Part G: Compute the momentum expectation value <p> and verify Ehrenfest's theorem.",
            "Part H: Calculate the uncertainty product Delta x * Delta p and verify Heisenberg's principle.",
            "Part I: Find the probability density current j(x, t) and verify probability conservation.",
            "Part J: Discuss the parity symmetry properties of the eigenfunctions under coordinate inversion.",
            "Part K: Analyze the tunneling transmission coefficient through a finite rectangular barrier."
        ]),
        ("Question 5: Electrodynamics and Wave Propagation", [
            "Consider Maxwell's equations in linear isotropic homogeneous non-conducting dielectric media.",
            "Part A: State the four microscopic Maxwell equations in differential vector form.",
            "Part B: Derive the decoupled electromagnetic wave equations for fields E and B from curl equations.",
            "Part C: Compute the propagation phase velocity v in terms of permittivity epsilon and permeability mu.",
            "Part D: Formulate monochromatic plane wave solutions propagating along the positive z-axis.",
            "Part E: Demonstrate the transverse electromagnetic (TEM) nature of free propagating plane waves.",
            "Part F: Calculate the instantaneous and time-averaged Poynting vector S for energy flux density.",
            "Part G: Determine the electromagnetic momentum density and radiation pressure on an absorbing surface.",
            "Part H: Apply electromagnetic boundary conditions across a planar interface between two dielectric media.",
            "Part I: Derive Fresnel's reflection and transmission coefficients for s-polarized (TE) incident waves.",
            "Part J: Derive Fresnel's equations for p-polarized (TM) waves and compute Brewster's polarizing angle.",
            "Part K: Determine the skin depth delta for wave penetration into a conducting metallic waveguide."
        ])
    ]

    for p_idx, (title, lines) in enumerate(topics):
        page = doc.new_page(width=595, height=842)
        # Heading
        page.insert_text(fitz.Point(50, 70), title, fontsize=14, fontname="helv")
        
        # 12 non-empty lines
        for l_idx, line_text in enumerate(lines):
            page.insert_text(fitz.Point(50, 110 + l_idx * 24), line_text, fontsize=9, fontname="times-roman")
            
        # Repeating footer in bottom 7% (y > 780 pt)
        page.draw_line(fitz.Point(50, 790), fitz.Point(545, 790), color=(0.7, 0.7, 0.7))
        page.insert_text(fitz.Point(50, 810), f"Confidential · Examination Board Assessment Materials · Page {p_idx + 1}", fontsize=8, fontname="helv")

    doc.save(os.path.join(corpus_dir, "question_bank_pages.pdf"))
    doc.close()

    # Page 1 golden (page tier - no cleaner, includes footer)
    q1_title, q1_lines = topics[0]
    page1_golden = f"## {q1_title}\n\n" + "\n".join(q1_lines) + "\n\nConfidential · Examination Board Assessment Materials · Page 1\n"
    write_golden(os.path.join(golden_dir, "question_bank_pages.page1.md"), page1_golden)

    # Document golden (document tier - cleaner removes footers, assembler adds page markers)
    doc_sections = []
    for p_idx, (title, lines) in enumerate(topics):
        body = "\n".join(lines)
        doc_sections.append(f"<!-- page: {p_idx + 1} -->\n\n## {title}\n\n{body}")
    doc_golden = "\n\n".join(doc_sections)
    write_golden(os.path.join(golden_dir, "question_bank_pages.doc.md"), doc_golden)

# ---------------------------------------------------------------------------
# 9. digital_with_raster_figure.pdf (real embedded raster image)
# ---------------------------------------------------------------------------
def make_digital_with_raster_figure():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 60), "Deep Learning Document Understanding Pipeline", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 90), "The architecture processes documents via unified layout analysis and vision extraction.", fontsize=10, fontname="times-roman")

    # Generate an image with PIL and embed it as a true raster image
    img = Image.new("RGB", (800, 300), color=(240, 242, 245))
    draw = ImageDraw.Draw(img)
    draw.rectangle([10, 10, 790, 290], outline=(80, 80, 80), width=3)
    draw.text((250, 130), "RASTER HARDWARE BLOCK DIAGRAM", fill=(40, 40, 40))
    img_path = os.path.join(corpus_dir, "temp_fig.png")
    img.save(img_path)

    # Insert raster image into PDF
    img_rect = fitz.Rect(50, 120, 545, 270)
    page.insert_image(img_rect, filename=img_path)
    if os.path.exists(img_path):
        os.remove(img_path)

    page.insert_text(fitz.Point(50, 290), "Figure 1: Architectural diagram of the neural ingestion pipeline.", fontsize=9, fontname="times-italic")
    page.insert_text(fitz.Point(50, 320), "All tensor computations run locally on the neural accelerator.", fontsize=10, fontname="times-roman")

    doc.save(os.path.join(corpus_dir, "digital_with_raster_figure.pdf"))
    doc.close()

    golden = """# Deep Learning Document Understanding Pipeline

The architecture processes documents via unified layout analysis and vision extraction.

> **Figure:** (image on this page — not transcribed in Offline mode)

*Figure 1: Architectural diagram of the neural ingestion pipeline.*

All tensor computations run locally on the neural accelerator.
"""
    write_golden(os.path.join(golden_dir, "digital_with_raster_figure.page1.md"), golden)
    write_golden(os.path.join(golden_dir, "digital_with_raster_figure.doc.md"), f"<!-- page: 1 -->\n\n{golden.strip()}")

# ---------------------------------------------------------------------------
# 10. Multi-Page Fixtures: blank_middle_page & page_selection_sample
# ---------------------------------------------------------------------------
def make_multipage_structural_fixtures():
    # 1. blank_middle_page.pdf (3 pages: page 1 text, page 2 blank, page 3 text)
    doc1 = fitz.open()
    p1 = doc1.new_page(width=595, height=842)
    p1.insert_text(fitz.Point(50, 100), "This is page 1 content of the multi-page report.", fontsize=12, fontname="times-roman")
    # Page 2 is empty
    doc1.new_page(width=595, height=842)
    # Page 3
    p3 = doc1.new_page(width=595, height=842)
    p3.insert_text(fitz.Point(50, 100), "This is page 3 content of the multi-page report.", fontsize=12, fontname="times-roman")
    doc1.save(os.path.join(corpus_dir, "blank_middle_page.pdf"))
    doc1.close()

    golden_blank = """<!-- page: 1 -->

This is page 1 content of the multi-page report.

<!-- page: 2 -->
<!-- blank page -->

<!-- page: 3 -->

This is page 3 content of the multi-page report.
"""
    write_golden(os.path.join(golden_dir, "blank_middle_page.doc.md"), golden_blank)

    # 2. page_selection_sample.pdf (5 pages)
    doc2 = fitz.open()
    for i in range(1, 6):
        p = doc2.new_page(width=595, height=842)
        p.insert_text(fitz.Point(50, 100), f"Section Content for Page Number {i}", fontsize=14, fontname="helv")
        p.insert_text(fitz.Point(50, 130), f"This text is unique to page {i} of the comprehensive document.", fontsize=10, fontname="times-roman")
    doc2.save(os.path.join(corpus_dir, "page_selection_sample.pdf"))
    doc2.close()

    golden_sel = """<!-- page: 2 -->

# Section Content for Page Number 2

This text is unique to page 2 of the comprehensive document.

<!-- page: 4 -->

# Section Content for Page Number 4

This text is unique to page 4 of the comprehensive document.
"""
    write_golden(os.path.join(golden_dir, "page_selection_sample.doc.md"), golden_sel)

# ---------------------------------------------------------------------------
# 11. scan_raster_multipage.pdf (3 real raster pages with noise/skew)
# ---------------------------------------------------------------------------
def make_scan_raster_multipage():
    doc = fitz.open()
    for p_num in range(1, 4):
        img_w, img_h = 1000, 1400
        img = Image.new("RGB", (img_w, img_h), color=(252, 250, 245))
        draw = ImageDraw.Draw(img)
        draw.text((80, 100), f"SCANNED RECORD PAGE {p_num}", fill=(20, 20, 20))
        draw.text((80, 150), f"Telemetry Reading Session {p_num * 100}", fill=(40, 40, 40))
        for line_i in range(1, 8):
            draw.text((80, 200 + line_i * 50), f"Record entry item {line_i}: sensor channel measurement within tolerance bounds.", fill=(35, 35, 35))
        
        # Noise
        for _ in range(3000):
            nx = random.randint(0, img_w - 1)
            ny = random.randint(0, img_h - 1)
            val = random.randint(180, 230)
            img.putpixel((nx, ny), (val, val, val))
        
        skewed = img.rotate(0.4 if p_num % 2 == 1 else -0.4, resample=Image.BICUBIC, expand=False, fillcolor=(252, 250, 245))
        blurred = skewed.filter(ImageFilter.GaussianBlur(radius=0.3))
        
        temp_img_path = os.path.join(corpus_dir, f"temp_raster_p{p_num}.png")
        blurred.save(temp_img_path)
        
        p = doc.new_page(width=595, height=842)
        p.insert_image(fitz.Rect(0, 0, 595, 842), filename=temp_img_path)
        if os.path.exists(temp_img_path):
            os.remove(temp_img_path)

    doc.save(os.path.join(corpus_dir, "scan_raster_multipage.pdf"))
    doc.close()

def main():
    print("Generating comprehensive Round 3 corpus...")
    make_nospace_kerning()
    make_two_column_real()
    make_article_2col()
    make_banner_matrix()
    make_three_column_banner()
    make_right_aligned_table()
    make_negative_table_fixtures()
    make_question_bank_5pages()
    make_digital_with_raster_figure()
    make_multipage_structural_fixtures()
    make_scan_raster_multipage()
    print("Corpus generation complete.")

if __name__ == "__main__":
    main()

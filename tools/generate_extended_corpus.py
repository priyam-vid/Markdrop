#!/usr/bin/env python3
"""
Generates the extended evaluation corpus for Markdrop Round 2.
Produces realistic PDF files designed to expose defects E1-E13, along with verified golden Markdown files.
"""

import os
import random
import pymupdf as fitz
from PIL import Image, ImageDraw, ImageFont, ImageFilter

corpus_dir = os.path.join(os.path.dirname(__file__), "..", "testdata", "corpus")
golden_dir = os.path.join(corpus_dir, "golden")
os.makedirs(corpus_dir, exist_ok=True)
os.makedirs(golden_dir, exist_ok=True)

# ---------------------------------------------------------------------------
# 1. digital_nospace_kerning.pdf (E1 regression: kerning gaps, NO space glyphs)
# ---------------------------------------------------------------------------
def make_nospace_kerning():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    
    # We create raw PDF text stream commands with TJ kerning offsets and NO space characters
    # Font F1 = Helvetica
    font_res = page.insert_font(fontname="helv", fontbuffer=None)
    
    # In PDF, TJ takes an array of strings and displacement numbers (negative means move right, i.e. gap)
    # [(Neural) -250 (Document) -250 (Understanding) -250 (Without) -250 (Spaces)] TJ
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
    # Insert raw stream into page
    # FitZ allows inserting text or raw stream
    # Let's insert using clean fitz API or raw stream
    # In PyMuPDF, page._write(...) or draw through font with text words
    # To be 100% compliant with standard PDF engines, let's write with fitz text writer or raw content
    # Actually, fitz TextWriter can insert text with manual positions, or we can write a minimal PDF directly!
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
    with open(os.path.join(golden_dir, "digital_nospace_kerning.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 2. digital_two_column_real.pdf (E2 regression: 2 columns + title + footnote)
# ---------------------------------------------------------------------------
def make_two_column_real():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842) # A4
    
    # Full width title
    page.insert_text(fitz.Point(50, 60), "Scalable Layout Analysis for Mobile Devices", fontsize=18, fontname="helv")
    page.insert_text(fitz.Point(50, 85), "An Empirical Investigation into Deterministic Reconstruction", fontsize=11, fontname="times-italic")
    page.draw_line(fitz.Point(50, 95), fitz.Point(545, 95), color=(0.8, 0.8, 0.8))

    # Left column: x=50 to x=280
    r_left = fitz.Rect(50, 115, 280, 700)
    page.insert_textbox(r_left,
        "1. Introduction\n\n"
        "Document reconstruction on mobile devices requires robust spatial partitioning. When reading a two-column layout, content must proceed down the entire left column before transitioning to the right column.\n\n"
        "2. Left Column Details\n\n"
        "Without proper bounding box geometry, naïve parsers interleave horizontal rows between adjacent columns, resulting in scrambled sentences and unreadable output.",
        fontsize=10, fontname="times-roman")

    # Right column: x=315 to x=545
    r_right = fitz.Rect(315, 115, 545, 700)
    page.insert_textbox(r_right,
        "3. Right Column Progress\n\n"
        "The right column continues the primary narrative thread. Spatial XY-cut algorithms project horizontal and vertical white-space gutters across the page to isolate column blocks cleanly.\n\n"
        "4. Conclusion\n\n"
        "Evaluating spatial separation ensures that column order is preserved under all zoom levels and font resolutions.",
        fontsize=10, fontname="times-roman")

    # Footnote band across bottom: x=50 to x=545, y=740 to y=780
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
    with open(os.path.join(golden_dir, "digital_two_column_real.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 3. digital_table_multiword.pdf (E3/E4: multiword cells, empty cell, numbers)
# ---------------------------------------------------------------------------
def make_table_multiword():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)

    page.insert_text(fitz.Point(50, 60), "Data Center Infrastructure Performance", fontsize=18, fontname="helv")
    page.insert_text(fitz.Point(50, 90), "Below is the infrastructure utilization summary recorded across compute clusters.", fontsize=10, fontname="times-roman")

    col_x = [50, 160, 260, 360, 460, 545]
    row_y = [120, 150, 180, 210, 240]

    headers = ["Cluster Node", "Cores Allocated", "Memory Pool", "Network IO", "Operating Status"]
    for i in range(len(headers)):
        page.insert_text(fitz.Point(col_x[i] + 4, 138), headers[i], fontsize=9, fontname="helv")

    rows = [
        ["Alpha Primary", "64 Cores", "256 GB", "12.8 Gbps", "Online Active"],
        ["Beta Secondary", "32 Cores", "", "8.4 Gbps", "Standby Mode"],
        ["Gamma Worker", "128 Cores", "512 GB", "24.1 Gbps", "Online Active"]
    ]

    for r_idx, r_data in enumerate(rows):
        curr_y = row_y[r_idx + 1]
        for c_idx, val in enumerate(r_data):
            if val:
                page.insert_text(fitz.Point(col_x[c_idx] + 4, curr_y + 18), val, fontsize=9, fontname="times-roman")

    for y in row_y:
        page.draw_line(fitz.Point(50, y), fitz.Point(545, y), color=(0.7, 0.7, 0.7))
    for x in col_x:
        page.draw_line(fitz.Point(x, 120), fitz.Point(x, 240), color=(0.7, 0.7, 0.7))

    page.insert_text(fitz.Point(50, 280), "Cluster health checks verified zero thermal throttling incidents during testing.", fontsize=10, fontname="times-roman")

    doc.save(os.path.join(corpus_dir, "digital_table_multiword.pdf"))
    doc.close()

    golden_md = """# Data Center Infrastructure Performance

Below is the infrastructure utilization summary recorded across compute clusters.

| Cluster Node | Cores Allocated | Memory Pool | Network IO | Operating Status |
| --- | --- | --- | --- | --- |
| Alpha Primary | 64 Cores | 256 GB | 12.8 Gbps | Online Active |
| Beta Secondary | 32 Cores | | 8.4 Gbps | Standby Mode |
| Gamma Worker | 128 Cores | 512 GB | 24.1 Gbps | Online Active |

Cluster health checks verified zero thermal throttling incidents during testing.
"""
    with open(os.path.join(golden_dir, "digital_table_multiword.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 4. digital_multipage_table.pdf (E11: 3-page table continuation)
# ---------------------------------------------------------------------------
def make_multipage_table():
    doc = fitz.open()
    headers = ["Component ID", "Subsystem Category", "Fault Tolerance", "Telemetry Latency"]
    col_x = [50, 160, 310, 430, 545]
    
    for p_num in range(3):
        page = doc.new_page(width=595, height=842)
        page.insert_text(fitz.Point(50, 60), f"System Architecture Telemetry (Part {p_num + 1})", fontsize=16, fontname="helv")
        
        # Header row on each page
        row_y = 100
        for i, h in enumerate(headers):
            page.insert_text(fitz.Point(col_x[i] + 5, row_y + 18), h, fontsize=9, fontname="helv")
        page.draw_line(fitz.Point(50, row_y), fitz.Point(545, row_y), color=(0.6, 0.6, 0.6))
        page.draw_line(fitz.Point(50, row_y + 25), fitz.Point(545, row_y + 25), color=(0.6, 0.6, 0.6))
        
        # 4 rows per page
        for r in range(4):
            item_num = p_num * 4 + r + 1
            curr_y = row_y + 25 + r * 25
            row_data = [f"COMP-{item_num:03d}", "Kernel Dispatcher", "High Redundancy", f"{1.2 * item_num:.1f} ms"]
            for i, val in enumerate(row_data):
                page.insert_text(fitz.Point(col_x[i] + 5, curr_y + 18), val, fontsize=9, fontname="times-roman")
            page.draw_line(fitz.Point(50, curr_y + 25), fitz.Point(545, curr_y + 25), color=(0.8, 0.8, 0.8))

    doc.save(os.path.join(corpus_dir, "digital_multipage_table.pdf"))
    doc.close()

    golden_md = """# System Architecture Telemetry (Part 1)

| Component ID | Subsystem Category | Fault Tolerance | Telemetry Latency |
| --- | --- | --- | --- |
| COMP-001 | Kernel Dispatcher | High Redundancy | 1.2 ms |
| COMP-002 | Kernel Dispatcher | High Redundancy | 2.4 ms |
| COMP-003 | Kernel Dispatcher | High Redundancy | 3.6 ms |
| COMP-004 | Kernel Dispatcher | High Redundancy | 4.8 ms |
"""
    with open(os.path.join(golden_dir, "digital_multipage_table.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 5. question_bank_pages.pdf (E10: Question N headings + repeating footer)
# ---------------------------------------------------------------------------
def make_question_bank():
    doc = fitz.open()
    questions = [
        ("Question 1: Differential Equations", "Find the general solution to the second-order homogeneous differential equation y'' + 4y = 0."),
        ("Question 2: Linear Algebra", "Determine the eigenvalues and corresponding eigenvectors for the matrix A = [[3, 1], [0, 2]]."),
        ("Question 3: Multivariable Calculus", "Evaluate the surface integral of vector field F over the closed unit sphere centered at origin.")
    ]
    for p_idx, (q_title, q_body) in enumerate(questions):
        page = doc.new_page(width=595, height=842)
        # Top heading (Question N)
        page.insert_text(fitz.Point(50, 80), q_title, fontsize=14, fontname="helv")
        rect_body = fitz.Rect(50, 110, 545, 300)
        page.insert_textbox(rect_body, q_body, fontsize=10, fontname="times-roman")
        
        # Repeating footer in bottom 7% (y > 780): Confidential notice + page number
        page.draw_line(fitz.Point(50, 800), fitz.Point(545, 800), color=(0.7, 0.7, 0.7))
        page.insert_text(fitz.Point(50, 815), f"Confidential — Examination Board Assessment Materials — Page {p_idx + 1}", fontsize=8, fontname="helv")

    doc.save(os.path.join(corpus_dir, "question_bank_pages.pdf"))
    doc.close()

    golden_md = """## Question 1: Differential Equations

Find the general solution to the second-order homogeneous differential equation y'' + 4y = 0.
"""
    with open(os.path.join(golden_dir, "question_bank_pages.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 6. digital_with_figures.pdf (E13: Figure box emission)
# ---------------------------------------------------------------------------
def make_digital_with_figures():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)

    page.insert_text(fitz.Point(50, 60), "Deep Learning Document Understanding Pipeline", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 90), "The architecture processes documents via unified layout analysis and vision extraction.", fontsize=10, fontname="times-roman")

    # Insert a visual figure box
    page.draw_rect(fitz.Rect(70, 120, 520, 300), color=(0.3, 0.3, 0.3), fill=(0.94, 0.94, 0.96))
    page.insert_text(fitz.Point(180, 215), "[System Architecture Block Diagram]", fontsize=12, fontname="helv")

    page.insert_text(fitz.Point(70, 320), "Figure 1: Architectural diagram of the neural ingestion pipeline.", fontsize=9, fontname="times-italic")
    page.insert_text(fitz.Point(50, 360), "All tensor computations run locally on the neural accelerator.", fontsize=10, fontname="times-roman")

    doc.save(os.path.join(corpus_dir, "digital_with_figures.pdf"))
    doc.close()

    golden_md = """# Deep Learning Document Understanding Pipeline

The architecture processes documents via unified layout analysis and vision extraction.

> **Figure:** (image on this page — not transcribed in Offline mode)

*Figure 1: Architectural diagram of the neural ingestion pipeline.*

All tensor computations run locally on the neural accelerator.
"""
    with open(os.path.join(golden_dir, "digital_with_figures.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 7. scan_image_only.pdf (E17: TRUE raster scan with noise and skew via PIL)
# ---------------------------------------------------------------------------
def make_scan_image_only():
    # Render an image using PIL
    img_w, img_h = 1200, 1600
    img = Image.new("RGB", (img_w, img_h), color=(250, 249, 245))
    draw = ImageDraw.Draw(img)

    # Draw simulated text lines
    draw.text((100, 120), "OFFICIAL LABORATORY INSPECTION REPORT", fill=(20, 20, 20))
    draw.text((100, 180), "Facility Location: Analytical Research Facility Sector 7", fill=(40, 40, 40))
    draw.text((100, 240), "Status: Verified Compliant under Biosafety Level 2 Standards", fill=(40, 40, 40))
    draw.text((100, 320), "1. Ventilation and airflow mechanisms passed the pressure differential test.", fill=(30, 30, 30))
    draw.text((100, 380), "2. Chemical containment storage cabinets were securely locked and labeled.", fill=(30, 30, 30))
    draw.text((100, 440), "3. Emergency eyewash stations were flushed and operational.", fill=(30, 30, 30))

    # Add noise
    for _ in range(5000):
        nx = random.randint(0, img_w - 1)
        ny = random.randint(0, img_h - 1)
        val = random.randint(180, 240)
        img.putpixel((nx, ny), (val, val, val))

    # Slight skew (rotate 0.6 degrees)
    skewed = img.rotate(0.6, resample=Image.BICUBIC, expand=False, fillcolor=(250, 249, 245))
    blurred = skewed.filter(ImageFilter.GaussianBlur(radius=0.4))

    # Convert to PDF
    pdf_path = os.path.join(corpus_dir, "scan_image_only.pdf")
    blurred.save(pdf_path, "PDF", resolution=150.0)

    golden_md = """# OFFICIAL LABORATORY INSPECTION REPORT

Facility Location: Analytical Research Facility Sector 7

Status: Verified Compliant under Biosafety Level 2 Standards

1. Ventilation and airflow mechanisms passed the pressure differential test.
2. Chemical containment storage cabinets were securely locked and labeled.
3. Emergency eyewash stations were flushed and operational.
"""
    with open(os.path.join(golden_dir, "scan_image_only.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 8. scan_image_with_stamp.pdf (E13: Real raster scan + 60-char footer stamp)
# ---------------------------------------------------------------------------
def make_scan_image_with_stamp():
    # Load the raster scan from scan_image_only.pdf and add a tiny digital footer
    doc = fitz.open(os.path.join(corpus_dir, "scan_image_only.pdf"))
    page = doc[0]
    # Add a 60-char text footer
    page.insert_text(fitz.Point(80, page.rect.height - 30), "RECORD ID: BATES-0049281-CONFIDENTIAL-DISTRICT-COURT-ARCHIVE", fontsize=7, fontname="courier")
    doc.save(os.path.join(corpus_dir, "scan_image_with_stamp.pdf"))
    doc.close()

    golden_md = """# OFFICIAL LABORATORY INSPECTION REPORT

Facility Location: Analytical Research Facility Sector 7

Status: Verified Compliant under Biosafety Level 2 Standards

1. Ventilation and airflow mechanisms passed the pressure differential test.
2. Chemical containment storage cabinets were securely locked and labeled.
3. Emergency eyewash stations were flushed and operational.
"""
    with open(os.path.join(golden_dir, "scan_image_with_stamp.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 9. rotated_page.pdf (E9: 90-degree rotated page)
# ---------------------------------------------------------------------------
def make_rotated_page():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(80, 100), "Landscape Technical Specification Table", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(80, 140), "This document was scanned in 90 degree clockwise rotation.", fontsize=11, fontname="times-roman")
    page.set_rotation(90)
    doc.save(os.path.join(corpus_dir, "rotated_page.pdf"))
    doc.close()

    golden_md = """# Landscape Technical Specification Table

This document was scanned in 90 degree clockwise rotation.
"""
    with open(os.path.join(golden_dir, "rotated_page.page1.md"), "w", encoding="utf-8") as f:
        f.write(golden_md.strip() + "\n")

# ---------------------------------------------------------------------------
# 10. encrypted.pdf (E9: Password protected)
# ---------------------------------------------------------------------------
def make_encrypted():
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.insert_text(fitz.Point(50, 100), "Protected Executive Summary", fontsize=16, fontname="helv")
    page.insert_text(fitz.Point(50, 140), "This document requires encryption password to decrypt.", fontsize=11, fontname="times-roman")
    # Save with user password
    doc.save(os.path.join(corpus_dir, "encrypted.pdf"), encryption=fitz.PDF_ENCRYPT_AES_256, user_pw="markdrop123", owner_pw="adminpass")
    doc.close()

print("Generating extended corpus...")
make_nospace_kerning()
make_two_column_real()
make_table_multiword()
make_multipage_table()
make_question_bank()
make_digital_with_figures()
make_scan_image_only()
make_scan_image_with_stamp()
make_rotated_page()
make_encrypted()
print("Extended corpus generated successfully.")

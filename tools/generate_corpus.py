import fitz  # PyMuPDF
import os

corpus_dir = os.path.join(os.path.dirname(__file__), "..", "testdata", "corpus")
os.makedirs(corpus_dir, exist_ok=True)

# 1. digital_article_2col.pdf
doc1 = fitz.open()
page1 = doc1.new_page(width=595, height=842) # A4

# Document Title
page1.insert_text(fitz.Point(50, 60), "Neural Document Understanding: A Two-Column Study", fontsize=18, fontname="helv", color=(0.1, 0.2, 0.5))

# Abstract
page1.insert_text(fitz.Point(50, 90), "Abstract", fontsize=14, fontname="helv", color=(0.2, 0.2, 0.2))
rect_abstract = fitz.Rect(50, 100, 545, 150)
page1.insert_textbox(rect_abstract, 
    "This paper presents an evaluation of layout analysis and text reconstruction techniques for complex document architectures. We demonstrate that column separation and reading order preservation are essential for accurate downstream processing.",
    fontsize=10, fontname="times-roman")

# Column 1 (Left: x=50 to x=280)
rect_col1 = fitz.Rect(50, 160, 280, 500)
page1.insert_textbox(rect_col1,
    "1. Introduction\n\n"
    "Modern document understanding pipelines face significant challenges when transcribing multi-column layouts. Without proper spatial reasoning, reading order across columns can easily become corrupted.\n\n"
    "When reading a two-column page, a human reader naturally proceeds from the top of the left column to the bottom, before continuing to the top of the right column.",
    fontsize=10, fontname="times-roman")

# Column 2 (Right: x=315 to x=545)
rect_col2 = fitz.Rect(315, 160, 545, 500)
page1.insert_textbox(rect_col2,
    "2. Methodology\n\n"
    "Our approach groups tokens into lines and uses recursive XY-cut partitioning to identify column gutters and margins.\n\n"
    "This ensures that independent columns are transcribed sequentially rather than interleaved horizontally.\n\n"
    "3. Experimental Setup\n\n"
    "We evaluated the pipeline across a diverse set of synthetic and real-world benchmark documents containing varied fonts and densities.",
    fontsize=10, fontname="times-roman")

doc1.save(os.path.join(corpus_dir, "digital_article_2col.pdf"))
doc1.close()

# 2. digital_table_heavy.pdf
doc2 = fitz.open()
page2 = doc2.new_page(width=595, height=842)

page2.insert_text(fitz.Point(50, 60), "Quarterly Hardware Performance Benchmarks", fontsize=18, fontname="helv", color=(0.1, 0.2, 0.5))
page2.insert_text(fitz.Point(50, 90), "Below are the benchmark metrics recorded across four computing clusters during the fiscal testing cycle.", fontsize=10, fontname="times-roman")

# Table
# Draw table grid
table_top = 120
col_x = [50, 150, 230, 320, 440, 545]
row_y = [120, 145, 170, 195, 220, 245]

# Table headers
headers = ["Server Node", "CPU Cores", "Memory (GB)", "Throughput (req/s)", "Latency (ms)"]
for i in range(len(headers)):
    page2.insert_text(fitz.Point(col_x[i] + 5, table_top + 18), headers[i], fontsize=9, fontname="helv")

# Table rows
rows_data = [
    ["Node Alpha", "32", "128", "4850", "12.4"],
    ["Node Beta", "64", "256", "9200", "8.1"],
    ["Node Gamma", "16", "64", "2400", "22.7"],
    ["Node Delta", "128", "512", "18500", "4.3"]
]

for r_idx, r_data in enumerate(rows_data):
    curr_y = row_y[r_idx + 1]
    for c_idx, cell_value in enumerate(r_data):
        page2.insert_text(fitz.Point(col_x[c_idx] + 5, curr_y + 18), cell_value, fontsize=9, fontname="times-roman")

# Lines
for y in row_y:
    page2.draw_line(fitz.Point(50, y), fitz.Point(545, y), color=(0.7, 0.7, 0.7))
for x in col_x:
    page2.draw_line(fitz.Point(x, 120), fitz.Point(x, 245), color=(0.7, 0.7, 0.7))

page2.insert_text(fitz.Point(50, 280), "Cluster Utilization Summary", fontsize=13, fontname="helv")
rect_summary = fitz.Rect(50, 290, 545, 360)
page2.insert_textbox(rect_summary, 
    "All nodes operated within the nominal thermal dissipation threshold. Secondary storage replication remained synchronized throughout the duration of the benchmark.",
    fontsize=10, fontname="times-roman")

doc2.save(os.path.join(corpus_dir, "digital_table_heavy.pdf"))
doc2.close()

# 3. digital_math.pdf
doc3 = fitz.open()
page3 = doc3.new_page(width=595, height=842)

page3.insert_text(fitz.Point(50, 60), "Mathematical Foundations of Signal Attenuation", fontsize=18, fontname="helv", color=(0.1, 0.2, 0.5))
page3.insert_text(fitz.Point(50, 95), "1. Classical Energy Relations", fontsize=13, fontname="helv")
page3.insert_text(fitz.Point(50, 115), "The total kinetic energy in relativistic mechanics is given by the celebrated relation:", fontsize=10, fontname="times-roman")
page3.insert_text(fitz.Point(220, 145), "E = mc^2", fontsize=12, fontname="times-italic")

page3.insert_text(fitz.Point(50, 175), "For an electromagnetic field propagating through an absorbing medium with absorption coefficient alpha,", fontsize=10, fontname="times-roman")
page3.insert_text(fitz.Point(50, 190), "the intensity I(x) at depth x satisfies the differential equation:", fontsize=10, fontname="times-roman")
page3.insert_text(fitz.Point(220, 220), "dI/dx = -alpha * I(x)", fontsize=12, fontname="times-italic")

page3.insert_text(fitz.Point(50, 250), "Solving this relation yields the standard exponential decay law:", fontsize=10, fontname="times-roman")
page3.insert_text(fitz.Point(220, 280), "I(x) = I_0 * e^(-alpha * x)", fontsize=12, fontname="times-italic")

page3.insert_text(fitz.Point(50, 320), "2. Discrete Fourier Transform", fontsize=13, fontname="helv")
page3.insert_text(fitz.Point(50, 340), "For a discrete sequence x_n of length N, the transform X_k is computed as:", fontsize=10, fontname="times-roman")
page3.insert_text(fitz.Point(180, 370), "X_k = sum_{n=0}^{N-1} x_n * e^(-i * 2pi * k * n / N)", fontsize=12, fontname="times-italic")
page3.insert_text(fitz.Point(50, 400), "where i = sqrt(-1).", fontsize=10, fontname="times-roman")

doc3.save(os.path.join(corpus_dir, "digital_math.pdf"))
doc3.close()

# 4. mixed_slides.pdf (Landscape)
doc4 = fitz.open()
page4 = doc4.new_page(width=842, height=595) # Landscape

page4.insert_text(fitz.Point(50, 60), "Cloud Architecture Modernization", fontsize=22, fontname="helv", color=(0.1, 0.2, 0.5))

# Figure / Diagram placeholder box
page4.draw_rect(fitz.Rect(50, 90, 400, 260), color=(0.4, 0.4, 0.4), fill=(0.95, 0.95, 0.97))
page4.insert_text(fitz.Point(70, 175), "Figure: System Architecture Diagram (Monolith vs Microservices)", fontsize=10, fontname="helv", color=(0.3, 0.3, 0.3))

# Key Milestones (Right side)
page4.insert_text(fitz.Point(430, 110), "Key Migration Milestones", fontsize=14, fontname="helv")
page4.insert_text(fitz.Point(440, 140), "- Phase 1: Decouple user authentication and session cache", fontsize=11, fontname="times-roman")
page4.insert_text(fitz.Point(440, 170), "- Phase 2: Migrate persistent transactional data to distributed shards", fontsize=11, fontname="times-roman")
page4.insert_text(fitz.Point(440, 200), "- Phase 3: Transition background job scheduling to event-driven serverless workers", fontsize=11, fontname="times-roman")

page4.insert_text(fitz.Point(50, 310), "Target SLA", fontsize=14, fontname="helv")
page4.insert_text(fitz.Point(60, 340), "- Service availability: 99.99% uptime", fontsize=11, fontname="times-roman")
page4.insert_text(fitz.Point(60, 370), "- Median request latency: under 45 milliseconds", fontsize=11, fontname="times-roman")

doc4.save(os.path.join(corpus_dir, "mixed_slides.pdf"))
doc4.close()

# 5. scan_printed.pdf (Synthetic printed document representing scanned text)
doc5 = fitz.open()
page5 = doc5.new_page(width=595, height=842)
page5.insert_text(fitz.Point(50, 70), "Standard Operating Procedure: Laboratory Inventory", fontsize=16, fontname="helv", color=(0.1, 0.1, 0.1))
page5.insert_text(fitz.Point(50, 110), "1. Purpose and Scope", fontsize=12, fontname="helv")
rect_sop1 = fitz.Rect(50, 120, 545, 170)
page5.insert_textbox(rect_sop1, "This document describes the mandatory verification protocol for hazardous chemical inventories across analytical research facilities.", fontsize=10, fontname="times-roman")

page5.insert_text(fitz.Point(50, 190), "2. Safety Guidelines", fontsize=12, fontname="helv")
page5.insert_text(fitz.Point(60, 215), "- All personnel must wear certified protective eyewear and nitrile gloves.", fontsize=10, fontname="times-roman")
page5.insert_text(fitz.Point(60, 235), "- Spill containment kits must be inspected every thirty days.", fontsize=10, fontname="times-roman")
page5.insert_text(fitz.Point(60, 255), "- Ensure proper ventilation prior to opening solvent containers.", fontsize=10, fontname="times-roman")

page5.insert_text(fitz.Point(50, 290), "3. Incident Notification", fontsize=12, fontname="helv")
rect_sop3 = fitz.Rect(50, 300, 545, 360)
page5.insert_textbox(rect_sop3, "In the event of an accidental discharge or contact with skin, immediately flush the area with copious amounts of water for at least fifteen minutes and notify the environmental safety supervisor.", fontsize=10, fontname="times-roman")

# Rendered document representing printed scan
doc5.save(os.path.join(corpus_dir, "scan_printed.pdf"))
doc5.close()

# 6. scan_handwritten.pdf (Synthetic notes representing handwritten document)
doc6 = fitz.open()
page6 = doc6.new_page(width=595, height=842)
page6.insert_text(fitz.Point(60, 70), "Field Notes: Project Hydrology", fontsize=16, fontname="helv")
page6.insert_text(fitz.Point(60, 100), "Date: October 14", fontsize=11, fontname="times-italic")

page6.insert_text(fitz.Point(70, 130), "- Water temperature at inflow: 14.2 C", fontsize=10, fontname="times-roman")
page6.insert_text(fitz.Point(70, 155), "- Flow rate measured at weir: approximately 42 L/min", fontsize=10, fontname="times-roman")
page6.insert_text(fitz.Point(70, 180), "- Observed minor silt accumulation near intake filter", fontsize=10, fontname="times-roman")

# Margin note box
page6.draw_rect(fitz.Rect(60, 210, 530, 245), color=(0.7, 0.7, 0.7), fill=(0.97, 0.97, 0.95))
page6.insert_text(fitz.Point(70, 230), "Margin note: Remember to bring replacement sampling vials tomorrow morning.", fontsize=10, fontname="times-italic")

page6.insert_text(fitz.Point(60, 275), "Task list:", fontsize=11, fontname="helv")
page6.insert_text(fitz.Point(70, 300), "[x] Calibrate pH sensor (read 7.15)", fontsize=10, fontname="courier")
page6.insert_text(fitz.Point(70, 325), "[ ] Measure dissolved oxygen at lower pool [illegible]", fontsize=10, fontname="courier")
page6.insert_text(fitz.Point(70, 350), "[ ] Deliver samples to central lab by 4 PM", fontsize=10, fontname="courier")

doc6.save(os.path.join(corpus_dir, "scan_handwritten.pdf"))
doc6.close()

print("Generated all 6 corpus PDFs successfully.")

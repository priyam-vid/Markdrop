# User-Provided Handwriting & Real Scan Test Data

This directory is reserved for human-supplied real-world documents, such as physical handwritten notes, whiteboard photos, or complex photocopied/scanned documents.

Per Markdrop's Honesty Rules, synthetic or typed text layers will never be masqueraded as handwritten benchmarks. Until real user files are provided in this directory, all handwriting and on-device vision metrics are explicitly reported as `NOT RUN (no user file)`.

## How to Supply Test Documents

You can add test cases by placing matching pairs of files in this folder:

1. **Input Document**:
   - `handwritten_<name>.pdf` (or `.jpg`, `.png`)
2. **Ground-Truth Reference (One of the following)**:
   - Full Markdown ground-truth: `handwritten_<name>.golden.md`
   - Key phrases list: `handwritten_<name>.must_contain.txt` (one required phrase per line; used to calculate word recall without penalizing minor formatting variations).

## Evaluation

Once files are added, the evaluation suite (`tools/evaluate.py` and `RealPipelineEvaluationHarnessTest`) will automatically detect them, measure recognition recall, and include them in the generated verification report.

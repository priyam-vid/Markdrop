#!/usr/bin/env python3
"""
Unit tests and negative controls for tools/evaluate.py and tools/make_report.py
"""

import os
import subprocess
import sys
import tempfile
import unittest

from evaluate import (
    compute_wer,
    compute_order_score,
    compute_structural_table_f1,
    compute_heading_f1,
    check_math_balance,
    count_glued_words
)
from make_report import generate_markdown_report

class TestEvaluator(unittest.TestCase):

    def test_wer_calculation(self):
        # 1 substitution out of 3 words = 1/3
        wer = compute_wer("a b c", "a b d")
        self.assertAlmostEqual(wer, 1.0 / 3.0, places=5)

    def test_wer_identical(self):
        wer = compute_wer("Hello World", "Hello World")
        self.assertEqual(wer, 0.0)

    def test_wer_completely_different(self):
        wer = compute_wer("apple banana", "cat dog elephant")
        self.assertGreaterEqual(wer, 1.0)

    def test_glued_words_counter(self):
        glued = "NeuralDocumentUnderstandingAndDeepLearningTechniques"
        normal = "Neural Document Understanding"
        self.assertEqual(count_glued_words(glued), 1)
        self.assertEqual(count_glued_words(normal), 0)

    def test_structural_table_f1_identical(self):
        t1 = "| A | B |\n|---|---|\n| 1 | 2 |"
        t2 = "| A | B |\n|---|---|\n| 1 | 2 |"
        f1 = compute_structural_table_f1(t1, t2)
        self.assertEqual(f1, 1.0)

    def test_structural_table_f1_negative_control_scrambled(self):
        # Same cells, but rows or columns are scrambled:
        # Original: (0,0)=A, (0,1)=B, (1,0)=1, (1,1)=2
        # Scrambled: (0,0)=B, (0,1)=A, (1,0)=2, (1,1)=1
        t1 = "| A | B |\n|---|---|\n| 1 | 2 |"
        t2 = "| B | A |\n|---|---|\n| 2 | 1 |"
        f1 = compute_structural_table_f1(t1, t2)
        # Structural coordinate matching MUST fail: f1 should be 0.0, strictly < 1.0!
        self.assertIsNotNone(f1)
        self.assertLess(f1, 1.0)

    def test_structural_table_f1_negative_control_no_tables(self):
        # Two documents with NO tables must return None (evaluator formats as n/a), NEVER 1.00!
        p1 = "This is a document with plain paragraphs."
        p2 = "This is another document with plain paragraphs."
        f1 = compute_structural_table_f1(p1, p2)
        self.assertIsNone(f1)

    def test_order_score_negative_control_swapped_columns(self):
        # Golden: Banner, Left Column sentences, Right Column sentences
        ref = """
        This is the introduction banner statement describing the research.
        Left column sentence one discusses background literature.
        Left column sentence two evaluates initial experimental data.
        Left column sentence three establishes primary conclusions.
        Right column sentence one discusses alternative configurations.
        Right column sentence two measures latency impact.
        Right column sentence three provides future recommendations.
        """
        # Swapped columns hypothesis: Banner, Right Column sentences, Left Column sentences
        hyp = """
        This is the introduction banner statement describing the research.
        Right column sentence one discusses alternative configurations.
        Right column sentence two measures latency impact.
        Right column sentence three provides future recommendations.
        Left column sentence one discusses background literature.
        Left column sentence two evaluates initial experimental data.
        Left column sentence three establishes primary conclusions.
        """
        order_score = compute_order_score(ref, hyp)
        # Swapping columns MUST fail the order score threshold (0.95)
        self.assertLess(order_score, 0.95)

    def test_heading_f1(self):
        h1 = "# Title\n## Section"
        h2 = "# Title\n### Section"
        f1 = compute_heading_f1(h1, h2)
        self.assertAlmostEqual(f1, 0.5, places=2)

    def test_math_balance(self):
        self.assertTrue(check_math_balance("The relation $E=mc^2$ holds."))
        self.assertFalse(check_math_balance("Unbalanced $E=mc^2 formula."))
        self.assertTrue(check_math_balance("$$\n\\int x dx\n$$"))
        self.assertFalse(check_math_balance("$$\n\\int x dx"))

    def test_cli_negative_controls(self):
        # 1. Refuses same directory
        with tempfile.TemporaryDirectory() as tmpdir:
            res = subprocess.run([sys.executable, "tools/evaluate.py", "--outputs", tmpdir, "--golden", tmpdir],
                                 capture_output=True, text=True)
            self.assertEqual(res.returncode, 2)
            self.assertIn("same directory", res.stderr)

        # 2. Missing output exits non-zero
        with tempfile.TemporaryDirectory() as gold_dir, tempfile.TemporaryDirectory() as out_dir:
            thresh_path = os.path.join(gold_dir, "thresholds.json")
            with open(thresh_path, "w") as tf:
                tf.write('{"documents": {"doc1": {"tier": "page", "max_wer": 0.05}}}')
            with open(os.path.join(gold_dir, "doc1.page1.md"), "w") as f:
                f.write("Expected text")
            # outputs directory is empty
            res = subprocess.run([sys.executable, "tools/evaluate.py", "--outputs", out_dir, "--golden", gold_dir, "--thresholds", thresh_path],
                                 capture_output=True, text=True)
            self.assertEqual(res.returncode, 1)

    def test_make_report_integrity(self):
        # Report generator must refuse to print PASS for a failing row
        with tempfile.TemporaryDirectory() as tmpdir:
            results_path = os.path.join(tmpdir, "results.json")
            data = {
                "total": 1,
                "passed": 0,
                "expected_failures": 0,
                "unexpected_failures": 1,
                "results": [
                    {
                        "name": "failing_doc",
                        "tier": "page",
                        "extracted_by": "Test",
                        "wall_clock_ms": 10,
                        "wer": 0.45,
                        "order_score": 0.50,
                        "table_f1": 0.0,
                        "heading_f1": 0.0,
                        "math_balance": "VALID",
                        "glued_words": 0,
                        "verdict": "FAIL",
                        "notes": "Failed: WER > 0.05"
                    }
                ]
            }
            import json
            with open(results_path, "w") as f:
                json.dump(data, f)

            report = generate_markdown_report(results_path, "Test Report")
            self.assertIn("**FAIL**", report)
            self.assertNotIn("**PASS**", report)

if __name__ == "__main__":
    unittest.main()

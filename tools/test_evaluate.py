#!/usr/bin/env python3
"""
Unit tests and negative controls for tools/evaluate.py
"""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

from evaluate import compute_wer, compute_table_cell_f1, compute_heading_f1, check_math_balance, count_glued_words

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
        # Word > 25 chars without punctuation
        glued = "NeuralDocumentUnderstandingAndDeepLearningTechniques"
        normal = "Neural Document Understanding"
        self.assertEqual(count_glued_words(glued), 1)
        self.assertEqual(count_glued_words(normal), 0)

    def test_table_cell_f1(self):
        t1 = "| A | B |\n|---|---|\n| 1 | 2 |"
        t2 = "| A | B |\n|---|---|\n| 1 | 3 |"
        f1 = compute_table_cell_f1(t1, t2)
        # Cells in t1: {a, b, 1, 2}. Cells in t2: {a, b, 1, 3}. Intersection: {a, b, 1} (3).
        # Precision: 3/4, Recall: 3/4, F1: 0.75
        self.assertAlmostEqual(f1, 0.75, places=2)

    def test_heading_f1(self):
        h1 = "# Title\n## Section"
        h2 = "# Title\n### Section"
        f1 = compute_heading_f1(h1, h2)
        # (1, title) matches. (2, section) vs (3, section) mismatch.
        # Precision: 1/2, Recall: 1/2, F1: 0.5
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
            # Create golden file
            with open(os.path.join(gold_dir, "doc1.page1.md"), "w") as f:
                f.write("Expected text")
            # outputs directory is empty
            res = subprocess.run([sys.executable, "tools/evaluate.py", "--outputs", out_dir, "--golden", gold_dir],
                                 capture_output=True, text=True)
            self.assertEqual(res.returncode, 1)
            self.assertIn("Missing expected output files", res.stderr)

        # 3. Output with one word changed has WER > 0
        with tempfile.TemporaryDirectory() as gold_dir, tempfile.TemporaryDirectory() as out_dir:
            with open(os.path.join(gold_dir, "doc1.page1.md"), "w") as f:
                f.write("one two three")
            with open(os.path.join(out_dir, "doc1.md"), "w") as f:
                f.write("one two four")
            res = subprocess.run([sys.executable, "tools/evaluate.py", "--outputs", out_dir, "--golden", gold_dir],
                                 capture_output=True, text=True)
            self.assertEqual(res.returncode, 0)
            self.assertIn("33.33%", res.stdout)

if __name__ == "__main__":
    unittest.main()

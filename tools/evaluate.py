#!/usr/bin/env python3
"""
Markdrop Evaluation Tool (Round 2)
Evaluates real pipeline outputs against golden ground-truth files.
Strict honesty rules:
- Must read actual output files; exits non-zero if any output is missing or empty.
- Refuses to compare a directory to itself.
- Computes measured WER, Table Cell F1, Heading F1, Math Balance, and Glued Word count.
"""

import argparse
import os
import re
import sys
from typing import Dict, List, Set, Tuple

def levenshtein_distance(s1: List[str], s2: List[str]) -> int:
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)
    previous_row = list(range(len(s2) + 1))
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    return previous_row[-1]

def tokenize_words(text: str) -> List[str]:
    # Normalizes markdown formatting and extracts word tokens
    cleaned = re.sub(r'[*_#`~]', ' ', text)
    tokens = re.findall(r'\b\w+\b', cleaned.lower())
    return tokens

def compute_wer(ref_text: str, hyp_text: str) -> float:
    ref_words = tokenize_words(ref_text)
    hyp_words = tokenize_words(hyp_text)
    if not ref_words:
        return 0.0 if not hyp_words else 1.0
    dist = levenshtein_distance(ref_words, hyp_words)
    return dist / len(ref_words)

def extract_table_cells(markdown: str) -> Set[str]:
    cells = set()
    for line in markdown.splitlines():
        trimmed = line.strip()
        if trimmed.startswith('|') and trimmed.endswith('|'):
            parts = [c.strip() for c in trimmed.split('|')[1:-1]]
            if all(re.match(r'^:?-+:?$', p) for p in parts):
                continue
            for cell in parts:
                if cell:
                    cells.add(cell.lower())
    return cells

def compute_table_cell_f1(ref_text: str, hyp_text: str) -> float:
    ref_cells = extract_table_cells(ref_text)
    hyp_cells = extract_table_cells(hyp_text)
    if not ref_cells and not hyp_cells:
        return 1.0
    if not ref_cells or not hyp_cells:
        return 0.0
    tp = len(ref_cells.intersection(hyp_cells))
    fp = len(hyp_cells - ref_cells)
    fn = len(ref_cells - hyp_cells)
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    if precision + recall == 0:
        return 0.0
    return 2 * (precision * recall) / (precision + recall)

def extract_headings(markdown: str) -> Set[Tuple[int, str]]:
    headings = set()
    for line in markdown.splitlines():
        match = re.match(r'^(#{1,6})\s+(.+)$', line.strip())
        if match:
            level = len(match.group(1))
            text = match.group(2).strip().lower()
            headings.add((level, text))
    return headings

def compute_heading_f1(ref_text: str, hyp_text: str) -> float:
    ref_headings = extract_headings(ref_text)
    hyp_headings = extract_headings(hyp_text)
    if not ref_headings and not hyp_headings:
        return 1.0
    if not ref_headings or not hyp_headings:
        return 0.0
    tp = len(ref_headings.intersection(hyp_headings))
    fp = len(hyp_headings - ref_headings)
    fn = len(ref_headings - hyp_headings)
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    if precision + recall == 0:
        return 0.0
    return 2 * (precision * recall) / (precision + recall)

def check_math_balance(markdown: str) -> bool:
    # Delimiter counts: inline $ and block $$
    block_matches = len(re.findall(r'\$\$', markdown))
    # Replace block $$ with space to count lone $
    no_block = re.sub(r'\$\$', ' ', markdown)
    inline_matches = len(re.findall(r'(?<!\$)\$(?!\$)', no_block))
    return (block_matches % 2 == 0) and (inline_matches % 2 == 0)

def count_glued_words(markdown: str) -> int:
    # Count tokens > 25 characters without whitespace or punctuation (E1 canary)
    tokens = re.split(r'\s+', markdown)
    glued_count = 0
    for tok in tokens:
        # Strip markdown syntax symbols
        pure = re.sub(r'^[#*_\-\|`>]+|[#*_\-\|`>]+$', '', tok)
        # If pure alphabetic/alphanumeric > 25 characters without internal punctuation
        if len(pure) > 25 and re.match(r'^[A-Za-z0-9]+$', pure):
            glued_count += 1
    return glued_count

def evaluate_files(outputs_dir: str, golden_dir: str) -> List[Dict]:
    abs_outputs = os.path.abspath(outputs_dir)
    abs_golden = os.path.abspath(golden_dir)

    if abs_outputs == abs_golden:
        print(f"ERROR: --outputs and --golden resolve to the same directory: {abs_outputs}", file=sys.stderr)
        sys.exit(2)

    if not os.path.isdir(abs_golden):
        print(f"ERROR: Golden directory not found: {abs_golden}", file=sys.stderr)
        sys.exit(2)

    if not os.path.isdir(abs_outputs):
        print(f"ERROR: Outputs directory not found: {abs_outputs}", file=sys.stderr)
        sys.exit(2)

    golden_files = sorted([f for f in os.listdir(abs_golden) if f.endswith('.page1.md')])
    if not golden_files:
        print(f"ERROR: No *.page1.md files found in golden directory: {abs_golden}", file=sys.stderr)
        sys.exit(2)

    results = []
    missing_files = []
    empty_files = []

    for g_file in golden_files:
        base_name = g_file[:-len('.page1.md')]
        # Output file name: <base_name>.md (or <base_name>.page1.md)
        hyp_candidates = [
            os.path.join(abs_outputs, f"{base_name}.md"),
            os.path.join(abs_outputs, f"{base_name}.page1.md")
        ]
        hyp_path = next((p for p in hyp_candidates if os.path.isfile(p)), None)

        if not hyp_path:
            missing_files.append(f"{base_name}.md")
            continue

        with open(os.path.join(abs_golden, g_file), 'r', encoding='utf-8') as f:
            ref_content = f.read()

        with open(hyp_path, 'r', encoding='utf-8') as f:
            hyp_content = f.read()

        if not hyp_content.strip():
            empty_files.append(hyp_path)
            continue

        wer = compute_wer(ref_content, hyp_content)
        table_f1 = compute_table_cell_f1(ref_content, hyp_content)
        heading_f1 = compute_heading_f1(ref_content, hyp_content)
        math_valid = check_math_balance(hyp_content)
        glued_words = count_glued_words(hyp_content)

        results.append({
            "name": base_name,
            "wer": wer,
            "table_f1": table_f1,
            "heading_f1": heading_f1,
            "math_balance": "VALID" if math_valid else "INVALID",
            "glued_words": glued_words,
            "hyp_path": hyp_path
        })

    if missing_files or empty_files:
        if missing_files:
            print("ERROR: Missing expected output files for evaluation:", file=sys.stderr)
            for m in missing_files:
                print(f"  - {m}", file=sys.stderr)
        if empty_files:
            print("ERROR: Output files are empty:", file=sys.stderr)
            for e in empty_files:
                print(f"  - {e}", file=sys.stderr)
        sys.exit(1)

    return results

def print_report(results: List[Dict]):
    header = f"{'Document':<30} | {'WER':<8} | {'Table F1':<10} | {'Heading F1':<10} | {'Math Bal':<8} | {'Glued >25'}"
    print("\n" + "=" * len(header))
    print("MARKDROP PIPELINE EVALUATION MEASUREMENTS (ROUND 2)")
    print("=" * len(header))
    print(header)
    print("-" * len(header))
    for r in results:
        print(f"{r['name']:<30} | {r['wer']:>7.2%} | {r['table_f1']:>10.2f} | {r['heading_f1']:>10.2f} | {r['math_balance']:^8} | {r['glued_words']:>9}")
    print("=" * len(header) + "\n")

def main():
    parser = argparse.ArgumentParser(description="Evaluate Markdrop pipeline outputs against golden references.")
    parser.add_argument("--outputs", required=True, help="Path to directory containing real pipeline output .md files")
    parser.add_argument("--golden", required=True, help="Path to directory containing golden .page1.md files")
    args = parser.parse_args()

    results = evaluate_files(args.outputs, args.golden)
    print_report(results)

if __name__ == "__main__":
    main()

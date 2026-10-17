#!/usr/bin/env python3
"""
Markdrop Strict Mechanical Pipeline Evaluator (Round 3)

Calculates objective metrics and produces mechanical PASS / FAIL / n/a / NOT RUN verdicts
strictly governed by pre-committed thresholds in testdata/corpus/thresholds.json.

Honesty Rules:
- Verdicts are never prose; they come exclusively from threshold checks in this script.
- Table F1 is structural: compares (table_idx, row_idx, col_idx, cell_text) triples.
- Missing tables return None (printed as 'n/a'), NEVER 1.00.
- Sentence-level LCS reading order score exposes column-swapping / interleaving.
- Results are saved to build/eval/results.json for report generation.
- Exits non-zero if an unexpected failure occurs OR if an expected failure unexpectedly passes.
"""

import argparse
import json
import os
import re
import sys
from typing import Any, Dict, List, Optional, Set, Tuple

def normalize_text_line_endings(text: str) -> str:
    return text.replace('\r\n', '\n').replace('\r', '\n')

def tokenize_words(text: str) -> List[str]:
    cleaned = re.sub(r'[*_#`~|>]', ' ', normalize_text_line_endings(text))
    tokens = re.findall(r'\b\w+\b', cleaned.lower())
    return tokens

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

def compute_wer(ref_text: str, hyp_text: str) -> float:
    ref_words = tokenize_words(ref_text)
    hyp_words = tokenize_words(hyp_text)
    if not ref_words:
        return 0.0 if not hyp_words else 1.0
    dist = levenshtein_distance(ref_words, hyp_words)
    return dist / len(ref_words)

def extract_sentences(text: str) -> List[str]:
    normalized = normalize_text_line_endings(text)
    # Remove markdown block/line formatting characters
    cleaned = re.sub(r'^[#*_\->|]+\s*', '', normalized, flags=re.MULTILINE)
    # Remove inline emphasis markers (*, _, `) so formatting doesn't perturb sentence LCS
    cleaned = re.sub(r'[*_`]', '', cleaned)
    # Split by punctuation or newlines
    raw_sentences = re.split(r'[.\n?!]+', cleaned)
    sentences = []
    for s in raw_sentences:
        clean_s = re.sub(r'\s+', ' ', s).strip().lower()
        # Keep non-trivial sentences (at least 3 words)
        if len(clean_s.split()) >= 3:
            sentences.append(clean_s)
    return sentences

def compute_order_score(ref_text: str, hyp_text: str) -> float:
    ref_s = extract_sentences(ref_text)
    hyp_s = extract_sentences(hyp_text)
    if not ref_s:
        return 1.0

    # Map each sentence to its canonical representation
    # Compute Longest Common Subsequence of matching sentences
    m, n = len(ref_s), len(hyp_s)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(m):
        for j in range(n):
            if ref_s[i] == hyp_s[j]:
                dp[i + 1][j + 1] = dp[i][j] + 1
            else:
                dp[i + 1][j + 1] = max(dp[i + 1][j], dp[i][j + 1])
    lcs_len = dp[m][n]
    return lcs_len / float(m)

def parse_markdown_tables(text: str) -> List[List[List[str]]]:
    tables = []
    current_table = []
    lines = normalize_text_line_endings(text).splitlines()
    for line in lines:
        trimmed = line.strip()
        if trimmed.startswith('|') and trimmed.endswith('|'):
            cells = [re.sub(r'\s+', ' ', c).strip().lower() for c in trimmed.split('|')[1:-1]]
            # Skip delimiter separator rows (|---|---|)
            if all(re.match(r'^:?-+:?$', c) for c in cells):
                continue
            current_table.append(cells)
        else:
            if current_table:
                tables.append(current_table)
                current_table = []
    if current_table:
        tables.append(current_table)
    return tables

def compute_structural_table_f1(ref_text: str, hyp_text: str) -> Optional[float]:
    ref_tables = parse_markdown_tables(ref_text)
    hyp_tables = parse_markdown_tables(hyp_text)

    if not ref_tables and not hyp_tables:
        return None  # Neither side has a table -> return n/a

    if not ref_tables or not hyp_tables:
        return 0.0

    # Build set of structural cell coordinates: (table_idx, row_idx, col_idx, cell_text)
    ref_triples = set()
    for t_idx, tbl in enumerate(ref_tables):
        for r_idx, row in enumerate(tbl):
            for c_idx, cell in enumerate(row):
                ref_triples.add((t_idx, r_idx, c_idx, cell))

    hyp_triples = set()
    for t_idx, tbl in enumerate(hyp_tables):
        for r_idx, row in enumerate(tbl):
            for c_idx, cell in enumerate(row):
                hyp_triples.add((t_idx, r_idx, c_idx, cell))

    tp = len(ref_triples.intersection(hyp_triples))
    fp = len(hyp_triples - ref_triples)
    fn = len(ref_triples - hyp_triples)

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    if precision + recall == 0:
        return 0.0
    return 2.0 * (precision * recall) / (precision + recall)

def extract_headings(text: str) -> Set[Tuple[int, str]]:
    headings = set()
    for line in normalize_text_line_endings(text).splitlines():
        match = re.match(r'^(#{1,6})\s+(.+)$', line.strip())
        if match:
            level = len(match.group(1))
            h_text = re.sub(r'\s+', ' ', match.group(2)).strip().lower()
            headings.add((level, h_text))
    return headings

def compute_heading_f1(ref_text: str, hyp_text: str) -> Optional[float]:
    ref_headings = extract_headings(ref_text)
    hyp_headings = extract_headings(hyp_text)
    if not ref_headings and not hyp_headings:
        return None
    if not ref_headings or not hyp_headings:
        return 0.0
    tp = len(ref_headings.intersection(hyp_headings))
    fp = len(hyp_headings - ref_headings)
    fn = len(ref_headings - hyp_headings)
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    if precision + recall == 0:
        return 0.0
    return 2.0 * (precision * recall) / (precision + recall)

def check_math_balance(text: str) -> bool:
    norm = normalize_text_line_endings(text)
    block_matches = len(re.findall(r'\$\$', norm))
    no_block = re.sub(r'\$\$', ' ', norm)
    inline_matches = len(re.findall(r'(?<!\$)\$(?!\$)', no_block))
    return (block_matches % 2 == 0) and (inline_matches % 2 == 0)

def count_glued_words(text: str) -> int:
    tokens = re.split(r'\s+', normalize_text_line_endings(text))
    glued_count = 0
    for tok in tokens:
        pure = re.sub(r'^[#*_\-\|`>]+|[#*_\-\|`>]+$', '', tok)
        if len(pure) > 25 and re.match(r'^[A-Za-z0-9]+$', pure):
            glued_count += 1
    return glued_count

def evaluate_corpus(outputs_dir: str, golden_dir: str, thresholds_path: str) -> Dict[str, Any]:
    abs_outputs = os.path.abspath(outputs_dir)
    abs_golden = os.path.abspath(golden_dir)

    if abs_outputs == abs_golden:
        print(f"ERROR: --outputs and --golden resolve to the same directory: {abs_outputs}", file=sys.stderr)
        sys.exit(2)

    with open(thresholds_path, 'r', encoding='utf-8') as f:
        thresholds_cfg = json.load(f)

    expected_failures: Dict[str, str] = thresholds_cfg.get("expectedFailures", {})
    doc_specs: Dict[str, Dict[str, Any]] = thresholds_cfg.get("documents", {})

    results = []
    unexpected_failures = []
    unexpected_passes = []

    for name, spec in sorted(doc_specs.items()):
        # Handle NOT RUN specifications
        if "not_run" in spec:
            results.append({
                "name": name,
                "tier": spec.get("tier", "page"),
                "extracted_by": "None",
                "wall_clock_ms": 0,
                "wer": None,
                "order_score": None,
                "table_f1": None,
                "heading_f1": None,
                "math_balance": "n/a",
                "glued_words": 0,
                "verdict": f"NOT RUN ({spec['not_run']})",
                "notes": spec["not_run"]
            })
            continue

        tier = spec.get("tier", "page")
        # Locate golden file
        golden_file_candidates = []
        if tier == "page":
            golden_file_candidates.extend([
                os.path.join(abs_golden, f"{name}.page1.md"),
                os.path.join(abs_golden, "article_banner_matrix", f"{name}.page1.md")
            ])
        else:
            golden_file_candidates.extend([
                os.path.join(abs_golden, f"{name}.doc.md"),
                os.path.join(abs_golden, "article_banner_matrix", f"{name}.doc.md"),
                os.path.join(abs_golden, f"{name}.page1.md")
            ])
        golden_path = next((p for p in golden_file_candidates if os.path.isfile(p)), None)

        if not golden_path:
            print(f"ERROR: Golden file not found for document '{name}'", file=sys.stderr)
            sys.exit(1)

        # Locate output file in outputs_dir
        hyp_candidates = [
            os.path.join(abs_outputs, f"{name}.md"),
            os.path.join(abs_outputs, f"{name}.doc.md"),
            os.path.join(abs_outputs, f"{name}.page1.md")
        ]
        hyp_path = next((p for p in hyp_candidates if os.path.isfile(p)), None)
        if not hyp_path:
            print(f"ERROR: Missing output file for '{name}' in {abs_outputs}", file=sys.stderr)
            sys.exit(1)

        with open(golden_path, 'r', encoding='utf-8') as f:
            ref_content = f.read()

        with open(hyp_path, 'r', encoding='utf-8') as f:
            hyp_content = f.read()

        if not hyp_content.strip():
            print(f"ERROR: Output file is empty: {hyp_path}", file=sys.stderr)
            sys.exit(1)

        # Trace metadata if available
        trace_path = os.path.join(abs_outputs, f"{name}.trace.json")
        wall_clock = 0
        extracted_by = "TextLayerSession"
        if os.path.isfile(trace_path):
            try:
                with open(trace_path, 'r', encoding='utf-8') as tf:
                    tdata = json.load(tf)
                    wall_clock = tdata.get("durationMs", 0)
                    extracted_by = tdata.get("engineUsed", extracted_by)
            except Exception:
                pass

        # Compute all metrics
        wer = compute_wer(ref_content, hyp_content)
        order_score = compute_order_score(ref_content, hyp_content)
        table_f1 = compute_structural_table_f1(ref_content, hyp_content)
        heading_f1 = compute_heading_f1(ref_content, hyp_content)
        math_valid = check_math_balance(hyp_content)
        glued_count = count_glued_words(hyp_content)

        # Check threshold failures
        failures = []
        max_wer = spec.get("max_wer")
        if max_wer is not None and wer > max_wer:
            failures.append(f"WER {wer:.2%} > {max_wer:.2%}")

        min_order = spec.get("min_order_score")
        if min_order is not None and order_score < min_order:
            failures.append(f"Order {order_score:.2f} < {min_order:.2f}")

        min_table = spec.get("min_table_f1")
        if min_table is not None:
            if table_f1 is None:
                failures.append("Expected table but none detected")
            elif table_f1 < min_table:
                failures.append(f"Table F1 {table_f1:.2f} < {min_table:.2f}")

        min_heading = spec.get("min_heading_f1")
        if min_heading is not None:
            if heading_f1 is None:
                failures.append("Expected headings but none detected")
            elif heading_f1 < min_heading:
                failures.append(f"Heading F1 {heading_f1:.2f} < {min_heading:.2f}")

        max_glued = spec.get("max_glued", 0)
        if glued_count > max_glued:
            failures.append(f"Glued words {glued_count} > {max_glued}")

        if not math_valid:
            failures.append("Unbalanced math delimiters")

        # Custom constraints
        must_keep_headings = spec.get("must_keep_headings", [])
        hyp_headings_text = {h[1] for h in extract_headings(hyp_content)}
        for req_h in must_keep_headings:
            if req_h.lower() not in hyp_headings_text:
                failures.append(f"Missing required heading: '{req_h}'")

        must_not_contain = spec.get("must_not_contain", [])
        for forbidden in must_not_contain:
            if forbidden.lower() in hyp_content.lower():
                failures.append(f"Contains forbidden text: '{forbidden}'")

        expect_markers = spec.get("expect_markers", [])
        if expect_markers:
            found_markers = [int(m) for m in re.findall(r'<!--\s*page:\s*(\d+)\s*-->', hyp_content)]
            if found_markers != expect_markers:
                failures.append(f"Markers {found_markers} != expected {expect_markers}")

        passed = len(failures) == 0
        is_expected_failure = name in expected_failures

        if passed:
            if is_expected_failure:
                unexpected_passes.append(name)
            verdict = "PASS"
            notes = "Thresholds satisfied"
        else:
            if is_expected_failure:
                verdict = "EXPECTED_FAIL"
                notes = f"{expected_failures[name]} (Failed: {'; '.join(failures)})"
            else:
                verdict = "FAIL"
                notes = f"Failed: {'; '.join(failures)}"
                unexpected_failures.append((name, failures))

        results.append({
            "name": name,
            "tier": tier,
            "extracted_by": extracted_by,
            "wall_clock_ms": wall_clock,
            "wer": wer,
            "order_score": order_score,
            "table_f1": table_f1,
            "heading_f1": heading_f1,
            "math_balance": "VALID" if math_valid else "INVALID",
            "glued_words": glued_count,
            "verdict": verdict,
            "notes": notes
        })

    summary = {
        "total": len(results),
        "passed": sum(1 for r in results if r["verdict"] == "PASS"),
        "expected_failures": sum(1 for r in results if r["verdict"] == "EXPECTED_FAIL"),
        "unexpected_failures": len(unexpected_failures),
        "unexpected_passes": len(unexpected_passes),
        "not_run": sum(1 for r in results if r["verdict"].startswith("NOT RUN")),
        "results": results
    }

    # Save results.json
    results_json_path = os.path.join(abs_outputs, "results.json")
    with open(results_json_path, 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2)

    return summary

def print_evaluation_table(summary: Dict[str, Any]):
    results = summary["results"]
    header = f"{'Document':<35} | {'Tier':<8} | {'WER':<8} | {'Order':<7} | {'Table F1':<10} | {'Heading':<10} | {'Math':<7} | {'Glued':<5} | {'Verdict'}"
    print("\n" + "=" * len(header))
    print("MARKDROP MECHANICAL PIPELINE EVALUATION VERDICTS (ROUND 3)")
    print("=" * len(header))
    print(header)
    print("-" * len(header))
    for r in results:
        w_str = f"{r['wer']:>7.2%}" if r['wer'] is not None else "   n/a  "
        o_str = f"{r['order_score']:>6.2f} " if r['order_score'] is not None else "  n/a  "
        t_str = f"{r['table_f1']:>9.2f} " if r['table_f1'] is not None else "   n/a    "
        h_str = f"{r['heading_f1']:>9.2f} " if r['heading_f1'] is not None else "   n/a    "
        g_str = f"{r['glued_words']:>5}" if r['wer'] is not None else " n/a "
        print(f"{r['name']:<35} | {r['tier']:<8} | {w_str} | {o_str} | {t_str} | {h_str} | {r['math_balance']:^7} | {g_str} | {r['verdict']}")
    print("=" * len(header) + "\n")

    print(f"Summary: {summary['passed']} PASS, {summary['expected_failures']} EXPECTED_FAIL, {summary['unexpected_failures']} UNEXPECTED_FAIL, {summary['not_run']} NOT RUN")

def main():
    parser = argparse.ArgumentParser(description="Mechanical evaluation of Markdrop pipeline outputs against golden references.")
    parser.add_argument("--outputs", required=True, help="Path to directory containing real pipeline output .md files")
    parser.add_argument("--golden", required=True, help="Path to directory containing golden .page1.md and .doc.md files")
    parser.add_argument("--thresholds", default="testdata/corpus/thresholds.json", help="Path to thresholds.json")
    args = parser.parse_args()

    summary = evaluate_corpus(args.outputs, args.golden, args.thresholds)
    print_evaluation_table(summary)

    if summary["unexpected_passes"] > 0:
        print(f"\nWARNING: Documents in expectedFailures passed unexpectedly: {summary['unexpected_passes']}. Update thresholds.json!", file=sys.stderr)
        sys.exit(3)

    if summary["unexpected_failures"] > 0:
        print(f"\nFAILED: {summary['unexpected_failures']} unexpected failure(s) occurred!", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()

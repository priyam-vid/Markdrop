#!/usr/bin/env python3
"""
Generates Markdrop Evaluation Reports exclusively from build/eval/results.json.

Honesty Rules:
- Never hand-write or estimate metrics.
- Every metric, duration, and verdict is extracted directly from the mechanical evaluator JSON output.
- All computed columns (including Heading F1, Math Balance, and Reading Order) must be shown.
"""

import argparse
import json
import os
import sys

def generate_markdown_report(results_json_path: str, title: str) -> str:
    with open(results_json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    results = data.get("results", [])
    total = data.get("total", len(results))
    passed = data.get("passed", 0)
    exp_fail = data.get("expected_failures", 0)
    unexp_fail = data.get("unexpected_failures", 0)
    not_run = data.get("not_run", 0)

    lines = []
    lines.append(f"# {title}")
    lines.append("")
    lines.append("Status: Machine-Generated from `tools/evaluate.py` evaluation run.")
    lines.append(f"Summary: **{passed}** PASS | **{exp_fail}** EXPECTED FAIL | **{unexp_fail}** UNEXPECTED FAIL | **{not_run}** NOT RUN")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("## Objective Evaluation Results Table")
    lines.append("")
    lines.append("| Document | Tier | Extracted By | Wall-Clock | WER | Order Score | Table F1 | Heading F1 | Math Balance | Glued (>25 chars) | Verdict | Notes |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|")

    for r in results:
        name = r["name"]
        tier = r["tier"]
        ext_by = r["extracted_by"]
        clock = f"{r['wall_clock_ms']}ms" if r["wall_clock_ms"] > 0 else "0ms"
        wer = f"{r['wer']:.2%}" if r["wer"] is not None else "n/a"
        order = f"{r['order_score']:.2f}" if r["order_score"] is not None else "n/a"
        tbl = f"{r['table_f1']:.2f}" if r["table_f1"] is not None else "n/a"
        hdg = f"{r['heading_f1']:.2f}" if r["heading_f1"] is not None else "n/a"
        math_bal = r["math_balance"]
        glued = str(r["glued_words"]) if r["wer"] is not None else "n/a"
        verdict = f"**{r['verdict']}**"
        notes = r["notes"].replace("|", "\\|")
        lines.append(f"| `{name}` | {tier} | {ext_by} | {clock} | {wer} | {order} | {tbl} | {hdg} | {math_bal} | {glued} | {verdict} | {notes} |")

    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("### Metric Definitions")
    lines.append("- **WER**: Word Error Rate on normalized token sequences vs golden.")
    lines.append("- **Order Score**: Sentence-level Longest Common Subsequence (LCS) ratio over normalized sentences.")
    lines.append("- **Table F1**: Structural cell alignment F1 matching `(table_idx, row_idx, col_idx, text)` triples. Evaluated as `n/a` when neither document contains tables.")
    lines.append("- **Heading F1**: Precision/Recall F1 over heading level and title text.")
    lines.append("- **Math Balance**: Delimiter parity verification for `$...$` and `$$...$$`.")
    lines.append("- **Glued (>25 chars)**: Count of continuous unpunctuated tokens exceeding 25 characters without space.")
    lines.append("")

    return "\n".join(lines)

def main():
    parser = argparse.ArgumentParser(description="Generate Markdown evaluation report from results.json")
    parser.add_argument("--input", default="build/eval/results.json", help="Path to results.json")
    parser.add_argument("--output", help="Optional path to output markdown file")
    parser.add_argument("--title", default="MARKDROP MECHANICAL VERIFICATION REPORT", help="Report Title")
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        print(f"ERROR: Results JSON not found at {args.input}", file=sys.stderr)
        sys.exit(1)

    md = generate_markdown_report(args.input, args.title)
    if args.output:
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8", newline="\n") as f:
            f.write(md + "\n")
        print(f"Report generated successfully: {args.output}")
    else:
        print(md)

if __name__ == "__main__":
    main()

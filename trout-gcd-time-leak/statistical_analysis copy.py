"""Statistical analysis of timing results."""

import json
import os
import statistics
from collections import defaultdict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_DIR = os.path.join(BASE_DIR, "results")
INPUT_FILE = os.path.join(RESULTS_DIR, "timing_results.json")
REPORT_FILE = os.path.join(RESULTS_DIR, "statistical_report.txt")


def mean(xs):
    return statistics.mean(xs) if xs else 0.0


def stdev(xs):
    return statistics.stdev(xs) if len(xs) > 1 else 0.0


def corr(xs, ys):
    if len(xs) < 2:
        return 0.0
    mx, my = mean(xs), mean(ys)
    sx = sum((x - mx) ** 2 for x in xs)
    sy = sum((y - my) ** 2 for y in ys)
    if sx == 0 or sy == 0:
        return 0.0
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / (sx * sy) ** 0.5


def main():
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        records = json.load(f)

    grouped = defaultdict(list)
    for r in records:
        grouped[(r["bits"], r["algorithm"])].append(r)

    lines = []
    lines.append("GCD Timing Statistical Report")
    lines.append("=" * 80)
    lines.append("")
    lines.append("Input model: ECC-inspired GCD(d, p), where d is a point-operation denominator and p is a prime modulus.")
    lines.append("Operand sizes: 256-bit, 512-bit, and 1024-bit.")
    lines.append("")
    lines.append("Mean timing and step summary")
    lines.append("-" * 80)
    lines.append(f"{'Bits':>8}  {'Algorithm':<24} {'Mean ns':>14} {'Std ns':>14} {'Mean steps':>14} {'Corr(steps,time)':>18}")

    baseline = {}
    for bits in sorted({r["bits"] for r in records}):
        eu = [r["time_ns"] for r in records if r["bits"] == bits and r["algorithm"] == "euclidean"]
        baseline[bits] = mean(eu)
        for algo in ["euclidean", "binary", "fixed_iteration_binary"]:
            rows = grouped[(bits, algo)]
            times = [r["time_ns"] for r in rows]
            steps = [r["steps"] for r in rows]
            lines.append(f"{bits:8d}  {algo:<24} {mean(times):14.0f} {stdev(times):14.0f} {mean(steps):14.1f} {corr(steps, times):18.3f}")

    lines.append("")
    lines.append("Relative overhead vs Euclidean GCD")
    lines.append("-" * 80)
    lines.append(f"{'Bits':>8}  {'Euclidean':>12} {'Binary':>12} {'Fixed-Iteration':>18}")
    for bits in sorted(baseline):
        row = []
        for algo in ["euclidean", "binary", "fixed_iteration_binary"]:
            values = [r["time_ns"] for r in records if r["bits"] == bits and r["algorithm"] == algo]
            row.append(mean(values) / baseline[bits] if baseline[bits] else 0.0)
        lines.append(f"{bits:8d}  {row[0]:12.1f} {row[1]:12.1f} {row[2]:18.1f}")

    lines.append("")
    lines.append("Interpretation")
    lines.append("-" * 80)
    lines.append("The Euclidean and Binary GCD algorithms use input-dependent loop termination, so their step counts and timings vary with the operands.")
    lines.append("The Fixed-Iteration Binary GCD candidate uses a fixed loop budget of 2 * bit_length steps, reducing direct iteration-count leakage.")
    lines.append("This comes with additional computational overhead and should be treated as an educational candidate, not a production constant-time implementation.")

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print("\n".join(lines))
    print(f"\nSaved: {REPORT_FILE}")


if __name__ == "__main__":
    main()

"""Generate timing plots."""

import json
import os
from collections import defaultdict

import matplotlib.pyplot as plt

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_DIR = os.path.join(BASE_DIR, "results")
PLOTS_DIR = os.path.join(BASE_DIR, "plots")
os.makedirs(PLOTS_DIR, exist_ok=True)

INPUT_FILE = os.path.join(RESULTS_DIR, "timing_results.json")


def mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


def main():
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        records = json.load(f)

    algorithms = ["euclidean", "binary", "fixed_iteration_binary"]
    bits_values = sorted({r["bits"] for r in records})

    # Plot 1: timing histograms, one separate figure
    plt.figure(figsize=(10, 6))
    for algo in algorithms:
        vals = [r["time_ns"] / 1000 for r in records if r["algorithm"] == algo]
        plt.hist(vals, bins=20, alpha=0.45, label=algo)
    plt.xlabel("Time (microseconds)")
    plt.ylabel("Frequency")
    plt.title("Timing Histograms for GCD Algorithms")
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(PLOTS_DIR, "01_timing_histograms.png"), dpi=180)
    plt.close()

    # Plot 2: step count vs execution time
    plt.figure(figsize=(10, 6))
    for algo in algorithms:
        xs = [r["steps"] for r in records if r["algorithm"] == algo]
        ys = [r["time_ns"] / 1000 for r in records if r["algorithm"] == algo]
        plt.scatter(xs, ys, s=22, label=algo, alpha=0.7)
    plt.xlabel("Step count")
    plt.ylabel("Time (microseconds)")
    plt.title("Correlation Between Step Count and Execution Time")
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(PLOTS_DIR, "02_correlation_scatter.png"), dpi=180)
    plt.close()

    # Plot 3: overhead comparison
    x = list(range(len(bits_values)))
    width = 0.25
    baseline = {}
    for bits in bits_values:
        eu = [r["time_ns"] for r in records if r["bits"] == bits and r["algorithm"] == "euclidean"]
        baseline[bits] = mean(eu)

    plt.figure(figsize=(10, 6))
    for idx, algo in enumerate(algorithms):
        overheads = []
        for bits in bits_values:
            vals = [r["time_ns"] for r in records if r["bits"] == bits and r["algorithm"] == algo]
            overheads.append(mean(vals) / baseline[bits] if baseline[bits] else 0)
        positions = [i + (idx - 1) * width for i in x]
        plt.bar(positions, overheads, width=width, label=algo)
    plt.xticks(x, [str(b) for b in bits_values])
    plt.xlabel("Operand size (bits)")
    plt.ylabel("Relative overhead vs Euclidean")
    plt.title("Performance Overhead Comparison")
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(PLOTS_DIR, "03_overhead_comparison.png"), dpi=180)
    plt.close()

    print(f"Saved plots to {PLOTS_DIR}")


if __name__ == "__main__":
    main()

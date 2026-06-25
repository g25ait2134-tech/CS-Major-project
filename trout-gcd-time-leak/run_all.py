"""
run_all.py
Runs the complete experimental pipeline:
  0. Context reference
  1. Correctness tests
  2. Timing experiment using ECC-inspired 256/512/1024-bit inputs
  3. Statistical analysis
  4. Visualization

Usage:
    python run_all.py
"""

from pathlib import Path
import subprocess
import sys

BASE_DIR = Path(__file__).resolve().parent
SRC = BASE_DIR / "src"
TESTS = BASE_DIR / "tests"


def run(label, cmd):
    print("\n" + "#" * 100)
    print(f"# {label}")
    print("#" * 100)
    print("Running:", " ".join(cmd))
    result = subprocess.run(cmd, cwd=BASE_DIR, check=False)
    if result.returncode != 0:
        print(f"\n[FAILED] {label} exited with code {result.returncode}")
        sys.exit(result.returncode)


if __name__ == "__main__":
    run("STEP 0/5: Context Reference — WHERE GCD APPEARS", [sys.executable, str(SRC / "paper_citations.py")])
    run("STEP 1/5: Correctness Tests", [sys.executable, str(TESTS / "test_correctness.py")])
    run("STEP 2/5: Timing Experiment", [sys.executable, str(SRC / "timing_harness.py")])
    run("STEP 3/5: Statistical Analysis", [sys.executable, str(SRC / "statistical_analysis.py")])
    run("STEP 4/5: Generating Plots", [sys.executable, str(SRC / "visualize.py")])

    print("\n" + "=" * 100)
    print("PIPELINE COMPLETE")
    print("=" * 100)
    print("Results:  results/timing_results.json")
    print("          results/statistical_report.txt")
    print("Plots:    plots/01_timing_histograms.png")
    print("          plots/02_correlation_scatter.png")
    print("          plots/03_overhead_comparison.png")
    print("=" * 100)

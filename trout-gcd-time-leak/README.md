# trout-gcd-time-leak

**Fixed-Iteration Binary GCD — Timing Side-Channel Study**

An implementation-level study of timing leakage in GCD algorithms used during modular inversion in Elliptic Curve Cryptography (ECC), motivated by the Trout threshold ECDSA protocol (CCS 2025).

> **Note:** This is an educational proof-of-concept, not a production constant-time cryptographic implementation. Python itself is not constant-time at the hardware level.

---

## Table of Contents

- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Running the Project](#running-the-project)
- [Running Individual Steps](#running-individual-steps)
- [Outputs](#outputs)
- [What Each Module Does](#what-each-module-does)
- [Algorithms Compared](#algorithms-compared)
- [Key Results](#key-results)
- [Limitations](#limitations)

---

## Project Structure

```
trout-gcd-time-leak/
├── run_all.py                  # Full pipeline runner
├── README.md
├── requirements.txt
├── src/
│   ├── variable_gcd.py         # Leaky Euclidean GCD (baseline)
│   ├── binary_gcd.py           # Leaky Binary GCD (second baseline)
│   ├── constant_time_gcd.py    # Fixed-iteration GCD (proposed fix)
│   ├── crypto_inputs.py        # ECC-inspired 256/512/1024-bit operand generator
│   ├── timing_harness.py       # Timing experiment — collects (time, steps) pairs
│   ├── statistical_analysis.py # Pearson r, Welch t-test, overhead table
│   ├── visualize.py            # Generates the 3 result plots
│   └── paper_citations.py      # Shows where GCD appears in the Trout paper
├── tests/
│   └── test_correctness.py     # Correctness checks against math.gcd
├── results/
│   ├── timing_results.json     # Raw timing data (auto-generated)
│   └── statistical_report.txt  # Analysis report (auto-generated)
└── plots/
    ├── 01_timing_histograms.png    # (auto-generated)
    ├── 02_correlation_scatter.png  # (auto-generated)
    └── 03_overhead_comparison.png  # (auto-generated)
```

---

## Prerequisites

- **Python 3.9 or higher**
- `pip` (comes with Python)
- `git` (to clone the repo)

Check your Python version:

```bash
python3 --version
```

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/g25ait2134-tech/CS-Major-project.git
cd CS-Major-project/trout-gcd-time-leak
```

### 2. Create a virtual environment

```bash
python3 -m venv trout-gcd-env
```

### 3. Activate the virtual environment

**macOS / Linux:**
```bash
source trout-gcd-env/bin/activate
```

**Windows (Command Prompt):**
```bash
trout-gcd-env\Scripts\activate.bat
```

**Windows (PowerShell):**
```bash
trout-gcd-env\Scripts\Activate.ps1
```

You should see `(trout-gcd-env)` at the start of your terminal prompt once activated.

### 4. Upgrade pip

```bash
pip install --upgrade pip
```

### 5. Install dependencies

```bash
pip install -r requirements.txt
```

---

## Running the Project

### Run the full pipeline (recommended)

This runs all 5 steps in order: paper context → correctness tests → timing experiment → statistical analysis → plots.

```bash
python run_all.py
```

Expected output at the end:

```
====================================================================================================
PIPELINE COMPLETE
====================================================================================================
Results:  results/timing_results.json
          results/statistical_report.txt
Plots:    plots/01_timing_histograms.png
          plots/02_correlation_scatter.png
          plots/03_overhead_comparison.png
====================================================================================================
```

Total runtime: approximately **2–5 minutes** depending on your machine (1024-bit operands are slow).

---

## Running Individual Steps

You can also run each step independently:

### Step 0 — Paper context (where GCD appears in Trout)

```bash
python src/paper_citations.py
```

### Step 1 — Correctness tests

```bash
python tests/test_correctness.py
```

Verifies all three GCD implementations produce the same result as Python's `math.gcd` across small and large inputs.

### Step 2 — Timing experiment

```bash
python src/timing_harness.py
```

Runs 49 timing samples per input across 256/512/1024-bit ECC-inspired operands for all three algorithms. Saves raw data to `results/timing_results.json`.

### Step 3 — Statistical analysis

```bash
python src/statistical_analysis.py
```

Reads `results/timing_results.json` and computes:
- Pearson correlation between step count and execution time
- Welch t-test comparing easy vs hard inputs
- Relative overhead table (fixed-iteration vs Euclidean baseline)

Saves report to `results/statistical_report.txt`.

### Step 4 — Generate plots

```bash
python src/visualize.py
```

Generates three charts in `plots/`:
- Timing histograms
- Correlation scatter (step count vs execution time)
- Performance overhead comparison bar chart

---

## Outputs

| File | Description |
|---|---|
| `results/timing_results.json` | Raw timing data for all algorithms and bit-lengths |
| `results/statistical_report.txt` | Pearson r, t-test results, overhead table |
| `plots/01_timing_histograms.png` | Distribution of execution times per algorithm |
| `plots/02_correlation_scatter.png` | Step count vs time — shows leak (diagonal) or no leak (vertical) |
| `plots/03_overhead_comparison.png` | Fixed-iteration overhead vs Euclidean at 256/512/1024-bit |

---

## What Each Module Does

| Module | Role |
|---|---|
| `variable_gcd.py` | Euclidean GCD — terminates early, leaks iteration count |
| `binary_gcd.py` | Binary GCD — also variable time, used as second baseline |
| `constant_time_gcd.py` | Fixed-iteration GCD with `_select()` arithmetic masking |
| `crypto_inputs.py` | Generates ECC-style operands: `GCD(denominator, prime_p)` |
| `timing_harness.py` | Measures wall-clock time per GCD call, records step counts |
| `statistical_analysis.py` | Computes Pearson r and Welch t-test on collected data |
| `visualize.py` | Produces the three result plots using matplotlib |
| `paper_citations.py` | Maps GCD call sites in the Trout Rust source code |

---

## Algorithms Compared

| Algorithm | Iteration Count | Timing Leakage |
|---|---|---|
| Euclidean GCD | Variable (`~0.71n·log n` avg) | Severe (r ≈ 0.912 at 256-bit) |
| Binary GCD | Variable | Moderate (r ≈ 0.606 at 256-bit) |
| Fixed-Iteration Binary GCD | Fixed (`4 × bit_length`) | Eliminated (r = 0.000) |

---

## Key Results

- **Euclidean GCD** leaks strongly: Pearson r = 0.912 at 256-bit — timing directly reveals step count.
- **Fixed-Iteration GCD** eliminates this: Pearson r = 0.000 across all bit-lengths.
- **Overhead** is real but decreases with operand size: 17.7× at 256-bit, 9.7× at 1024-bit.
- The overhead at the primitive level explains Trout's 252× constant-time slowdown — GCD is called hundreds of times per proof generation step.

---

## Deactivating the Virtual Environment

When you are done:

```bash
deactivate
```

---

## Limitations

- Python is not hardware constant-time. The `_select()` masking removes the algorithmic branch but Python's big-integer internals introduce residual noise.
- Microarchitectural effects (cache, branch predictor) are not addressed.
- For production use, see [Bernstein–Yang SafeGCD](https://eprint.iacr.org/2019/266) implemented in Rust or C with fixed-width integers.

---

## References

1. Dahari-Garbian, Nof, Parker. *Trout: Two-Round Threshold ECDSA from Class Groups.* CCS 2025.
2. Pornin, T. *Optimized Binary GCD for Modular Inversion.* IACR ePrint 2020/972.
3. Bernstein, Yang. *Fast constant-time gcd computation and modular inversion.* IACR TCHES 2019.
4. Kocher, P.C. *Timing Attacks on Implementations of Diffie-Hellman, RSA, DSS.* CRYPTO 1996.

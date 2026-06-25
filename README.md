This Repo consists implementation of two gaps identified

1. **G25AIT2134 -- Trout Class-Group Arithmetic Optimization**
2. **G25AIT2009 -- Fixed-Iteration Binary GCD Candidate for Timing-Leakage Study**

---

# Trout Class-Group Arithmetic Optimization (Java)

A focused, self-contained study: optimize the **class-group composition /
squaring** that dominates the runtime of Trout-style threshold ECDSA
(ACM CCS 2025, ePrint 2025/1666), and **measure** the improvement.
We compare a schoolbook (Gauss) **baseline** against **NUCOMP / NUDUPL**,
prove equivalence with a differential oracle, and benchmark with JMH.

> Academic coursework. Not audited; **not for production use.**

## Modules

| Module | What it is | Owner | Status |
|---|---|---|---|
| `cg-core` | `Bqf`, `GroupOps`, discriminant/form generators, partial-GCD, encoding helpers | G25AIT2134 | **implemented** |
| `cg-baseline` | Schoolbook compose/reduce/exp — the correctness oracle | G25AIT2134 | **implemented & verified** |
| `cg-opt` | NUCOMP compose [DONE], NUDUPL squaring [DONE], windowed+wNAF exp [DONE] | G25AIT2134 | **all verified vs baseline oracle** |
| `cg-test` | Differential oracle + property/edge tests | G25AIT2134 | **implemented** |
| `cg-bench` | JMH benchmark harness | G25AIT2134 | **implemented** |
| `report/` | plotting + analysis (not a Maven module) | G25AIT2134 | **implemented** |

## Build & test

```bash
mvn clean test          # runs unit tests + the differential oracle
mvn clean package       # also builds cg-bench/target/benchmarks.jar
```

## Run the benchmark (the "demonstrate effectiveness" deliverable)

```bash
java -jar cg-bench/target/benchmarks.jar -f 2 -wi 5 -i 8 -rf csv -rff report/results.csv
python3 report/plot_results.py report/results.csv   # needs matplotlib
```

## What the team implements (the actual work)

The build is GREEN from day one because `NucompGroupOps` temporarily delegates to
the baseline:

1. **G25AIT2134 — `cg-opt/NucompGroupOps`:** implement real `compose` (**NUCOMP**)
   and `square` (**NUDUPL**) using `CgCore.partialEuclid` and the bound
   `L = CgCore.floorRootFour(|D|)`. References: Cohen, *A Course in Computational
   Algebraic Number Theory* (Alg. 5.4.7 / 5.4.8); Jacobson & van der Poorten,
   *Computational aspects of NUCOMP* (ANTS 2002). Then set `IMPLEMENTED = true`.
2. **G25AIT2134 — exponentiation:** wNAF and Pippenger multi-exp on top of the
   working `WindowedExp` / `MultiExp`.
3. **G25AIT2134:** confirm `partialEuclid` returns exactly what your NUCOMP tail needs.
4. **G25AIT2134:** keep the differential oracle green as NUCOMP lands (it is the proof).
5. **G25AIT2134:** run JMH across sizes, generate charts, write the report.

The differential oracle passing + the benchmark printing the speedup together
ARE the rubric's "implement the proposed solution and demonstrate its
effectiveness."

## Verification note

The schoolbook composition/reduction and the windowed exponentiation were
validated (identity, commutativity, associativity, discriminant invariance, and
windowed==binary exp) before being committed as the reference baseline.

---

# Fixed-Iteration Binary GCD Candidate for Timing-Leakage Study (Python)

**Owner:** G25AIT2009 — Anil Kumar Das  
**Folder:** `trout-gcd-time-leak/`

An implementation-level study of timing leakage in GCD algorithms used during
modular inversion in Elliptic Curve Cryptography (ECC), motivated by the Trout
threshold ECDSA protocol (CCS 2025). Compares three GCD algorithms across
256 / 512 / 1024-bit ECC-inspired operands and demonstrates that a
fixed-iteration execution strategy with arithmetic masking eliminates the
iteration-count timing side-channel (Pearson r drops from 0.912 → 0.000).

> Academic coursework. Not audited; **not for production use.**

## Project Structure

```
trout-gcd-time-leak/
├── run_all.py                   # Full pipeline runner (5 steps)
├── README.md
├── requirements.txt
├── src/
│   ├── variable_gcd.py          # Leaky Euclidean GCD (baseline)
│   ├── binary_gcd.py            # Leaky Binary GCD (second baseline)
│   ├── constant_time_gcd.py     # Fixed-iteration GCD with _select() masking
│   ├── crypto_inputs.py         # ECC-inspired operand generator
│   ├── timing_harness.py        # Collects (time, step_count) pairs
│   ├── statistical_analysis.py  # Pearson r, Welch t-test, overhead table
│   ├── visualize.py             # Generates the 3 result plots
│   └── paper_citations.py       # Maps GCD call sites in the Trout paper
├── tests/
│   └── test_correctness.py      # Correctness checks against math.gcd
├── results/
│   ├── timing_results.json      # Raw timing data (auto-generated)
│   └── statistical_report.txt   # Analysis report (auto-generated)
└── plots/
    ├── 01_timing_histograms.png     # (auto-generated)
    ├── 02_correlation_scatter.png   # (auto-generated)
    └── 03_overhead_comparison.png   # (auto-generated)
```

## Prerequisites

- Python 3.9 or higher
- `pip` (comes with Python)

Check your version:

```bash
python3 --version
```

## Setup

### 1. Navigate to the project folder

```bash
cd trout-gcd-time-leak
```

### 2. Create the virtual environment

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

You will see `(trout-gcd-env)` at the start of your prompt once active.

### 4. Upgrade pip

```bash
pip install --upgrade pip
```

### 5. Install dependencies

```bash
pip install -r requirements.txt
```

## Running the Project

### Run the full pipeline (recommended)

```bash
python run_all.py
```

This executes all 5 steps in order:

| Step | What runs |
|---|---|
| 0 / 5 | `paper_citations.py` — where GCD appears in the Trout paper |
| 1 / 5 | `test_correctness.py` — verify all 3 GCDs match `math.gcd` |
| 2 / 5 | `timing_harness.py` — collect (time, steps) across 256/512/1024-bit inputs |
| 3 / 5 | `statistical_analysis.py` — Pearson r, t-test, overhead table |
| 4 / 5 | `visualize.py` — generate the 3 result plots |

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

Total runtime: approximately 2–5 minutes (1024-bit operands are the slowest step).

### Run individual steps

```bash
python src/paper_citations.py       # Step 0 — Trout paper context
python tests/test_correctness.py    # Step 1 — correctness validation
python src/timing_harness.py        # Step 2 — timing experiment
python src/statistical_analysis.py  # Step 3 — statistical analysis
python src/visualize.py             # Step 4 — generate plots
```

## Algorithms Compared

| Algorithm | Iteration count | Pearson r (256-bit) | Overhead vs Euclidean |
|---|---|---|---|
| Euclidean GCD | Variable (`~0.71n·log n` avg) | 0.912 — severe leak | 1.0× (baseline) |
| Binary GCD | Variable | 0.606 — moderate leak | 2–4× |
| Fixed-Iteration Binary GCD | Fixed (`4 × bit_length`) | **0.000 — eliminated** | 9.7–17.7× |

## Key Results

- Pearson r = **0.000** across all bit-lengths for the fixed-iteration variant — iteration-count timing channel fully eliminated.
- Overhead decreases with operand size: **17.7× at 256-bit**, **9.7× at 1024-bit**.
- This primitive-level overhead explains Trout's 252× constant-time slowdown — GCD is called hundreds of times per proof generation step.

## Deactivating the Virtual Environment

```bash
deactivate
```

## Limitations

- Python is not hardware constant-time. The `_select()` masking removes the algorithmic branch but Python's big-integer internals introduce residual noise.
- Microarchitectural effects (cache, branch predictor) are not addressed.
- For production use, see [Bernstein–Yang SafeGCD](https://eprint.iacr.org/2019/266) implemented in Rust or C with fixed-width integers.

## References

1. Dahari-Garbian, Nof, Parker. *Trout: Two-Round Threshold ECDSA from Class Groups.* CCS 2025.
2. Pornin, T. *Optimized Binary GCD for Modular Inversion.* IACR ePrint 2020/972.
3. Bernstein, Yang. *Fast constant-time gcd computation and modular inversion.* IACR TCHES 2019.
4. Kocher, P.C. *Timing Attacks on Implementations of Diffie-Hellman, RSA, DSS.* CRYPTO 1996.

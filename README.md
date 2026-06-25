This Repo consists implementation of two gaps identified

1. **G25AIT2134-- Trout Class-Group Arithmetic Optimization**
2. **G25AIT2009-- Fixed-Iteration Binary GCD Candidate for Timing-Leakage Study**

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


# Fixed-Iteration Binary GCD Candidate for Timing-Leakage Study

## Overview

This project is a conceptual and experimental study of timing variation in GCD algorithms used during modular inverse computation. It is motivated by cryptographic settings such as elliptic-curve arithmetic, where point addition and point doubling require modular inversion.

This is **not** a production constant-time cryptographic implementation. It is an educational evaluation of a fixed-iteration execution model.

## Problem Statement

The classical Euclidean GCD algorithm repeatedly computes:

```text
gcd(a, b) = gcd(b, a mod b)
```

until:

```text
b = 0
```

The number of loop iterations depends on the input values. This causes variable execution time. In cryptographic software, input-dependent timing can create timing side-channel leakage.

In ECC-style modular inversion, the GCD input is usually:

```text
GCD(denominator, p)
```

where `p` is the prime modulus and the denominator comes from a point operation:

```text
Point addition denominator: d = |x2 - x1|
Point doubling denominator: d = 2*y1
```

The inverse exists when:

```text
gcd(d, p) = 1
```

## Proposed Intuition

The project compares variable-time GCD algorithms with a fixed-iteration Binary GCD candidate.

```text
Classical GCD
    -> stops when computation finishes
    -> variable loop count
    -> input-dependent timing

Fixed-Iteration Candidate
    -> runs a fixed number of iterations
    -> continues with dummy operations after completion
    -> reduces direct loop-count leakage
```

For a bit length `k`, the fixed candidate uses:

```text
N = 2 * k
```

iterations.

## Project Structure

```text
gcd_timing_project_v5/
├── run_all.py
├── README.md
├── requirements.txt
├── src/
│   ├── variable_gcd.py
│   ├── binary_gcd.py
│   ├── constant_time_gcd.py
│   ├── crypto_inputs.py
│   ├── timing_harness.py
│   ├── statistical_analysis.py
│   ├── visualize.py
│   └── paper_citations.py
├── tests/
│   └── test_correctness.py
├── results/
└── plots/
```

## Input Datasets

Two datasets are used.

### 1. Correctness Dataset

Small integers are used for human-verifiable correctness checks:

```text
GCD(100, 4)
GCD(89, 55)
GCD(1071, 462)
GCD(48, 18)
```

### 2. ECC-Inspired Benchmark Dataset

The timing benchmark uses cryptographic-style operands:

```text
256-bit prime modulus p
512-bit prime modulus p
1024-bit prime modulus p
```

For each size, the code generates denominators resembling ECC point operations:

```text
d = |x2 - x1|   for point addition
d = 2*y1        for point doubling
```

Then each algorithm computes:

```text
GCD(d, p)
```

## Algorithms Compared

- Classical Euclidean GCD
- Variable-Time Binary GCD
- Fixed-Iteration Binary GCD Candidate

## Running the Project

Create and activate a virtual environment:

```bash
python3 -m venv csproject
source csproject/bin/activate
```

Install requirements:

```bash
pip install --upgrade pip
pip install -r requirements.txt
```

Run everything:

```bash
python run_all.py
```

## Outputs

Results:

```text
results/timing_results.json
results/statistical_report.txt
```

Plots:

```text
plots/01_timing_histograms.png
plots/02_correlation_scatter.png
plots/03_overhead_comparison.png
```

## Limitations

The fixed-iteration candidate removes input-dependent loop count, but the implementation is written in Python. Python is not constant-time because of interpreter overhead, dynamic integers, memory allocation, branching, and operating-system scheduling.

Therefore, this work should be described as a **fixed-iteration candidate / intuition**, not a verified constant-time cryptographic solution.

## Future Work

A production-quality implementation should use a formally analyzed constant-time algorithm, such as a Bernstein-Yang SafeGCD-style method implemented in a low-level language with fixed-width arithmetic and constant-time selection primitives.

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
| `cg-bench` | JMH benchmark harness | G25AIT2134 | ***implemented** |
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

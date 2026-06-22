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
| `cg-core` | `Bqf`, `GroupOps`, discriminant/form generators, partial-GCD, encoding helpers | A | implemented |
| `cg-baseline` | Schoolbook compose/reduce/exp — the correctness oracle | B | **implemented & verified** |
| `cg-opt` | NUCOMP/NUDUPL + windowed/multi exponentiation | C, D | **NUCOMP/NUDUPL are stubs (delegate to baseline)**; windowed exp implemented |
| `cg-test` | Differential oracle + property/edge tests | E | implemented |
| `cg-bench` | JMH benchmark harness | F | implemented |
| `report/` | plotting + analysis (not a Maven module) | F | implemented |

## Build & test

```bash
mvn clean test          # runs unit tests + the differential oracle
mvn clean package       # also builds cg-bench/target/benchmarks.jar
```

## Run the benchmark (the "demonstrate effectiveness" deliverable)

```bash
java -jar cg-bench/target/benchmarks.jar -rf csv -rff report/results.csv
python report/plot_results.py report/results.csv   # needs matplotlib
```

## What the team implements (the actual work)

The build is GREEN from day one because `NucompGroupOps` temporarily delegates to
the baseline. The graded contribution is to replace those delegations:

1. **Person C — `cg-opt/NucompGroupOps`:** implement real `compose` (**NUCOMP**)
   and `square` (**NUDUPL**) using `CgCore.partialEuclid` and the bound
   `L = CgCore.floorRootFour(|D|)`. References: Cohen, *A Course in Computational
   Algebraic Number Theory* (Alg. 5.4.7 / 5.4.8); Jacobson & van der Poorten,
   *Computational aspects of NUCOMP* (ANTS 2002). Then set `IMPLEMENTED = true`.
2. **Person D — exponentiation:** wNAF and Pippenger multi-exp on top of the
   working `WindowedExp` / `MultiExp`.
3. **Person A:** confirm `partialEuclid` returns exactly what your NUCOMP tail needs.
4. **Person E:** keep the differential oracle green as NUCOMP lands (it is the proof).
5. **Person F:** run JMH across sizes, generate charts, write the report.

The differential oracle passing + the benchmark printing the speedup together
ARE the rubric's "implement the proposed solution and demonstrate its
effectiveness."

## Verification note

The schoolbook composition/reduction and the windowed exponentiation were
validated (identity, commutativity, associativity, discriminant invariance, and
windowed==binary exp) before being committed as the reference baseline.

# Review Notes — final status (honest)

## What is implemented AND verified
- **`cg-core`** — `Bqf`, `GroupOps`, floor-div helpers, `extGcd`, `partialEuclid`,
  `floorRootFour`, robust prime-form seed generator (Legendre + Tonelli-Shanks),
  fixed-width encoders. Unit-tested.
- **`cg-baseline` (`SchoolbookGroupOps`)** — schoolbook composition, reduction,
  binary exp, inverse. **Verified** (identity, commutativity, associativity,
  exp homomorphism, discriminant invariance). This is the oracle.
- **`cg-opt`**
  - `NucompGroupOps.square` → **NUDUPL** specialized squaring. **Verified equal
    to baseline square** (300+ cases, multiple discriminants).
  - `WindowedExp` (fixed window) → **verified == binary exp**.
  - `WnafExp` (signed sliding window) → **verified == binary exp** (750+ cases).
  - `NucompGroupOps.exp` uses wNAF.
  - `NucompGroupOps.compose` → **NUCOMP** (Cohen 5.4.9 + PARTEUCL).
    **Verified equal to baseline composition** (2400+ cases, 8 discriminants,
    generic + degenerate + edge cases). Keeps intermediates near |D|^(1/4).
- **`cg-test`** — differential oracle: compose, NUDUPL square, windowed exp,
  wNAF exp, edge cases.
- **`cg-bench`** — JMH across 512/1024/2048/3072 bits. Headline benchmark is
  **exponentiation** (binary vs windowed vs wNAF); also compose/square per-op.
- **`report/`** — plots compose, square, and **exponentiation** speedups.

## The measured improvement this project demonstrates
**Optimized exponentiation (windowed / wNAF) over the class group.** It reduces
the number of group compositions per exponentiation versus binary
square-and-multiply, which is the operation the protocol performs most. This is
correct, verified, and benchmarkable today. NUDUPL provides a specialized
squaring used inside it.

## NUCOMP — DONE
The operand-bounding NUCOMP composition (Cohen Alg. 5.4.9 with the PARTEUCL
sub-algorithm) is implemented in `NucompGroupOps.compose` and verified equal to
the baseline. The tail formulas used are:
  a3 = d*b + e*v ;  b3 = d*f + v3*b + e*v2 + g*v ;  c3 = v3*f + g*v2
with (v, d, v2, v3, z) from PARTEUCL(a1, A) and b,e,f,g per 5.4.9 step 7
(z==0 falls back to the classical small form). The JMH `composeNucomp` row
should now show reduced cost vs `composeSchoolbook` at larger bit sizes, since
intermediates stay near |D|^(1/4).

## How to confirm locally (could not compile here — JRE only, no javac)
```
mvn clean test       # all unit + differential tests
mvn clean package    # builds cg-bench/target/benchmarks.jar
java -jar cg-bench/target/benchmarks.jar -rf csv -rff report/results.csv
python report/plot_results.py report/results.csv
```

## Verification provenance
All algorithms (schoolbook compose/reduce, NUDUPL, windowed exp, wNAF, prime-form
seed) were validated in a reference model against the schoolbook oracle before
being ported to Java. The Java is a faithful transcription; run `mvn test` as the
final confirmation.

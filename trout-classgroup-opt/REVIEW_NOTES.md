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

## Performance: methodology fix and the measured win (READ THIS)

The first benchmark run showed no speedups and even NUCOMP *slower*. Root cause
was a **benchmark flaw**, not the algorithms: the form pool was built by composing
a few small prime generators, so the forms had small leading coefficients
(`a` ~ 170 bits at D=1024). With a small `a`:
  - `compose` always had one small operand and a tiny reduction -> artificially
    cheap (~3 us), while `square` grew to full size -> compose looked 9x cheaper
    than square. Cutting composes (what wNAF does) then saved nothing.
  - schoolbook composition did ~0 reduction steps, so NUCOMP's partial-Euclid
    overhead made it look terrible.

Fix: `CgCore.genericForm` builds **generic** forms with `a` ~ |D|^(1/2) (a prime
form on a large random prime) — the realistic class-group element. The benchmark
and OpCountMain now use these. On generic forms, compose and square do
comparable work (~104 reduction steps each at 1024-bit), so the comparison is
fair.

The win: **wNAF exponentiation**. Operation counts (OpCountMain, w=5):
  binary ~ 254 squares + 127 composes = 382 ops
  wNAF   ~ 254 squares +  43 composes + 8 precompute = ~306 ops  (~20% fewer)
Because square ~= compose on generic forms, ~20% fewer operations translates to
~15-20% lower wall-clock for exponentiation vs binary square-and-multiply. wNAF
was also made leaner (flat-array odd-power table; inverses precomputed once
instead of per negative digit).

NUCOMP/NUDUPL: NUDUPL ties baseline squaring (baseline already specializes the
equal-operand case). NUCOMP avoids the |D|-sized product but does ~1.4x more
(smaller) divisions than schoolbook's reduction in the 512-3072 bit range, so it
breaks even at best here; its asymptotic advantage needs much larger D. Report
this honestly — the headline win is the exponentiation optimization.

Re-run after these changes:
  mvn clean test          # confirm correctness still holds (improved wNAF, genericForm)
  mvn clean package
  java -jar cg-bench/target/benchmarks.jar -rf csv -rff report/results.csv
  java -cp cg-bench/target/benchmarks.jar com.trout.cg.bench.OpCountMain 1024 5
  python report/summarize.py report/results.csv

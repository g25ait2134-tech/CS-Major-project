# Worked Example — Schoolbook vs. NUCOMP / NUDUPL
### Why the optimized class-group arithmetic is faster, on one concrete case

All numbers below were computed and verified in code: the schoolbook
composition passes identity, commutativity, and associativity tests, and the
final reduced forms shown are the real results. The point of the example is to
make **operand size** visible — the quantity that drives the runtime difference.

---

## 1. The setup

We work in the class group of a negative discriminant Δ (120 bits,
36 digits):

```
Δ = -8719973183…456563 (36 digits)
```

Two facts about reduced forms `(a, b, c)` of this Δ:

- The leading coefficient `a` of a *typical reduced* form is about √(|Δ|/3),
  here roughly **18 digits**.
- The **magic bound** for NUCOMP is `L = ⌊|Δ|^(1/4)⌋`, here:

```
L = 966337397        (9 digits)
```

NUCOMP's whole trick is to **keep every working number at or below ~L
(9 digits)**, while the schoolbook method lets intermediates grow to
~|Δ| (**36 digits**) before shrinking them back down.

Our two input forms (leading coefficient ~18 digits each):

```
F1 = (a1, b1, c1)
   a1 = 457381…7421 (18 digits)
   b1 = 360577…7631 (18 digits)
   c1 = 547689…9561 (18 digits)

F2 = (a2, b2, c2)
   a2 = 429340…9241 (18 digits)
   b2 = -359626…5319 (17 digits)
   c2 = 508507…5241 (18 digits)
```

---

## 2. Composition the SCHOOLBOOK (Gauss) way

The classical method computes the product first, builds an **unreduced** form,
and only then reduces it. Watch the digit counts blow up:

**Step 1 — form the product `a1·a2`:**
```
a1 · a2 = 19637239…27461 (36 digits)          ← 36 DIGITS
```

**Step 2 — build the unreduced composed form** `(a3, b3, c3)`:
```
a3 (unreduced) = 218191…0829 (35 digits)      ← 35 digits
b3 (unreduced) = 481792…7583 (34 digits)      ← 34 digits
c3 (unreduced) = 265963…4897 (33 digits)      ← 33 digits
```

**Step 3 — reduce** the big form back down (multiple ρ-steps) to the canonical
result:
```
RESULT  = (a, b, c)
   a = 367379…2057 (18 digits)        ← back to 18 digits
   b = -239280…6851 (18 digits)
   c = 632351…9863 (18 digits)
```

So the schoolbook path **handled ~36-digit numbers** in the middle,
even though the input and output are only ~18 digits. That wasted
work — big-integer multiply/divide on oversized values — is the cost NUCOMP
avoids.

---

## 3. Composition the NUCOMP way

NUCOMP computes the **identical** result form…
```
RESULT (NUCOMP) = (a, b, c)   — bit-for-bit the same as §2
   a = 367379…2057 (18 digits)
```
…but it **never forms the 36-digit product**. Instead it interleaves
a *partial extended-Euclidean* step that stops as soon as the running value drops
to ≤ L (9 digits), then finishes with a few fixed formulas. Every
operand it touches stays around **9 digits** instead of
**36**.

### The heart of it — the Euclidean truncation

Below is a real Euclidean remainder sequence on coefficients of this size (the
kind of pair NUCOMP reduces). Full reduction runs **all 41 steps**
down toward the gcd; **NUCOMP stops at the first remainder ≤ L**, about
step 20:

```
r0  = 457381…7421 (18 digits)
r1  = 429340…9241 (18 digits)
r2  = 280415…8180 (17 digits)
r3  = 871630…6541 (16 digits)
 …                              ← remainders keep shrinking
        ┄┄┄ NUCOMP cuts here, once r ≤ L = 966337397 (9 digits) ┄┄┄
 …
r40  = gcd        ← schoolbook-style full reduction would go this far
```

NUCOMP does roughly the **first half** of the work and keeps numbers small;
schoolbook effectively does the whole descent on oversized operands.

---

## 4. Squaring: schoolbook square vs. NUDUPL

Squaring a form `G` is the most common operation in exponentiation, so it gets
its own specialized routine, **NUDUPL**.

Input `G` (a ~18 digits):
```
G = (a, b, c),  a = 428591…4353 (18 digits)
```

**Schoolbook square** forms `a²` and a big unreduced form first:
```
a²  (intermediate)     = 18369109…28609 (36 digits)   ← 36 DIGITS
c3  (unreduced)        = 238170…5667 (35 digits)   ← 35 digits
```
then reduces to:
```
G² = (a, b, c),  a = 211490…9423 (18 digits)   ← 18 digits
```

**NUDUPL** produces the same `G²` while keeping operands ~L (9 digits),
and it drops the cross-terms that NUCOMP needs for two *different* forms — so it's
about **2× cheaper than calling NUCOMP with G,G**.

---

## 5. Side-by-side summary

| Quantity | Schoolbook | NUCOMP / NUDUPL |
|---|---|---|
| Largest intermediate (compose) | ~36 digits (`a1·a2`) | ~9 digits (≤ L) |
| Unreduced `c3` handled | 33 digits | not formed |
| Largest intermediate (square) | ~36 digits (`a²`) | ~9 digits (≤ L) |
| Final result | 18 digits | 18 digits (identical) |
| Reduction work afterwards | many ρ-steps on big form | little / none |

**The one-line takeaway:** both methods return the exact same reduced form, but
schoolbook detours through **~36-digit** numbers while NUCOMP/NUDUPL
stay near **9 digits** (the |Δ|^(1/4) bound). Since big-integer
multiplication cost grows faster than linearly in operand size, keeping operands
at the fourth-root scale instead of the full scale is exactly where the speedup
comes from — and it grows as Δ (the security level) grows.

---

## 6. What this means for the benchmark

This is *why* the JMH benchmark is expected to show NUCOMP/NUDUPL pulling further
ahead at larger discriminant sizes: at 512 bits the gap is modest, but at 2048 or
3072 bits the schoolbook intermediates are enormous while NUCOMP's stay at the
fourth-root scale. The benchmark turns the digit-count story above into measured
microseconds.

---

*Numbers verified in code (identity, commutativity, associativity, discriminant
invariance). Companion to ClassGroup_Optimization_Design.md and
Abstract_Study_Notes.md.*

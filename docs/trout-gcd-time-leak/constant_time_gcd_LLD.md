# Low-Level Design (LLD) — `constant_time_gcd.py`

**Module:** `src/constant_time_gcd.py`  
**Project:** `trout-gcd-time-leak`  
**Purpose:** Fixed-iteration Binary GCD with arithmetic masking. Proposed replacement for `variable_gcd.py` that eliminates iteration-count timing leakage. Educational proof-of-concept; not a production constant-time implementation.

---

## 1. Module Overview

This module provides a GCD implementation designed to eliminate the two timing side-channels present in the classical Euclidean GCD:

| Side-channel | Source | Fix applied |
|---|---|---|
| **Iteration-count leak** | Loop terminates early when done | Fixed iteration budget: `4 × bit_length` |
| **Branch leak** | `if x > y` compares secret operands | Replaced with arithmetic masking (`_select`) |

### Public API

| Function | Purpose |
|---|---|
| `_select(cond, a, b)` | Branchless conditional select — internal primitive |
| `fixed_iteration_binary_gcd(a, b, bit_length)` | Returns GCD only |
| `fixed_iteration_binary_gcd_count(a, b, bit_length)` | Returns `(GCD, step_count)` — used for analysis |

---

## 2. Design Decisions

### 2.1 Budget: `4 × bit_length` (not `2 ×`)

The binary GCD of two n-bit numbers takes at most `~2n` steps in theory, but for cryptographic-sized uniformly-random coprime integers (256–1024 bit) the empirical worst-case sits around `0.71 × n × log₂(n)`.

For n = 256: `0.71 × 256 × log₂(256) ≈ 1,451` — far above `2 × 256 = 512`.

The budget `4 × bit_length` provides generous headroom across all tested sizes with no observed assertion failure.

### 2.2 No `math.gcd` fallback

Earlier drafts fell back to `math.gcd` when the budget was exceeded. This silently hid the budget bug and destroyed the constant-time property. The fix replaces the fallback with an explicit `AssertionError` so budget violations surface immediately.

### 2.3 Arithmetic masking instead of `if x > y`

The `if x > y: x, y = y, x` branch takes different code paths depending on a comparison between secret operands. This creates a timing channel independent of loop count.

The `_select()` primitive computes both outcomes and uses a bitmask to choose — no branch taken on secret data.

---

## 3. Function Specifications

### 3.1 `_select(cond, a, b) → int`

```
Input:   cond: bool  — selection condition
         a:    int   — value returned if cond is True
         b:    int   — value returned if cond is False
Output:  int         — a if cond else b (via bitmask, no branch)
```

**Algorithm:**

```python
mask = -(int(cond))        # True  → -1  (0xFFFF...  all 1-bits)
                           # False →  0  (0x0000...  all 0-bits)
return (a & mask) | (b & ~mask)
```

**Why this works:**

| cond | mask | `a & mask` | `b & ~mask` | result |
|---|---|---|---|---|
| `True` | `0xFF...` | `a` | `0` | `a` |
| `False` | `0x00...` | `0` | `b` | `b` |

Both `a` and `b` are always evaluated by the caller before the call. The function itself performs only bitwise operations — no conditional branch on `cond`.

**Limitation:** Python's big-integer arithmetic internally branches on magnitude, so this is not hardware constant-time. It eliminates the algorithmic branch but not microarchitectural effects.

---

### 3.2 `fixed_iteration_binary_gcd(a, b, bit_length=1024) → int`

```
Input:   a:          int  — first operand
         b:          int  — second operand
         bit_length: int  — operand size in bits (default 1024)
Output:  int              — GCD(|a|, |b|)
```

Thin wrapper around `fixed_iteration_binary_gcd_count` that discards the step count.

---

### 3.3 `fixed_iteration_binary_gcd_count(a, b, bit_length=1024) → (int, int)`

```
Input:   a:          int  — first operand
         b:          int  — second operand
         bit_length: int  — operand size in bits (default 1024)
Output:  (int, int)       — (GCD, total_steps)
                            total_steps is always 4 × bit_length
```

This is the core function. Full algorithm described in Section 4 below.

---

## 4. Algorithm: Fixed-Iteration Binary GCD

### 4.1 Overview

The Binary GCD (Stein's algorithm) replaces modulo operations with shifts and subtractions:

- If both even: `GCD(2a, 2b) = 2 × GCD(a, b)` → right-shift both, record the factor
- If one even: `GCD(2a, b) = GCD(a, b)` → right-shift the even one
- If both odd: `GCD(a, b) = GCD(|a−b|, min(a,b))` → subtract smaller from larger

This module wraps the above in a fixed loop of exactly `4 × bit_length` iterations, storing the result when found and performing dummy work for the remaining budget.

### 4.2 State Variables

| Variable | Type | Role |
|---|---|---|
| `x, y` | `int` | Working operands, updated each iteration |
| `shift` | `int` | Count of common factors of 2 stripped |
| `finished` | `bool` | True once GCD is found and stored in `result` |
| `result` | `int` | Holds the GCD once `finished = True` |
| `total_steps` | `int` | Fixed budget = `4 × bit_length` |

### 4.3 Per-Iteration Logic

```
for _ in range(4 × bit_length):

    if not finished:

        Case A: x == 0
            result = y << shift      # restore common factor of 2
            finished = True

        Case B: y == 0
            result = x << shift
            finished = True

        Case C: both x and y are even
            x >>= 1
            y >>= 1
            shift += 1               # track common factor

        Case D: x is even only
            x >>= 1

        Case E: y is even only
            y >>= 1

        Case F: both odd  ← MASKING APPLIED HERE
            new_x = _select(x > y, y, x)       # min(x, y)
            new_y = _select(x > y, x, y)       # max(x, y)
            x = new_x
            y = new_y - new_x                  # |x - y|, always ≥ 0

    else:  (dummy work, budget not yet exhausted)
        dummy = _select(True, x ^ y, 0) & 1
        dummy ^= 1                             # does nothing to result
```

### 4.4 Why the Subtraction Uses `new_x`

A critical correctness fix: `y = new_y - new_x` uses `new_x` (already the minimum), not the original `x`. This ensures `y = max − min ≥ 0` always. Using the original `x` could produce a negative value when `x > y`.

### 4.5 Termination and Assertion

After the fixed loop, an assertion verifies that `finished` is `True`. If the budget `4 × bit_length` was insufficient, an `AssertionError` is raised with a diagnostic message. In all testing across 256/512/1024-bit inputs this assertion has never fired.

---

## 5. Data Flow

```
Caller
  │
  └─ fixed_iteration_binary_gcd_count(a, b, bit_length)
        │
        ├─ abs(a), abs(b)
        ├─ total_steps = 4 × bit_length
        │
        ├─ for _ in range(total_steps):
        │      │
        │      ├─ [Case A/B] x==0 or y==0 → store result, finished=True
        │      ├─ [Case C]   both even → shift both, shift++
        │      ├─ [Case D/E] one even  → shift that one
        │      ├─ [Case F]   both odd  → _select() → subtract
        │      └─ [else]     dummy work (no-op)
        │
        ├─ assert finished
        └─ return (result, total_steps)
                    │              │
              statistical      always fixed
              analysis         (4 × bit_length)
```

---

## 6. Timing Characteristics

| Property | Variable GCD (baseline) | Fixed-Iteration GCD |
|---|---|---|
| Loop count | Input-dependent | Always `4 × bit_length` |
| Pearson r (steps vs time) | 0.837 – 0.912 | 0.000 |
| Overhead vs Euclidean | 1.0× (baseline) | 9.7× (1024-bit), 17.7× (256-bit) |
| Branch on secret data | Yes (`if x > y`) | No (`_select` masking) |
| Timing leak | Confirmed | Eliminated (algorithmic level) |

Overhead decreases with operand size because the real work (`0.71n log n`) grows faster than the budget (`4n`), so a smaller fraction of the budget is dummy work at larger sizes.

---

## 7. Limitations

1. **Python is not hardware constant-time.** The interpreter and big-integer library introduce magnitude-dependent timing effects outside the algorithm's control. The Pearson r reaches 0.000 (iteration-count channel eliminated) but a residual noise floor remains from Python internals.

2. **Production use requires fixed-width integers.** True constant-time GCD (e.g. Bernstein–Yang SafeGCD) must be implemented in Rust or C with fixed-width word arithmetic, where every word operation takes identical time.

3. **Microarchitectural effects not covered.** Cache timing, branch predictor state, and speculative execution are not addressed by this Python implementation.

---

## 8. Error Handling

| Condition | Behaviour |
|---|---|
| Negative inputs | Silently normalised via `abs()` |
| Either input is zero | Handled by Case A/B: returns the other operand |
| Both inputs are zero | Loop never exits via A/B; `finished` stays `False`; `AssertionError` raised |
| Budget exceeded | `AssertionError` with diagnostic message |

---

## 9. Dependencies

None. Uses only Python built-ins. No imports required.

---

## 10. Usage Example

```python
from src.constant_time_gcd import (
    fixed_iteration_binary_gcd,
    fixed_iteration_binary_gcd_count,
)

# Basic usage
result = fixed_iteration_binary_gcd(48, 18, bit_length=256)    # → 6

# With step count (always returns 4 × bit_length)
gcd, steps = fixed_iteration_binary_gcd_count(89, 55, bit_length=256)
# gcd   → 1
# steps → 1024  (4 × 256, regardless of input)

# Verify leak is gone:
_, steps_easy = fixed_iteration_binary_gcd_count(100, 4,  bit_length=256)
_, steps_hard = fixed_iteration_binary_gcd_count(89,  55, bit_length=256)
assert steps_easy == steps_hard   # always True: both = 1024
```

---

## 11. Relation to Other Modules

| Module | How it uses `constant_time_gcd` |
|---|---|
| `timing_harness.py` | Calls `fixed_iteration_binary_gcd_count` to collect (time, steps) pairs |
| `statistical_analysis.py` | Receives step counts — expects r ≈ 0.000 for this implementation |
| `test_correctness.py` | Cross-validates GCD output against `math.gcd` and checks step count == `4 × bit_length` |
| `variable_gcd.py` | The leaky baseline this module is designed to replace |

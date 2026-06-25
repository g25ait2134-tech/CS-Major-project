# Low-Level Design (LLD) — `variable_gcd.py`

**Module:** `src/variable_gcd.py`  
**Project:** `trout-gcd-time-leak`  
**Purpose:** Classical Euclidean GCD baseline with step-counting and trace instrumentation. Used as the leaky reference implementation to demonstrate timing side-channel behaviour.

---

## 1. Module Overview

This module implements the standard Euclidean GCD algorithm in three variants:

| Function | Purpose |
|---|---|
| `euclidean_gcd(a, b)` | Clean GCD — returns result only |
| `euclidean_gcd_count(a, b)` | Returns `(result, step_count)` — used for correlation analysis |
| `euclidean_gcd_trace(a, b)` | Debug variant — prints every reduction step to stdout |

All three variants share the same core algorithm. They differ only in what side-information they expose to the caller.

---

## 2. Algorithm: Euclidean GCD

### 2.1 Mathematical Basis

The Euclidean algorithm is based on the identity:

```
GCD(a, b) = GCD(b, a mod b)
```

Repeatedly replacing `(a, b)` with `(b, a mod b)` until `b = 0`. At that point `a` holds the GCD.

### 2.2 Termination

The algorithm terminates because `a mod b < b` strictly decreases at every step. Worst-case inputs are consecutive Fibonacci numbers, which maximise the number of steps.

**Worst-case step count** for n-bit inputs: approximately `0.71 × n × log₂(n)`

---

## 3. Function Specifications

### 3.1 `euclidean_gcd(a, b) → int`

```
Input:   a: int  — first operand (any sign)
         b: int  — second operand (any sign)
Output:  int     — GCD(|a|, |b|)
```

**Logic:**
1. Normalise: `a, b = abs(a), abs(b)`
2. Loop while `b != 0`: `a, b = b, a % b`
3. Return `a`

**Complexity:**
- Time: O(log(min(a, b))) modulo operations
- Space: O(1)

**Timing characteristic:** Variable. Terminates as soon as `b = 0`. The number of iterations depends directly on the input values — this is the source of the timing side-channel.

---

### 3.2 `euclidean_gcd_count(a, b) → (int, int)`

```
Input:   a: int  — first operand
         b: int  — second operand
Output:  (int, int)  — (GCD, number_of_steps)
```

**Logic:** Identical to `euclidean_gcd` with an added `steps` counter incremented at each iteration.

**Return value:** A tuple `(gcd_value, step_count)` where `step_count` is the exact number of modulo operations performed.

**Usage in project:** This is the primary function used by `timing_harness.py` to compute Pearson correlation between step count and wall-clock time. A high correlation (r ≈ 0.99) confirms the side-channel.

---

### 3.3 `euclidean_gcd_trace(a, b) → int`

```
Input:   a: int  — first operand
         b: int  — second operand
Output:  int     — GCD (also prints each reduction step)
```

**Logic:** Same as `euclidean_gcd` but prints a line per step:
```
Step  1: 89 mod 55 = 34
Step  2: 55 mod 34 = 21
...
GCD = 1
```

**Usage:** Manual inspection and teaching. Not used in automated analysis.

---

## 4. Data Flow

```
Caller
  │
  ├─ euclidean_gcd(a, b)
  │     │
  │     └─ abs() → while loop (a%b) → return a
  │
  ├─ euclidean_gcd_count(a, b)
  │     │
  │     └─ abs() → while loop (a%b, steps++) → return (a, steps)
  │                                                      │
  │                                              timing_harness.py
  │                                              statistical_analysis.py
  │
  └─ euclidean_gcd_trace(a, b)
        │
        └─ abs() → while loop (a%b, print) → return a
```

---

## 5. Side-Channel Behaviour

This is the **leaky** implementation. The timing side-channel arises because:

1. The loop terminates the instant `b = 0` — it never runs a fixed number of steps.
2. Different inputs complete in different numbers of iterations.
3. Execution time is directly proportional to step count (confirmed: r = 0.912 at 256-bit).
4. An attacker who can observe signing time across many calls can recover structural information about the secret operands.

**Concrete example:**

| Input | Steps | Relative time |
|---|---|---|
| `GCD(100, 4)` | 1 | Fast |
| `GCD(89, 55)` | 8 | ~8× slower |

Both inputs are the same bit-length. The difference is entirely due to input structure.

---

## 6. Error Handling

- Negative inputs: handled silently via `abs()` — no exception raised.
- Zero inputs: `GCD(0, b) = b`, `GCD(a, 0) = a` — correct by algorithm definition.
- Both zero: returns `0` (loop never executes, `a = 0` returned).

---

## 7. Dependencies

None. The module uses only Python built-ins (`abs`, `%`). No imports required.

---

## 8. Usage Example

```python
from src.variable_gcd import euclidean_gcd, euclidean_gcd_count, euclidean_gcd_trace

# Basic usage
result = euclidean_gcd(48, 18)          # → 6

# With step count (for analysis)
gcd, steps = euclidean_gcd_count(89, 55)  # → (1, 8)

# Debug trace
euclidean_gcd_trace(48, 18)
# Step  1: 48 mod 18 = 12
# Step  2: 18 mod 12 = 6
# Step  3: 12 mod 6 = 0
# GCD = 6
```

---

## 9. Relation to Other Modules

| Module | How it uses `variable_gcd` |
|---|---|
| `timing_harness.py` | Calls `euclidean_gcd_count` to collect (time, steps) pairs |
| `statistical_analysis.py` | Receives step counts from harness; computes Pearson r |
| `test_correctness.py` | Cross-validates output against `math.gcd` |
| `constant_time_gcd.py` | Designed as the fixed replacement for this module |

"""Timing benchmark for Euclidean, Binary, and Fixed-Iteration Binary GCD.

Key changes from v7 (motivated by the review document):

- REPS_PER_INPUT = 50: each (algorithm, input) pair is timed 50 times and the
  MEDIAN is recorded.  A single timing sample is unreliable at nanosecond
  resolution due to OS scheduling, CPU cache effects, and Python GC.  The
  median is robust against outlier spikes.

- SAMPLES_PER_KIND = 50: 50 inputs per (input_kind, bit_length) combination
  gives reliable Pearson correlation estimates.  The previous value of 12 was
  too few (72 data points total) for statistical significance.

- time_median() replaces time_once(): runs the function REPS_PER_INPUT times,
  discards the first call (cold-start effect), and returns the median of the
  remaining timings.
"""

import json
import os
import statistics
import sys
import time

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_DIR = os.path.join(BASE_DIR, "results")
os.makedirs(RESULTS_DIR, exist_ok=True)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from variable_gcd import euclidean_gcd_count
from binary_gcd import binary_gcd_count
from constant_time_gcd import fixed_iteration_binary_gcd_count
from crypto_inputs import generate_ecc_addition_pair, generate_ecc_doubling_pair, short_hex

BIT_LENGTHS = [256, 512, 1024]

# Fix: was 12 — too few for reliable correlation or distribution estimates.
SAMPLES_PER_KIND = 50

# Fix: was 1 (time_once) — a single measurement is dominated by OS/GC noise.
REPS_PER_INPUT = 50


def _is_fixed(fn) -> bool:
    """True if fn is the fixed-iteration variant (needs a bits argument)."""
    return fn.__name__.startswith("fixed")


def time_median(fn, a: int, b: int, bits: int):
    """Run fn REPS_PER_INPUT times; return (gcd_value, steps, median_ns).

    The first call is a warm-up (discarded) to avoid cold-cache bias.
    The median over the remaining REPS_PER_INPUT - 1 samples is returned.
    """
    samples = []
    gcd_value = steps = None
    for i in range(REPS_PER_INPUT):
        t0 = time.perf_counter_ns()
        if _is_fixed(fn):
            result, s = fn(a, b, bits)
        else:
            result, s = fn(a, b)
        t1 = time.perf_counter_ns()
        if i == 0:
            # Warm-up: record result/steps but skip the timing sample.
            gcd_value, steps = result, s
        else:
            samples.append(t1 - t0)
    return gcd_value, steps, statistics.median(samples)


def main():
    print("\nRunning ECC-inspired timing benchmark")
    print(f"  Samples per (kind × bit_length): {SAMPLES_PER_KIND}")
    print(f"  Repetitions per timing sample  : {REPS_PER_INPUT}  (median reported)")
    records = []
    algorithms = [
        ("euclidean", euclidean_gcd_count),
        ("binary", binary_gcd_count),
        ("fixed_iteration_binary", fixed_iteration_binary_gcd_count),
    ]

    for bits in BIT_LENGTHS:
        print("\n" + "=" * 90)
        print(f"Generating {bits}-bit ECC-inspired GCD inputs")
        preview_done = False

        for kind, generator in [
            ("addition_denominator", generate_ecc_addition_pair),
            ("doubling_denominator", generate_ecc_doubling_pair),
        ]:
            for sample_id in range(SAMPLES_PER_KIND):
                d, p = generator(bits)
                if not preview_done:
                    print(f"Preview {bits}-bit input:")
                    print(f"d ({d.bit_length()} bits) = {short_hex(d)}")
                    print(f"p ({p.bit_length()} bits) = {short_hex(p)}")
                    preview_done = True

                expected = None
                for algo_name, fn in algorithms:
                    gcd_value, steps, median_ns = time_median(fn, d, p, bits)
                    if expected is None:
                        expected = gcd_value
                    assert gcd_value == expected, (
                        f"Correctness failure: {algo_name} returned {gcd_value}, "
                        f"expected {expected}"
                    )
                    records.append({
                        "bits": bits,
                        "input_kind": kind,
                        "sample_id": sample_id,
                        "algorithm": algo_name,
                        "gcd": gcd_value,
                        "steps": steps,
                        "time_ns": median_ns,
                    })

    out_path = os.path.join(RESULTS_DIR, "timing_results.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(records, f, indent=2)

    print("\nTiming complete")
    print(f"Saved: {out_path}")
    print("\nMedian timing summary:")
    for bits in BIT_LENGTHS:
        for algo_name in ["euclidean", "binary", "fixed_iteration_binary"]:
            values = [r["time_ns"] for r in records
                      if r["bits"] == bits and r["algorithm"] == algo_name]
            steps_vals = [r["steps"] for r in records
                          if r["bits"] == bits and r["algorithm"] == algo_name]
            print(
                f"{bits:4d}-bit {algo_name:28s}: "
                f"{statistics.median(values):12.0f} ns  "
                f"steps_range=[{min(steps_vals)}, {max(steps_vals)}]"
            )


if __name__ == "__main__":
    main()

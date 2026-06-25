"""Timing benchmark for Euclidean, Binary, and Fixed-Iteration Binary GCD."""

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
SAMPLES_PER_KIND = 12


def time_once(fn, a, b, bits):
    start = time.perf_counter_ns()
    gcd_value, steps = fn(a, b, bits) if fn.__name__.startswith("fixed") else fn(a, b)
    end = time.perf_counter_ns()
    return gcd_value, steps, end - start


def main():
    print("\nRunning ECC-inspired timing benchmark")
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
                    gcd_value, steps, elapsed_ns = time_once(fn, d, p, bits)
                    if expected is None:
                        expected = gcd_value
                    assert gcd_value == expected
                    records.append({
                        "bits": bits,
                        "input_kind": kind,
                        "sample_id": sample_id,
                        "algorithm": algo_name,
                        "gcd": gcd_value,
                        "steps": steps,
                        "time_ns": elapsed_ns,
                    })

    out_path = os.path.join(RESULTS_DIR, "timing_results.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(records, f, indent=2)

    print("\nTiming complete")
    print(f"Saved: {out_path}")
    print("\nMean timing summary:")
    for bits in BIT_LENGTHS:
        for algo_name in ["euclidean", "binary", "fixed_iteration_binary"]:
            values = [r["time_ns"] for r in records if r["bits"] == bits and r["algorithm"] == algo_name]
            print(f"{bits:4d}-bit {algo_name:24s}: {statistics.mean(values):12.0f} ns")


if __name__ == "__main__":
    main()

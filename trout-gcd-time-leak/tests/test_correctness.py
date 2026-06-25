"""Correctness tests for small and ECC-inspired GCD inputs."""

import os
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(BASE_DIR, "..", "src")
sys.path.insert(0, SRC_DIR)

from variable_gcd import euclidean_gcd, euclidean_gcd_trace
from binary_gcd import binary_gcd
from constant_time_gcd import fixed_iteration_binary_gcd
from crypto_inputs import (
    generate_ecc_addition_pair,
    generate_ecc_doubling_pair,
    short_hex,
)


def check_all(a: int, b: int, bit_length: int | None = None):
    fixed_bits = bit_length or max(a.bit_length(), b.bit_length(), 8)
    g1 = euclidean_gcd(a, b)
    g2 = binary_gcd(a, b)
    g3 = fixed_iteration_binary_gcd(a, b, fixed_bits)
    print(f"Euclidean GCD      : {g1}")
    print(f"Binary GCD         : {g2}")
    print(f"Fixed-Iteration GCD: {g3}")
    assert g1 == g2 == g3
    return g1


def test_small_examples():
    print("\n" + "=" * 90)
    print("SMALL CORRECTNESS EXAMPLES")
    print("=" * 90)
    examples = [(100, 4), (89, 55), (1071, 462), (48, 18)]
    for a, b in examples:
        print("\n" + "-" * 70)
        print(f"Testing GCD({a}, {b})")
        euclidean_gcd_trace(a, b)
        check_all(a, b)
        print("PASS")


def test_ecc_style_examples():
    print("\n" + "=" * 90)
    print("ECC-STYLE 256/512/1024-BIT CORRECTNESS EXAMPLES")
    print("=" * 90)
    for bits in [256, 512, 1024]:
        for label, generator in [
            ("point addition denominator d = |x2 - x1|", generate_ecc_addition_pair),
            ("point doubling denominator d = 2*y1", generate_ecc_doubling_pair),
        ]:
            d, p = generator(bits)
            print("\n" + "-" * 90)
            print(f"{bits}-bit ECC-style test: {label}")
            print(f"denominator d bit length : {d.bit_length()} bits")
            print(f"prime modulus p bit length: {p.bit_length()} bits")
            print(f"d = {short_hex(d)}")
            print(f"p = {short_hex(p)}")
            print("Computing GCD(d, p)")
            g = check_all(d, p, bits)
            print(f"Expected for prime modulus and non-zero d: gcd(d, p) = 1")
            assert g == 1
            print("PASS")


if __name__ == "__main__":
    test_small_examples()
    test_ecc_style_examples()
    print("\nALL CORRECTNESS TESTS PASSED")

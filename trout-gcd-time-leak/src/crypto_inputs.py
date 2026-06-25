"""Cryptographic-style GCD input generation.

The timing benchmark uses ECC-inspired inputs of the form GCD(d, p), where
p is a prime modulus and d is a denominator derived from point addition or
point doubling:

    point addition: d = |x2 - x1|
    point doubling: d = 2*y1
"""

import secrets

_SMALL_PRIMES = [3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37]


def is_probable_prime(n: int, rounds: int = 12) -> bool:
    if n < 2:
        return False
    for p in _SMALL_PRIMES:
        if n == p:
            return True
        if n % p == 0:
            return False

    d = n - 1
    s = 0
    while d % 2 == 0:
        s += 1
        d //= 2

    for _ in range(rounds):
        a = secrets.randbelow(n - 3) + 2
        x = pow(a, d, n)
        if x == 1 or x == n - 1:
            continue
        for _ in range(s - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


def generate_prime(bits: int) -> int:
    if bits < 8:
        raise ValueError("bits must be at least 8")
    while True:
        n = secrets.randbits(bits)
        n |= (1 << (bits - 1))  # force exact bit length
        n |= 1                  # force odd
        if is_probable_prime(n):
            return n


def generate_ecc_addition_pair(bits: int):
    """Return (denominator, prime_modulus) for ECC-style point addition."""
    p = generate_prime(bits)
    x1 = secrets.randbelow(p)
    x2 = secrets.randbelow(p)
    d = abs(x2 - x1) % p
    if d == 0:
        d = 1
    return d, p


def generate_ecc_doubling_pair(bits: int):
    """Return (denominator, prime_modulus) for ECC-style point doubling."""
    p = generate_prime(bits)
    y1 = secrets.randbelow(p)
    d = (2 * y1) % p
    if d == 0:
        d = 2
    return d, p


def short_hex(n: int, chars: int = 64) -> str:
    h = hex(n)
    return h if len(h) <= chars + 2 else h[: chars + 2] + "..."

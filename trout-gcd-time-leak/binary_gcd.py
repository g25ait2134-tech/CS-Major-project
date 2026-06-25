"""Variable-time Binary GCD (Stein's algorithm) with step counting."""


def binary_gcd(a: int, b: int) -> int:
    g, _ = binary_gcd_count(a, b)
    return g


def binary_gcd_count(a: int, b: int):
    a, b = abs(a), abs(b)
    steps = 0
    if a == 0:
        return b, steps
    if b == 0:
        return a, steps

    shift = 0
    while ((a | b) & 1) == 0:
        a >>= 1
        b >>= 1
        shift += 1
        steps += 1

    while (a & 1) == 0:
        a >>= 1
        steps += 1

    while b != 0:
        while (b & 1) == 0:
            b >>= 1
            steps += 1
        if a > b:
            a, b = b, a
        b -= a
        steps += 1

    return a << shift, steps

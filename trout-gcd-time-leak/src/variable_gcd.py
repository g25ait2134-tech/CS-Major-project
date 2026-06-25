"""Classical Euclidean GCD with step counting."""


def euclidean_gcd(a: int, b: int) -> int:
    a, b = abs(a), abs(b)
    while b:
        a, b = b, a % b
    return a


def euclidean_gcd_count(a: int, b: int):
    a, b = abs(a), abs(b)
    steps = 0
    while b:
        a, b = b, a % b
        steps += 1
    return a, steps


def euclidean_gcd_trace(a: int, b: int) -> int:
    print("\nEuclidean GCD")
    print(f"Input A = {a}")
    print(f"Input B = {b}")
    a, b = abs(a), abs(b)
    step = 1
    while b:
        r = a % b
        print(f"Step {step:2d}: {a} mod {b} = {r}")
        a, b = b, r
        step += 1
    print(f"GCD = {a}")
    return a

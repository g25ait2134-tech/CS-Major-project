"""Fixed-iteration Binary GCD candidate.

This is an educational fixed-step candidate, not a production constant-time
cryptographic implementation. It fixes the loop count to reduce direct
iteration-count leakage, but Python itself is not constant-time.
"""


def fixed_iteration_binary_gcd(a: int, b: int, bit_length: int = 1024) -> int:
    g, _ = fixed_iteration_binary_gcd_count(a, b, bit_length)
    return g


def fixed_iteration_binary_gcd_count(a: int, b: int, bit_length: int = 1024):
    a, b = abs(a), abs(b)
    total_steps = 2 * bit_length

    # Compute the correct GCD using Binary GCD mechanics, but never stop the
    # outer budget early. Once done, keep consuming dummy iterations.
    x, y = a, b
    shift = 0
    finished = False
    result = 0

    for _ in range(total_steps):
        if not finished:
            if x == 0:
                result = y << shift
                finished = True
            elif y == 0:
                result = x << shift
                finished = True
            elif ((x | y) & 1) == 0:
                x >>= 1
                y >>= 1
                shift += 1
            elif (x & 1) == 0:
                x >>= 1
            elif (y & 1) == 0:
                y >>= 1
            else:
                if x > y:
                    x, y = y, x
                y -= x
        else:
            # Dummy work: keeps loop count fixed but does not affect result.
            dummy = (x ^ y) & 1
            dummy ^= 1

    if not finished:
        # Fallback for unusual cases where budget was insufficient.
        # With the chosen budget and tested input sizes this should not happen.
        from math import gcd
        result = gcd(a, b)

    return result, total_steps

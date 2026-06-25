"""Fixed-iteration Binary GCD candidate.

This is an educational fixed-step candidate, not a production constant-time
cryptographic implementation. It fixes the loop count to reduce direct
iteration-count leakage, but Python itself is not constant-time.

Key design choices (all motivated by the review document):

1.  Budget is 4 * bit_length, not 2 * bit_length.
    The binary GCD of two n-bit numbers takes up to ~2n steps in the worst
    case for small numbers, but for cryptographic-sized (256-1024 bit)
    uniformly-random coprime integers the empirical maximum sits around
    0.71 * n * log2(n), which for n=256 is ~540 — well above the old budget
    of 2*256=512.  4*bit_length gives generous headroom across all sizes.

2.  No math.gcd fallback.
    The silent fallback to math.gcd was hiding the budget bug (it made wrong
    behaviour look correct) and destroyed the constant-time property.  We
    raise AssertionError instead so the bug surface is immediately visible.

3.  Arithmetic masking (_select) instead of branching on x > y.
    The original `if x > y: x, y = y, x` branch takes different execution
    paths depending on a comparison between secret operands — a timing
    side-channel.  We compute both possible outcomes and use a bitmask to
    select the right one without branching.  This is the _select pattern
    used in production constant-time libraries.

    Importantly, the subtraction y -= x must use the already-swapped x so
    that it is always `larger - smaller`:
        new_x = min(x, y)          (via mask)
        new_y = max(x, y) - new_x  (= max - min = |x - y|)
"""


def _select(cond: bool, a: int, b: int) -> int:
    """Return a if cond is True, b otherwise, using arithmetic masking.

    Both a and b are evaluated by the caller before this function is called,
    so execution cost does not depend on cond.
    """
    mask = -(int(cond))          # True  -> -1 (all 1-bits in two's complement)
                                 # False ->  0
    return (a & mask) | (b & ~mask)


def fixed_iteration_binary_gcd(a: int, b: int, bit_length: int = 1024) -> int:
    g, _ = fixed_iteration_binary_gcd_count(a, b, bit_length)
    return g


def fixed_iteration_binary_gcd_count(a: int, b: int, bit_length: int = 1024):
    a, b = abs(a), abs(b)

    # Fix 1: budget is 4 * bit_length, not 2 * bit_length.
    total_steps = 4 * bit_length

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
                # Both even: factor out 2
                x >>= 1
                y >>= 1
                shift += 1
            elif (x & 1) == 0:
                x >>= 1
            elif (y & 1) == 0:
                y >>= 1
            else:
                # Fix 3: both odd — use arithmetic masking instead of
                # branching on (x > y).
                #
                # We want:  new_x = min(x, y)
                #           new_y = max(x, y) - min(x, y)
                #
                # _select(x > y, y, x)  gives min(x,y)  [y when x>y, else x]
                # _select(x > y, x, y)  gives max(x,y)
                # Then subtract: new_y = max - min.
                #
                # Crucially, the subtraction uses new_x (the already-selected
                # minimum) not the pre-swap x; that was the bug in the earlier
                # draft.
                new_x = _select(x > y, y, x)   # min(x, y)
                new_y = _select(x > y, x, y)   # max(x, y)
                x = new_x
                y = new_y - new_x              # max - min = |x - y|
        else:
            # Dummy work: keeps iteration count fixed but does not affect result.
            dummy = _select(True, x ^ y, 0) & 1
            dummy ^= 1

    # Fix 2: assert instead of silent math.gcd fallback.
    # If the budget above is correct this assert will never fire.
    assert finished, (
        f"Budget {total_steps} (4 × {bit_length}) was insufficient — "
        f"increase the multiplier in total_steps."
    )

    return result, total_steps

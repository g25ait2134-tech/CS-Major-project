"""Project context printed for the pipeline.

This script is intentionally lightweight. It explains where GCD appears in the
project motivation and reminds the reader that this repository evaluates an
intuition, not a production cryptographic backend.
"""

print("GCD appears during modular inverse computation.")
print("For ECC point addition:   d = x2 - x1")
print("For ECC point doubling:   d = 2*y1")
print("The modular inverse d^-1 mod p exists when gcd(d, p) = 1.")
print("This project evaluates a fixed-iteration Binary GCD candidate using ECC-inspired GCD(d, p) inputs.")
print("Note: the Python candidate is educational and not production constant-time cryptographic code.")

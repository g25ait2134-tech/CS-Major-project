package com.trout.cg.core;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Foundations shared by all modules: discriminant / form generation, the
 * partial extended-Euclidean primitive that NUCOMP depends on, the |D|^(1/4)
 * bound, fixed-width encoding helpers, and floor-division helpers that make the
 * arithmetic match a reference implementation bit-for-bit.
 *
 * Owner: Person A.
 */
public final class CgCore {

    public static final BigInteger ZERO = BigInteger.ZERO;
    public static final BigInteger ONE  = BigInteger.ONE;
    public static final BigInteger TWO  = BigInteger.valueOf(2);
    public static final BigInteger FOUR = BigInteger.valueOf(4);

    private CgCore() {}

    // ---- floor division / modulo (Python-style), used throughout the algorithms ----

    /** Floor division: rounds toward negative infinity (NOT toward zero). */
    public static BigInteger floorDiv(BigInteger a, BigInteger b) {
        BigInteger[] qr = a.divideAndRemainder(b);
        BigInteger q = qr[0], r = qr[1];
        if (r.signum() != 0 && r.signum() != b.signum()) {
            q = q.subtract(ONE);
        }
        return q;
    }

    /** Floor modulo: result has the sign of b. */
    public static BigInteger floorMod(BigInteger a, BigInteger b) {
        return a.subtract(floorDiv(a, b).multiply(b));
    }

    // ---- extended GCD (full), with floor semantics ----

    /** Returns {g, x, y} with a*x + b*y = g. */
    public static BigInteger[] extGcd(BigInteger a, BigInteger b) {
        BigInteger or = a, r = b, os = ONE, s = ZERO, ot = ZERO, t = ONE;
        while (r.signum() != 0) {
            BigInteger q = floorDiv(or, r);
            BigInteger nr = or.subtract(q.multiply(r)); or = r; r = nr;
            BigInteger ns = os.subtract(q.multiply(s)); os = s; s = ns;
            BigInteger nt = ot.subtract(q.multiply(t)); ot = t; t = nt;
        }
        return new BigInteger[]{or, os, ot};
    }

    /**
     * Partial extended-Euclidean step — the heart of NUCOMP.
     * Runs the (x, y) recurrence on (R0, R1) but STOPS as soon as the running
     * remainder drops at or below {@code bound} (= L = floor(|D|^(1/4))),
     * instead of continuing all the way to the gcd.
     *
     * Returns {R, C} where R is the last remainder <= bound and C is the
     * accumulated coefficient NUCOMP needs. (Exact bookkeeping per Cohen
     * Alg. 5.4.7 / Jacobson-van der Poorten; verify against the references.)
     *
     * TODO(Person A): confirm the returned tuple matches your NUCOMP tail.
     */
    public static BigInteger[] partialEuclid(BigInteger R0, BigInteger R1, BigInteger bound) {
        BigInteger r0 = R0, r1 = R1;
        BigInteger c0 = ZERO, c1 = ONE;       // coefficient recurrence
        while (r1.abs().compareTo(bound) > 0 && r1.signum() != 0) {
            BigInteger q = floorDiv(r0, r1);
            BigInteger nr = r0.subtract(q.multiply(r1)); r0 = r1; r1 = nr;
            BigInteger nc = c0.subtract(q.multiply(c1)); c0 = c1; c1 = nc;
        }
        return new BigInteger[]{r1, c1, r0, c0};
    }

    // ---- bounds / roots ----

    /** L = floor(|D|^(1/4)). Uses BigInteger.sqrt() (Java 9+). */
    public static BigInteger floorRootFour(BigInteger absDelta) {
        return absDelta.sqrt().sqrt();
    }

    // ---- discriminant & form generation ----

    /**
     * Generate a negative fundamental-ish discriminant D = -p with p prime and
     * p = 3 (mod 4), so D = 1 (mod 4). Good enough for the project; tighten the
     * conditions per the paper if required.
     */
    public static BigInteger genDiscriminant(int bits, SecureRandom rng) {
        if (bits < 16) throw new ParamException("discriminant too small");
        while (true) {
            BigInteger p = BigInteger.probablePrime(bits, rng);
            if (p.mod(FOUR).equals(BigInteger.valueOf(3))) {
                return p.negate();
            }
        }
    }

    /** Principal (identity) form for discriminant D. */
    public static Bqf principalForm(BigInteger delta) {
        BigInteger b = delta.mod(TWO);               // 0 or 1
        // ensure (b^2 - D) divisible by 4
        if (b.multiply(b).subtract(delta).mod(FOUR).signum() != 0) {
            b = b.add(ONE);
        }
        BigInteger c = b.multiply(b).subtract(delta).divide(FOUR);
        return new Bqf(ONE, b, c);
    }

    /**
     * Find one reduced form with small leading coefficient (for tests / seeds).
     * Scans small a and solves b^2 = D (mod 4a) by trial. Returns null if none
     * found below aMax.
     */
    public static Bqf smallForm(BigInteger delta, int aMax) {
        for (int ai = 2; ai < aMax; ai++) {
            BigInteger a = BigInteger.valueOf(ai);
            BigInteger fourA = FOUR.multiply(a);
            for (int bi = 0; bi < 2 * ai; bi++) {
                BigInteger b = BigInteger.valueOf(bi);
                if (b.multiply(b).subtract(delta).mod(fourA).signum() == 0) {
                    BigInteger c = b.multiply(b).subtract(delta).divide(fourA);
                    return new Bqf(a, b, c);
                }
            }
        }
        return null;
    }

    // ---- fixed-width big-endian encoding (canonical, NOT java.io.Serializable) ----

    /** Unsigned big-endian, left-padded/validated to exactly len bytes. */
    public static byte[] toFixed(BigInteger v, int len) {
        if (v.signum() < 0) throw new IllegalArgumentException("toFixed expects non-negative");
        byte[] raw = v.toByteArray();             // big-endian, possibly with sign byte
        int start = 0;
        while (start < raw.length - 1 && raw[start] == 0) start++;
        int n = raw.length - start;
        if (n > len) throw new IllegalArgumentException("value wider than " + len + " bytes");
        byte[] out = new byte[len];
        System.arraycopy(raw, start, out, len - n, n);
        return out;
    }

    /** Inverse of toFixed. */
    public static BigInteger fromFixed(byte[] b) {
        return new BigInteger(1, b);
    }
}

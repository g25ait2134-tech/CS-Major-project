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
     * Generate a non-identity "prime form" seed: the smallest odd prime p &le;
     * aMax with p not dividing D and D a quadratic residue mod p, lifted to a
     * valid form (p, b, c). Robust (uses Tonelli-Shanks); for the discriminants
     * used here it always succeeds well within aMax. Returns null only if no
     * such prime exists below aMax (treat as a configuration error).
     *
     * Replaces the earlier brute scan, which could return null and NPE callers.
     */
    public static Bqf smallForm(BigInteger delta, int aMax) {
        for (int pi = 3; pi <= aMax; pi += 2) {
            BigInteger p = BigInteger.valueOf(pi);
            if (!p.isProbablePrime(20)) continue;
            if (delta.mod(p).signum() == 0) continue;       // p | D
            if (!legendreIsResidue(delta, p)) continue;     // (D|p) != 1
            BigInteger b1 = tonelli(delta.mod(p), p);
            if (b1 == null) continue;
            // lift to b == b1 (mod p), with b odd (since D == 1 mod 4)
            BigInteger b = b1.testBit(0) ? b1 : b1.add(p);
            BigInteger twoP = p.shiftLeft(1);
            b = b.mod(twoP);
            if (b.compareTo(p) > 0) b = b.subtract(twoP);    // into (-p, p]
            BigInteger fourP = FOUR.multiply(p);
            BigInteger num = b.multiply(b).subtract(delta);
            if (num.mod(fourP).signum() == 0) {
                return new Bqf(p, b, num.divide(fourP));
            }
        }
        return null;
    }

    /** True iff D is a quadratic residue mod odd prime p (Legendre symbol == 1). */
    private static boolean legendreIsResidue(BigInteger D, BigInteger p) {
        BigInteger ls = D.mod(p).modPow(p.subtract(ONE).shiftRight(1), p);
        return ls.equals(ONE);
    }

    /** Tonelli-Shanks: a square root of n mod odd prime p, or null if none. */
    static BigInteger tonelli(BigInteger n, BigInteger p) {
        n = n.mod(p);
        if (n.signum() == 0) return ZERO;
        if (!legendreIsResidue(n, p)) return null;
        if (p.mod(FOUR).equals(BigInteger.valueOf(3))) {
            return n.modPow(p.add(ONE).shiftRight(2), p);
        }
        BigInteger q = p.subtract(ONE);
        int s = 0;
        while (!q.testBit(0)) { q = q.shiftRight(1); s++; }
        BigInteger z = TWO;
        while (legendreIsResidue(z, p)) z = z.add(ONE);     // find a non-residue
        int m = s;
        BigInteger c = z.modPow(q, p);
        BigInteger t = n.modPow(q, p);
        BigInteger r = n.modPow(q.add(ONE).shiftRight(1), p);
        while (!t.equals(ONE)) {
            int i = 0;
            BigInteger t2 = t;
            while (!t2.equals(ONE)) { t2 = t2.multiply(t2).mod(p); i++; if (i == m) return null; }
            BigInteger b = c.modPow(ONE.shiftLeft(m - i - 1), p);
            m = i;
            c = b.multiply(b).mod(p);
            t = t.multiply(c).mod(p);
            r = r.multiply(b).mod(p);
        }
        return r;
    }

    /** Validate that a form has the expected discriminant (use in tests/asserts). */
    public static void requireDiscriminant(Bqf f, BigInteger expectedD) {
        if (!f.discriminant().equals(expectedD)) {
            throw new FormException("form discriminant " + f.discriminant()
                    + " != expected " + expectedD);
        }
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

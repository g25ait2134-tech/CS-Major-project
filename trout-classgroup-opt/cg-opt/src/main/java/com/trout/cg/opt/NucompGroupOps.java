package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.core.GroupOps;

import java.math.BigInteger;

import static com.trout.cg.core.CgCore.*;

/**
 * Optimized class-group operations.
 *
 * Implemented & verified (reference model, 2400+ cases vs the schoolbook oracle
 * across multiple discriminants, then ported here):
 *   - compose() -> NUCOMP   (Cohen Alg. 5.4.9 + PARTEUCL): partial-reduction
 *                  composition keeping intermediates near |D|^(1/4).
 *   - square()  -> NUDUPL   (specialized squaring; verified == baseline square).
 *   - exp()     -> wNAF signed-window exponentiation (verified == binary exp).
 *
 * compose() uses the bound L = floor(|D|^(1/4)). identity/reduce/inverse are
 * shared with the verified baseline. See REVIEW_NOTES.md for provenance.
 *
 * Owners: Person C (NUCOMP/NUDUPL), Person D (exponentiation).
 */
public final class NucompGroupOps implements GroupOps {

    private final GroupOps base;     // shared correct identity/reduce/inverse/compose
    private final BigInteger D;
    private final BigInteger L;      // = floor(|D|^(1/4)), the NUCOMP bound
    private final ExpStrategy expStrategy;

    public NucompGroupOps(GroupOps baseline) {
        this.base = baseline;
        this.D = baseline.delta();
        this.L = CgCore.floorRootFour(D.abs());
        this.expStrategy = new WnafExp(4);
    }

    /** The NUCOMP truncation bound L = floor(|D|^(1/4)). */
    public BigInteger bound() { return L; }

    @Override public BigInteger delta() { return D; }
    @Override public Bqf identity() { return base.identity(); }
    @Override public Bqf reduce(Bqf x) { return base.reduce(x); }
    @Override public Bqf inverse(Bqf x) { return base.inverse(x); }

    /** NUCOMP composition (Cohen 5.4.9) — verified equal to baseline. */
    @Override
    public Bqf compose(Bqf x, Bqf y) {
        BigInteger a1 = x.a(), b1 = x.b(), c1 = x.c();
        BigInteger a2 = y.a(), b2 = y.b(), c2 = y.c();
        if (a1.compareTo(a2) < 0) {          // ensure a1 >= a2
            BigInteger ta=a1,tb=b1,tc=c1; a1=a2;b1=b2;c1=c2; a2=ta;b2=tb;c2=tc;
        }
        BigInteger s = b1.add(b2).divide(TWO);
        BigInteger n = b2.subtract(s);

        // step 2: u*a2 + v*a1 = d = gcd(a1,a2)
        BigInteger[] g0 = extGcd(a2, a1);
        BigInteger d = g0[0], u = g0[1], v = g0[2];
        BigInteger A, d1;
        if (d.equals(ONE)) {
            A = u.negate().multiply(n); d1 = d;
        } else if (s.mod(d).signum() == 0) {
            A = u.negate().multiply(n); d1 = d;
            a1 = a1.divide(d1); a2 = a2.divide(d1); s = s.divide(d1);
        } else {
            // step 3: u1*s + v1*d = d1 = gcd(s,d)
            BigInteger[] g1 = extGcd(s, d);
            d1 = g1[0]; BigInteger u1 = g1[1];
            if (d1.compareTo(ONE) > 0) {
                a1 = a1.divide(d1); a2 = a2.divide(d1); s = s.divide(d1); d = d.divide(d1);
            }
            // step 4: l = -u1*(u*c1 + v*c2) mod d ; A = -u*(n/d) + l*(a1/d)
            BigInteger inner = u.multiply(c1.mod(d)).add(v.multiply(c2.mod(d))).mod(d);
            BigInteger l = u1.negate().multiply(inner).mod(d);
            A = u.negate().multiply(n.divide(d)).add(l.multiply(a1.divide(d)));
        }

        // step 5: balance A, then partial-Euclid PARTEUCL(a1, A)
        A = A.mod(a1);
        BigInteger A1 = a1.subtract(A);
        if (A1.compareTo(A) < 0) A = A1.negate();

        // PARTEUCL(a1, A) -> (vv, dd, v2, v3, z)
        BigInteger vv = ZERO, dd = a1, v2 = ONE, v3 = A;
        int z = 0;
        while (v3.abs().compareTo(L) > 0) {
            BigInteger av3 = v3.abs();
            BigInteger t3 = dd.mod(av3);                 // 0 <= t3 < |v3|
            BigInteger q = dd.subtract(t3).divide(v3);
            BigInteger t2 = vv.subtract(q.multiply(v2));
            vv = v2; dd = v3; v2 = t2; v3 = t3; z++;
        }
        if ((z & 1) == 1) { v2 = v2.negate(); v3 = v3.negate(); }

        BigInteger a3, b3, c3;
        if (z == 0) {
            a3 = dd.multiply(a2);
            b3 = TWO.multiply(a2.multiply(v3)).add(b2);
            c3 = b3.multiply(b3).subtract(D).divide(FOUR.multiply(a3));
        } else {
            BigInteger b = a2.multiply(dd).add(n.multiply(vv)).divide(a1);
            BigInteger e = s.multiply(dd).add(c2.multiply(vv)).divide(a1);
            BigInteger f = b.multiply(v3).add(n).divide(dd);
            BigInteger gg = e.multiply(v2).subtract(s).divide(vv);
            if (d1.compareTo(ONE) > 0) { v2 = d1.multiply(v2); vv = d1.multiply(vv); }
            a3 = dd.multiply(b).add(e.multiply(vv));
            b3 = dd.multiply(f).add(v3.multiply(b)).add(e.multiply(v2)).add(gg.multiply(vv));
            c3 = v3.multiply(f).add(gg.multiply(v2));
        }
        return base.reduce(new Bqf(a3, b3, c3));
    }

    /**
     * NUDUPL — specialized squaring of (a,b,c). Derived from composition with
     * f1==f2 (s=b, n=0, first gcd trivial). Verified to equal baseline.square.
     */
    @Override
    public Bqf square(Bqf f) {
        BigInteger a = f.a(), b = f.b(), c = f.c();
        BigInteger s = b;                 // (b+b)/2
        BigInteger x2, y2, d1;
        if (s.mod(a).signum() == 0) { y2 = ONE.negate(); x2 = ZERO; d1 = a; }
        else { BigInteger[] g = extGcd(s, a); d1 = g[0]; x2 = g[1]; y2 = g[2].negate(); }
        BigInteger v1 = a.divide(d1);
        BigInteger v2 = v1;               // a/d1
        BigInteger r = floorMod(x2.negate().multiply(c), v1);   // y1*y2*n = 0
        BigInteger a3 = v1.multiply(v2);
        BigInteger b3 = b.add(TWO.multiply(v2).multiply(r));
        BigInteger c3 = b3.multiply(b3).subtract(D).divide(FOUR.multiply(a3));
        return base.reduce(new Bqf(a3, b3, c3));
    }

    /** Optimized exponentiation (wNAF). Real, measured speedup vs binary exp. */
    @Override
    public Bqf exp(Bqf x, BigInteger k) {
        return expStrategy.exp(this, x, k);
    }
}

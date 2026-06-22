package com.trout.cg.baseline;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.core.GroupOps;

import java.math.BigInteger;

import static com.trout.cg.core.CgCore.*;

/**
 * Schoolbook (Gauss/Dirichlet) composition + standard reduction + binary
 * exponentiation. This is the CORRECTNESS REFERENCE (the "oracle"): clarity
 * over speed. The optimized module is validated by checking it produces the
 * identical reduced form as this implementation.
 *
 * Owner: Person B.  (Algorithms verified against identity / commutativity /
 * associativity property tests.)
 */
public final class SchoolbookGroupOps implements GroupOps {

    private final BigInteger D;
    private final Bqf identity;

    public SchoolbookGroupOps(BigInteger delta) {
        this.D = delta;
        this.identity = reduce(CgCore.principalForm(delta));
    }

    @Override public BigInteger delta() { return D; }
    @Override public Bqf identity() { return identity; }

    // (a, b, c) -> (a, b', c') with b' brought into (-a, a]
    private Bqf normalize(BigInteger a, BigInteger b, BigInteger c) {
        BigInteger twoA = TWO.multiply(a);
        BigInteger r = b.mod(twoA);                 // [0, 2a)
        if (r.compareTo(a) > 0) r = r.subtract(twoA);
        BigInteger k = b.subtract(r).divide(twoA);
        BigInteger c2 = c.subtract(k.multiply(b.add(r).divide(TWO)));
        return new Bqf(a, r, c2);
    }

    @Override
    public Bqf reduce(Bqf f) {
        BigInteger a = f.a(), b = f.b(), c = f.c();
        Bqf n = normalize(a, b, c);
        a = n.a(); b = n.b(); c = n.c();
        while (a.compareTo(c) > 0 || (a.equals(c) && b.signum() < 0)) {
            // rho step
            BigInteger na = c, nb = b.negate(), nc = a;
            Bqf nn = normalize(na, nb, nc);
            a = nn.a(); b = nn.b(); c = nn.c();
        }
        if ((a.equals(c) || a.equals(b.negate())) && b.signum() < 0) {
            b = b.negate();
        }
        return new Bqf(a, b, c);
    }

    @Override
    public Bqf compose(Bqf f1, Bqf f2) {
        BigInteger a1 = f1.a(), b1 = f1.b(), c1 = f1.c();
        BigInteger a2 = f2.a(), b2 = f2.b(), c2 = f2.c();
        if (a1.compareTo(a2) > 0) {
            BigInteger ta=a1,tb=b1,tc=c1; a1=a2;b1=b2;c1=c2; a2=ta;b2=tb;c2=tc;
        }
        BigInteger s = b1.add(b2).divide(TWO);
        BigInteger n = b2.subtract(s);

        BigInteger y1, d;
        if (a2.mod(a1).signum() == 0) { y1 = ZERO; d = a1; }
        else { BigInteger[] g = extGcd(a2, a1); d = g[0]; y1 = g[1]; }

        BigInteger x2, y2, d1;
        if (s.mod(d).signum() == 0) { y2 = ONE.negate(); x2 = ZERO; d1 = d; }
        else { BigInteger[] g = extGcd(s, d); d1 = g[0]; x2 = g[1]; y2 = g[2].negate(); }

        BigInteger v1 = a1.divide(d1);
        BigInteger v2 = a2.divide(d1);
        BigInteger r = floorMod(y1.multiply(y2).multiply(n).subtract(x2.multiply(c2)), v1);
        BigInteger a3 = v1.multiply(v2);
        BigInteger b3 = b2.add(TWO.multiply(v2).multiply(r));
        BigInteger c3 = b3.multiply(b3).subtract(D).divide(FOUR.multiply(a3));
        return reduce(new Bqf(a3, b3, c3));
    }

    @Override
    public Bqf square(Bqf x) {
        return compose(x, x);   // baseline has no specialized squaring
    }

    @Override
    public Bqf inverse(Bqf x) {
        return reduce(new Bqf(x.a(), x.b().negate(), x.c()));
    }

    @Override
    public Bqf exp(Bqf x, BigInteger k) {
        if (k.signum() == 0) return identity();
        Bqf base = x;
        if (k.signum() < 0) { base = inverse(x); k = k.negate(); }
        Bqf result = identity();
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            result = square(result);
            if (k.testBit(i)) result = compose(result, base);
        }
        return result;
    }
}

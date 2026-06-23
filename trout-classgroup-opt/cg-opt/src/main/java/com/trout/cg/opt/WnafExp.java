package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Width-w NAF (signed sliding window) exponentiation. Reduces the number of
 * non-squaring compositions from ~bitlen/2 (binary) to ~bitlen/(w+1).
 *
 * Performance-critical choices:
 *  - Odd powers stored in a flat array (no HashMap / Integer boxing in the loop).
 *  - Inverses for negative digits are PRECOMPUTED ONCE (not per digit), so a
 *    negative digit costs a single composition like a positive one.
 * Works over ANY GroupOps. Verified to equal binary exp.
 *
 * Owner: Person D.
 */
public final class WnafExp implements ExpStrategy {

    private final int w;

    public WnafExp(int window) {
        if (window < 2 || window > 8) throw new IllegalArgumentException("window 2..8");
        this.w = window;
    }

    /** Signed window NAF digits, least-significant first. */
    static int[] wnafDigits(BigInteger k, int w) {
        List<Integer> d = new ArrayList<>();
        BigInteger mod = BigInteger.ONE.shiftLeft(w);     // 2^w
        BigInteger half = BigInteger.ONE.shiftLeft(w - 1);
        while (k.signum() > 0) {
            int di;
            if (k.testBit(0)) {
                BigInteger m = k.mod(mod);
                if (m.compareTo(half) >= 0) m = m.subtract(mod);
                di = m.intValueExact();
                k = k.subtract(BigInteger.valueOf(di));
            } else {
                di = 0;
            }
            d.add(di);
            k = k.shiftRight(1);
        }
        int[] out = new int[d.size()];
        for (int i = 0; i < out.length; i++) out[i] = d.get(i);
        return out;
    }

    @Override
    public Bqf exp(GroupOps ops, Bqf base, BigInteger k) {
        if (k.signum() == 0) return ops.identity();
        if (k.signum() < 0) { base = ops.inverse(base); k = k.negate(); }

        // odd powers base^1, base^3, ..., base^(2^(w-1)-1) and their inverses.
        // pos[i] = base^(2i+1); neg[i] = base^-(2i+1). cnt = 2^(w-2).
        int cnt = 1 << (w - 2);
        Bqf[] pos = new Bqf[cnt];
        Bqf[] neg = new Bqf[cnt];
        pos[0] = base;
        Bqf base2 = ops.square(base);
        for (int i = 1; i < cnt; i++) pos[i] = ops.compose(pos[i - 1], base2);
        for (int i = 0; i < cnt; i++) neg[i] = ops.inverse(pos[i]);   // ONCE, not per digit

        int[] digs = wnafDigits(k, w);
        Bqf r = ops.identity();
        for (int i = digs.length - 1; i >= 0; i--) {
            r = ops.square(r);
            int di = digs[i];
            if (di > 0)      r = ops.compose(r, pos[(di - 1) >> 1]);
            else if (di < 0) r = ops.compose(r, neg[(-di - 1) >> 1]);
        }
        return r;
    }
}

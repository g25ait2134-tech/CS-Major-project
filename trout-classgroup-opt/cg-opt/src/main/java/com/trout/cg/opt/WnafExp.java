package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Width-w NAF (signed sliding window) exponentiation. Uses ops.inverse for
 * negative digits, halving the number of compositions vs binary on average.
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

        // precompute odd powers base^1, base^3, ..., base^(2^(w-1)-1)
        Map<Integer, Bqf> odd = new HashMap<>();
        odd.put(1, base);
        Bqf b2 = ops.square(base);
        int top = (1 << (w - 1));
        for (int i = 3; i < top; i += 2) odd.put(i, ops.compose(odd.get(i - 2), b2));

        int[] digs = wnafDigits(k, w);
        Bqf r = ops.identity();
        for (int i = digs.length - 1; i >= 0; i--) {
            r = ops.square(r);
            int di = digs[i];
            if (di > 0) r = ops.compose(r, odd.get(di));
            else if (di < 0) r = ops.compose(r, ops.inverse(odd.get(-di)));
        }
        return r;
    }
}

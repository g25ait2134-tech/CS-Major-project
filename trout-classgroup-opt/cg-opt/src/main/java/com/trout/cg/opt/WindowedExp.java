package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-window (2^w) exponentiation. Works over ANY GroupOps, so it benefits
 * automatically once NUCOMP/NUDUPL are implemented. Correct as written; the
 * stretch goal (wNAF, multi-exp) builds on this.
 *
 * Owner: Person D.
 */
public final class WindowedExp implements ExpStrategy {

    private final int w;

    public WindowedExp(int window) {
        if (window < 1 || window > 8) throw new IllegalArgumentException("window 1..8");
        this.w = window;
    }

    @Override
    public Bqf exp(GroupOps ops, Bqf base, BigInteger k) {
        if (k.signum() == 0) return ops.identity();
        if (k.signum() < 0) { base = ops.inverse(base); k = k.negate(); }

        // Precompute odd powers base^1, base^3, ..., base^(2^w - 1)
        int tableSize = 1 << (w - 1);
        List<Bqf> odd = new ArrayList<>(tableSize);
        Bqf b2 = ops.square(base);
        odd.add(base);                       // base^1
        for (int i = 1; i < tableSize; i++) {
            odd.add(ops.compose(odd.get(i - 1), b2));   // base^(2i+1)
        }

        Bqf result = ops.identity();
        int i = k.bitLength() - 1;
        while (i >= 0) {
            if (!k.testBit(i)) {
                result = ops.square(result);
                i--;
            } else {
                int lo = Math.max(0, i - w + 1);
                while (!k.testBit(lo)) lo++;     // trim trailing zeros of the window
                int width = i - lo + 1;
                int val = 0;
                for (int j = i; j >= lo; j--) val = (val << 1) | (k.testBit(j) ? 1 : 0);
                for (int s = 0; s < width; s++) result = ops.square(result);
                result = ops.compose(result, odd.get((val - 1) >> 1));
                i = lo - 1;
            }
        }
        return result;
    }
}

package com.trout.cg.opt;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;
import java.math.BigInteger;
import java.util.List;

/**
 * Multi-exponentiation: product of base_i ^ k_i.
 * STATUS: simple interleaved (Straus-style) implementation — correct but not
 * yet bucketed. STRETCH (Person D): replace with Pippenger buckets.
 */
public final class MultiExp {

    public Bqf multiExp(GroupOps ops, List<Bqf> bases, List<BigInteger> ks) {
        if (bases.size() != ks.size()) throw new IllegalArgumentException("size mismatch");
        int maxBits = 0;
        for (BigInteger k : ks) maxBits = Math.max(maxBits, k.bitLength());
        Bqf acc = ops.identity();
        for (int i = maxBits - 1; i >= 0; i--) {
            acc = ops.square(acc);
            for (int j = 0; j < bases.size(); j++) {
                if (ks.get(j).testBit(i)) acc = ops.compose(acc, bases.get(j));
            }
        }
        return acc;
    }
}

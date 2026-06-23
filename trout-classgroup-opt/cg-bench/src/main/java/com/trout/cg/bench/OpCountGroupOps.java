package com.trout.cg.bench;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.GroupOps;

import java.math.BigInteger;

/**
 * A GroupOps decorator that COUNTS the group operations performed. Used to
 * explain benchmark results mechanistically: e.g. how many compositions a
 * binary vs wNAF exponentiation actually issues. Not used in the hot
 * measurement path — only for the OpCountMain report.
 */
public final class OpCountGroupOps implements GroupOps {

    private final GroupOps d;
    public long composes, squares, inverses;

    public OpCountGroupOps(GroupOps delegate) { this.d = delegate; }

    public void reset() { composes = squares = inverses = 0; }
    public long total() { return composes + squares; }   // multiplications-equivalent

    @Override public Bqf compose(Bqf x, Bqf y) { composes++; return d.compose(x, y); }
    @Override public Bqf square(Bqf x)         { squares++;  return d.square(x); }
    @Override public Bqf inverse(Bqf x)        { inverses++; return d.inverse(x); }

    @Override public Bqf identity()            { return d.identity(); }
    @Override public Bqf reduce(Bqf x)         { return d.reduce(x); }
    @Override public Bqf exp(Bqf x, BigInteger k) { return d.exp(x, k); }
    @Override public BigInteger delta()        { return d.delta(); }
}

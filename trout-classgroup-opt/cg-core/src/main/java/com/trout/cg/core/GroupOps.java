package com.trout.cg.core;

import java.math.BigInteger;

/**
 * The class-group operations. BOTH the schoolbook baseline and the optimized
 * NUCOMP/NUDUPL implementation satisfy this interface, so the differential
 * oracle and the JMH benchmark can drive either one interchangeably.
 */
public interface GroupOps {

    /** Identity element (the principal form) for this discriminant. */
    Bqf identity();

    /** Group operation: compose two forms. */
    Bqf compose(Bqf x, Bqf y);

    /** Square a form (compose with itself). */
    Bqf square(Bqf x);

    /** Reduce a form to its canonical representative. */
    Bqf reduce(Bqf x);

    /** Scalar exponentiation x^k. */
    Bqf exp(Bqf x, BigInteger k);

    /** Inverse of a form: (a, -b, c) reduced. */
    Bqf inverse(Bqf x);

    /** The fixed discriminant D of this group. */
    BigInteger delta();

    /** Convenience: group equality = equality of reduced representatives. */
    default boolean eq(Bqf x, Bqf y) {
        return reduce(x).equals(reduce(y));
    }
}

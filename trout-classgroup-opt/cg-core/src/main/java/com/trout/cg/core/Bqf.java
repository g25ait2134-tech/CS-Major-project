package com.trout.cg.core;

import java.math.BigInteger;

/**
 * A binary quadratic form (a, b, c) of fixed discriminant D = b^2 - 4ac.
 * Represents one element of the class group.
 *
 * NOTE: equals() here is STRUCTURAL (component-wise). Two forms are equal as
 * group elements iff they have the same REDUCED representative, so compare
 * ops.reduce(x) with ops.reduce(y) for group equality (see GroupOps).
 */
public record Bqf(BigInteger a, BigInteger b, BigInteger c) {

    private static final BigInteger FOUR = BigInteger.valueOf(4);

    /** Build a form from (a, b) and the discriminant, computing c = (b^2 - D)/(4a). */
    public static Bqf of(BigInteger a, BigInteger b, BigInteger delta) {
        BigInteger num = b.multiply(b).subtract(delta);
        BigInteger den = FOUR.multiply(a);
        if (!num.remainder(den).equals(BigInteger.ZERO)) {
            throw new FormException("(b^2 - D) not divisible by 4a; invalid form");
        }
        return new Bqf(a, b, num.divide(den));
    }

    /** Discriminant b^2 - 4ac (should equal the group's fixed D). */
    public BigInteger discriminant() {
        return b.multiply(b).subtract(FOUR.multiply(a).multiply(c));
    }

    @Override
    public String toString() {
        return "(" + a + ", " + b + ", " + c + ")";
    }
}

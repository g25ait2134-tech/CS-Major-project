package com.trout.cg.core;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class CgCoreTest {

    @Test void fixedWidthRoundTrips() {
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 100; i++) {
            BigInteger v = new BigInteger(200, rng);
            byte[] enc = CgCore.toFixed(v, 32);
            assertEquals(32, enc.length);
            assertEquals(v, CgCore.fromFixed(enc));
        }
    }

    @Test void floorDivMatchesMathFloor() {
        assertEquals(BigInteger.valueOf(-3), CgCore.floorDiv(BigInteger.valueOf(-7), BigInteger.valueOf(3)));
        assertEquals(BigInteger.valueOf(2),  CgCore.floorDiv(BigInteger.valueOf(7),  BigInteger.valueOf(3)));
    }

    @Test void fourthRootBounds() {
        BigInteger n = BigInteger.TEN.pow(40);
        BigInteger r = CgCore.floorRootFour(n);
        assertTrue(r.pow(4).compareTo(n) <= 0);
        assertTrue(r.add(BigInteger.ONE).pow(4).compareTo(n) > 0);
    }

    @Test void discriminantIsOneMod4() {
        BigInteger D = CgCore.genDiscriminant(80, new SecureRandom());
        assertTrue(D.signum() < 0);
        assertEquals(BigInteger.ONE, D.mod(BigInteger.valueOf(4)));
    }
}

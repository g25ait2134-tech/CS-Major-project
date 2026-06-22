package com.trout.cg.test;

import com.trout.cg.baseline.SchoolbookGroupOps;
import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.core.GroupOps;
import com.trout.cg.opt.NucompGroupOps;
import com.trout.cg.opt.WindowedExp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The correctness backbone: the optimized NUCOMP/NUDUPL path must produce the
 * IDENTICAL reduced form as the schoolbook baseline. While NucompGroupOps still
 * delegates to the baseline these pass trivially; once Person C implements real
 * NUCOMP they become the real proof of correctness. Keep them green at all times.
 */
class DifferentialOracleTest {

    static BigInteger D;
    static SchoolbookGroupOps base;
    static NucompGroupOps opt;
    static List<Bqf> forms = new ArrayList<>();

    @BeforeAll
    static void setup() {
        // deterministic seed for reproducible failures
        D = CgCore.genDiscriminant(256, new SecureRandom(new byte[]{7,7,7,7}));
        base = new SchoolbookGroupOps(D);
        opt = new NucompGroupOps(base);
        Bqf gen = base.reduce(CgCore.smallForm(D, 6000));
        Bqf cur = gen;
        for (int i = 0; i < 60; i++) { cur = base.compose(cur, gen); forms.add(cur); }
    }

    @Test void compositionMatchesBaseline() {
        Random r = new Random(1);
        for (int i = 0; i < 500; i++) {
            Bqf x = forms.get(r.nextInt(forms.size()));
            Bqf y = forms.get(r.nextInt(forms.size()));
            assertTrue(base.eq(base.compose(x, y), opt.compose(x, y)),
                    "compose mismatch at i=" + i);
        }
    }

    @Test void squaringMatchesBaseline() {
        for (Bqf x : forms) {
            assertTrue(base.eq(base.square(x), opt.square(x)), "square mismatch");
        }
    }

    @Test void windowedExpMatchesBinary() {
        WindowedExp we = new WindowedExp(4);
        Random r = new Random(2);
        for (int i = 0; i < 100; i++) {
            Bqf x = forms.get(r.nextInt(forms.size()));
            BigInteger k = BigInteger.valueOf(r.nextInt(1_000_000));
            assertTrue(base.eq(base.exp(x, k), we.exp(base, x, k)), "exp mismatch k=" + k);
        }
    }

    @Test void edgeCases() {
        Bqf id = base.identity();
        Bqf x = forms.get(0);
        assertTrue(base.eq(base.compose(id, x), x));          // identity
        assertTrue(base.eq(base.compose(x, base.inverse(x)), id)); // inverse
        assertTrue(base.eq(base.exp(x, BigInteger.ZERO), id));     // x^0 = 1
        assertTrue(base.eq(base.exp(x, BigInteger.ONE), x));       // x^1 = x
    }
}

package com.trout.cg.test;

import com.trout.cg.baseline.SchoolbookGroupOps;
import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.core.GroupOps;
import com.trout.cg.opt.NucompGroupOps;
import com.trout.cg.opt.WindowedExp;
import com.trout.cg.opt.WnafExp;
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
 * IDENTICAL reduced form as the schoolbook baseline. NUCOMP (Cohen 5.4.9) and
 * NUDUPL are real implementations, so these tests are the proof of correctness.
 * Forms are built from several distinct prime generators so both the gcd==1 and
 * gcd>1 branches (and the z==0 / z>0 partial-reduction cases) get exercised.
 * Keep them green at all times.
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

        // several distinct prime-form generators -> generic composites
        List<Bqf> gens = new ArrayList<>();
        for (int cap = 2000; gens.size() < 8 && cap <= 200000; cap *= 2) {
            Bqf g = CgCore.smallForm(D, cap);
            if (g != null) { Bqf rg = base.reduce(g); if (!gens.contains(rg)) gens.add(rg); }
        }
        Random r = new Random(99);
        for (int i = 0; i < 60; i++) {
            Bqf cur = gens.get(r.nextInt(gens.size()));
            int steps = 2 + r.nextInt(25);                 // varied size -> large a, z>0
            for (int j = 0; j < steps; j++) cur = base.compose(cur, gens.get(r.nextInt(gens.size())));
            forms.add(cur);
        }
        // also add GENERIC forms (leading coeff ~ |D|^(1/2)) to exercise the
        // NUCOMP z>0 partial-reduction path and wNAF on realistic elements.
        java.util.Random fr = new java.util.Random(2024);
        for (int i = 0; i < 20; i++) forms.add(base.reduce(CgCore.genericForm(D, fr)));
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

    @Test void nuduplMatchesBaselineSquare() {
        for (Bqf x : forms) {
            assertTrue(base.eq(base.square(x), opt.square(x)), "NUDUPL != baseline square");
        }
    }

    @Test void wnafMatchesBinary() {
        WnafExp we = new WnafExp(4);
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 100; i++) {
            Bqf x = forms.get(r.nextInt(forms.size()));
            BigInteger k = BigInteger.valueOf(Math.abs(r.nextLong()));
            assertTrue(base.eq(base.exp(x, k), we.exp(base, x, k)), "wNAF != binary k=" + k);
        }
    }

}

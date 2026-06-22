package com.trout.cg.baseline;

import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property tests that establish SchoolbookGroupOps as a trustworthy oracle:
 * group laws + discriminant invariance. (Algorithms were also cross-validated
 * before commit.)
 */
class SchoolbookGroupOpsTest {

    static BigInteger D;
    static SchoolbookGroupOps ops;
    static List<Bqf> forms = new ArrayList<>();

    @BeforeAll
    static void setup() {
        D = CgCore.genDiscriminant(160, new SecureRandom(new byte[]{9,9,9,9}));
        ops = new SchoolbookGroupOps(D);
        Bqf g = ops.reduce(CgCore.smallForm(D, 8000));
        assertNotNull(g, "seed form must exist");
        Bqf cur = g;
        for (int i = 0; i < 40; i++) { cur = ops.compose(cur, g); forms.add(cur); }
    }

    @Test void identityIsNeutral() {
        Bqf id = ops.identity();
        for (Bqf f : forms) assertTrue(ops.eq(ops.compose(id, f), f));
    }

    @Test void inverseInverts() {
        Bqf id = ops.identity();
        for (Bqf f : forms) assertTrue(ops.eq(ops.compose(f, ops.inverse(f)), id));
    }

    @Test void commutative() {
        Random r = new Random(11);
        for (int i = 0; i < 100; i++) {
            Bqf a = forms.get(r.nextInt(forms.size())), b = forms.get(r.nextInt(forms.size()));
            assertTrue(ops.eq(ops.compose(a, b), ops.compose(b, a)));
        }
    }

    @Test void associative() {
        Random r = new Random(12);
        for (int i = 0; i < 100; i++) {
            Bqf a = forms.get(r.nextInt(forms.size())),
                b = forms.get(r.nextInt(forms.size())),
                c = forms.get(r.nextInt(forms.size()));
            assertTrue(ops.eq(ops.compose(ops.compose(a, b), c),
                              ops.compose(a, ops.compose(b, c))));
        }
    }

    @Test void expHomomorphism() {
        Random r = new Random(13);
        for (int i = 0; i < 50; i++) {
            Bqf x = forms.get(r.nextInt(forms.size()));
            BigInteger j = BigInteger.valueOf(r.nextInt(2000));
            BigInteger k = BigInteger.valueOf(r.nextInt(2000));
            assertTrue(ops.eq(ops.compose(ops.exp(x, j), ops.exp(x, k)), ops.exp(x, j.add(k))));
        }
    }

    @Test void discriminantInvariant() {
        for (Bqf f : forms) assertEquals(D, f.discriminant());
    }
}

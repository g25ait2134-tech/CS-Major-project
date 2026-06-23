package com.trout.cg.bench;

import com.trout.cg.baseline.SchoolbookGroupOps;
import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.opt.WindowedExp;
import com.trout.cg.opt.WnafExp;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Explains the exponentiation benchmark mechanistically by COUNTING the group
 * operations each strategy issues for the same (base, exponent), all driven
 * through one counter so the comparison is apples-to-apples.
 *
 * Run:  java -cp cg-bench/target/benchmarks.jar com.trout.cg.bench.OpCountMain [bits] [windowWidth]
 */
public final class OpCountMain {

    /** Local binary square-and-multiply over the counted ops (so binary is counted too). */
    static Bqf binaryExp(OpCountGroupOps ops, Bqf base, BigInteger k) {
        if (k.signum() == 0) return ops.identity();
        Bqf r = ops.identity();
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            r = ops.square(r);
            if (k.testBit(i)) r = ops.compose(r, base);
        }
        return r;
    }

    public static void main(String[] args) {
        int bits = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        SecureRandom rng = new SecureRandom(new byte[]{(byte) bits, 4, 2});
        BigInteger D = CgCore.genDiscriminant(bits, rng);
        SchoolbookGroupOps base = new SchoolbookGroupOps(D);
        OpCountGroupOps c = new OpCountGroupOps(base);

        java.util.Random fr = new java.util.Random(12345);

        int trials = 64;
        long binCompose = 0, binSquare = 0, winCompose = 0, winSquare = 0, wnafCompose = 0, wnafSquare = 0, wnafInv = 0;
        WindowedExp win = new WindowedExp(w);
        WnafExp wnaf = new WnafExp(w);

        for (int t = 0; t < trials; t++) {
            BigInteger k = new BigInteger(256, rng);
            Bqf b = base.reduce(CgCore.genericForm(D, fr));

            c.reset(); binaryExp(c, b, k);
            binCompose += c.composes; binSquare += c.squares;

            c.reset(); win.exp(c, b, k);
            winCompose += c.composes; winSquare += c.squares;

            c.reset(); wnaf.exp(c, b, k);
            wnafCompose += c.composes; wnafSquare += c.squares; wnafInv += c.inverses;
        }

        System.out.printf("Operation counts averaged over %d random 256-bit exponents%n", trials);
        System.out.printf("  D = %d bits, window w = %d%n%n", bits, w);
        System.out.printf("%-10s %12s %12s %12s%n", "method", "composes", "squares", "total mults");
        report("binary",  binCompose, binSquare, trials);
        report("windowed", winCompose, winSquare, trials);
        report("wNAF",    wnafCompose, wnafSquare, trials);
        System.out.printf("%nwNAF also did %.1f inverses/exp (cheap: negate b + reduce).%n",
                wnafInv / (double) trials);
        double binTot = (binCompose + binSquare) / (double) trials;
        double wnafTot = (wnafCompose + wnafSquare) / (double) trials;
        System.out.printf("Expected wNAF speedup from op count alone: %.1f%% fewer mults.%n",
                100.0 * (1 - wnafTot / binTot));
        System.out.println("(Squarings dominate and are equal across methods, so the ceiling is modest.)");
    }

    static void report(String name, long compose, long square, int trials) {
        System.out.printf("%-10s %12.1f %12.1f %12.1f%n",
                name, compose / (double) trials, square / (double) trials,
                (compose + square) / (double) trials);
    }
}

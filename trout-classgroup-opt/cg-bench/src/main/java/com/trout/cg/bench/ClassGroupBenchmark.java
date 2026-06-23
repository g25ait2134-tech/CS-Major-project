package com.trout.cg.bench;

import com.trout.cg.baseline.SchoolbookGroupOps;
import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.core.GroupOps;
import com.trout.cg.opt.NucompGroupOps;
import com.trout.cg.opt.WindowedExp;
import com.trout.cg.opt.WnafExp;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark across discriminant sizes.
 *
 * METHODOLOGY (why this differs from a naive harness):
 *  - VARYING INPUTS: a pool of distinct random forms/exponents is generated up
 *    front, and each invocation indexes a different element. Reusing one fixed
 *    (x, y) lets the JIT fold the work away, producing meaningless times that do
 *    not scale with bit size. Varying inputs prevents that.
 *  - BATCHED + CHAINED: compose/square are single-microsecond ops, far below the
 *    per-invocation overhead floor. Each @Benchmark does BATCH chained ops (the
 *    output feeds the next input, so a data dependency blocks dead-code
 *    elimination) and uses @OperationsPerInvocation(BATCH) so JMH reports the
 *    honest per-op cost.
 *  - SANITY: per-op cost MUST grow with bit size. If it doesn't, the measurement
 *    is broken, not the algorithm. (See OpCountMain for the mechanism behind the
 *    exponentiation results.)
 *
 * Run (after `mvn clean package`):
 *   java -jar cg-bench/target/benchmarks.jar -rf csv -rff report/results.csv
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(1)
public class ClassGroupBenchmark {

    /** Ops per compose/square invocation (chained). Power of two for masking. */
    static final int BATCH = 64;
    /** Size of the input pool. Power of two for cheap index masking. */
    static final int POOL = 64;
    static final int MASK = POOL - 1;

    @Param({"512", "1024", "2048", "3072"})
    public int bits;

    private GroupOps baseline;
    private NucompGroupOps optimized;
    private WindowedExp windowed;
    private WnafExp wnaf;

    private Bqf[] pool;            // distinct random forms
    private BigInteger[] exps;     // distinct 256-bit exponents
    private int idx;              // rolling index (varies inputs per invocation)

    @Setup(Level.Trial)
    public void setup() {
        SecureRandom rng = new SecureRandom(new byte[]{(byte) bits, 1, 2, 3});
        BigInteger D = CgCore.genDiscriminant(bits, rng);
        baseline = new SchoolbookGroupOps(D);
        optimized = new NucompGroupOps(baseline);
        windowed = new WindowedExp(5);
        wnaf = new WnafExp(5);

        // GENERIC forms (leading coeff ~ |D|^(1/2)) so compose and square are
        // comparably sized. A small-a pool makes compose artificially cheap and
        // hides the exponentiation speedup. java.util.Random is fine here.
        java.util.Random fr = new java.util.Random(0xC0FFEE ^ bits);
        pool = new Bqf[POOL];
        exps = new BigInteger[POOL];
        for (int i = 0; i < POOL; i++) {
            pool[i] = baseline.reduce(CgCore.genericForm(D, fr));
            exps[i] = new BigInteger(256, rng);
        }
    }

    private int next() { return (idx++) & MASK; }

    // ---- composition: BATCH chained ops over varying inputs ----
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public Bqf composeSchoolbook() {
        Bqf acc = pool[next()];
        for (int i = 0; i < BATCH; i++) acc = baseline.compose(acc, pool[(idx + i) & MASK]);
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public Bqf composeNucomp() {
        Bqf acc = pool[next()];
        for (int i = 0; i < BATCH; i++) acc = optimized.compose(acc, pool[(idx + i) & MASK]);
        return acc;
    }

    // ---- squaring: BATCH chained ops (input changes every step) ----
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public Bqf squareSchoolbook() {
        Bqf acc = pool[next()];
        for (int i = 0; i < BATCH; i++) acc = baseline.square(acc);
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public Bqf squareNudupl() {
        Bqf acc = pool[next()];
        for (int i = 0; i < BATCH; i++) acc = optimized.square(acc);
        return acc;
    }

    // ---- exponentiation (headline): one heavy op per invocation, varying inputs ----
    @Benchmark
    public Bqf expBinary()   { int i = next(); return baseline.exp(pool[i], exps[i]); }
    @Benchmark
    public Bqf expWindowed() { int i = next(); return windowed.exp(baseline, pool[i], exps[i]); }
    @Benchmark
    public Bqf expWnaf()     { int i = next(); return wnaf.exp(baseline, pool[i], exps[i]); }
}

package com.trout.cg.bench;

import com.trout.cg.baseline.SchoolbookGroupOps;
import com.trout.cg.core.Bqf;
import com.trout.cg.core.CgCore;
import com.trout.cg.opt.NucompGroupOps;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing schoolbook vs NUCOMP/NUDUPL across discriminant sizes.
 *
 * Run (after `mvn package`):
 *   java -jar cg-bench/target/benchmarks.jar -rf csv -rff results.csv
 *
 * While NucompGroupOps delegates to the baseline, the two rows will read about
 * the same. Once real NUCOMP/NUDUPL land, the gap should widen with bit size.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class ClassGroupBenchmark {

    @Param({"512", "1024", "2048", "3072"})
    public int bits;

    private SchoolbookGroupOps baseline;
    private NucompGroupOps optimized;
    private Bqf x, y;

    @Setup(Level.Trial)
    public void setup() {
        // fixed seed per size for identical inputs across both implementations
        SecureRandom rng = new SecureRandom(new byte[]{(byte) bits, 1, 2, 3});
        BigInteger D = CgCore.genDiscriminant(bits, rng);
        baseline = new SchoolbookGroupOps(D);
        optimized = new NucompGroupOps(baseline);
        Bqf g = baseline.reduce(CgCore.smallForm(D, 8000));
        x = baseline.compose(g, g);
        for (int i = 0; i < 8; i++) x = baseline.compose(x, g);
        y = baseline.compose(x, g);
    }

    @Benchmark public void composeSchoolbook(Blackhole bh) { bh.consume(baseline.compose(x, y)); }
    @Benchmark public void composeNucomp(Blackhole bh)     { bh.consume(optimized.compose(x, y)); }
    @Benchmark public void squareSchoolbook(Blackhole bh)  { bh.consume(baseline.square(x)); }
    @Benchmark public void squareNudupl(Blackhole bh)      { bh.consume(optimized.square(x)); }
}

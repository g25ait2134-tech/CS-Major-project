# cg-report

Analysis assets (not a Maven module).

1. Build and run the benchmark from the project root:
   ```
   mvn -q clean package
   java -jar cg-bench/target/benchmarks.jar -rf csv -rff report/results.csv
   ```
2. Plot:
   ```
   pip install matplotlib
   python report/plot_results.py report/results.csv
   ```
3. Drop `compose_speedup.png` / `square_speedup.png` and the speedup table into
   the slides and the final report. State the honesty caveat: NUCOMP is a known
   algorithm; the contribution is the controlled Java before/after measurement.

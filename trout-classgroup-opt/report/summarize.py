#!/usr/bin/env python3
"""Turn JMH CSV output into a clean speedup table.
Usage:  python report/summarize.py report/results.csv
JMH avg-time mode => lower is better; speedup = baseline / optimized."""
import csv, sys
from collections import defaultdict

path = sys.argv[1] if len(sys.argv) > 1 else "report/results.csv"
rows = list(csv.DictReader(open(path)))

def col(r, *names):
    for n in names:
        for k in r:
            if k.strip().lower() == n: return r[k]
    return None

data = defaultdict(dict)   # bits -> method -> score
for r in rows:
    bench = col(r, "benchmark").split(".")[-1]
    bits = int(col(r, "param: bits", "bits"))
    score = float(col(r, "score"))
    data[bits][bench] = score

pairs = [("Compose", "composeSchoolbook", "composeNucomp"),
         ("Square",  "squareSchoolbook",  "squareNudupl"),
         ("Exp wNAF",     "expBinary", "expWnaf"),
         ("Exp windowed", "expBinary", "expWindowed")]

print(f"{'operation':14}{'bits':>6}{'baseline':>14}{'optimized':>14}{'speedup':>10}")
print("-" * 58)
for label, base, opt in pairs:
    for bits in sorted(data):
        d = data[bits]
        if base in d and opt in d and d[opt] > 0:
            sp = d[base] / d[opt]
            tag = "x" if sp >= 1 else "x (slower)"
            print(f"{label:14}{bits:>6}{d[base]:>14.3f}{d[opt]:>14.3f}{sp:>9.2f}{tag[0]}")
    print()
print("Lower score = faster (avg time/op). Speedup > 1.0 means the optimized")
print("variant is faster. NUCOMP's advantage is expected to grow with bit size.")

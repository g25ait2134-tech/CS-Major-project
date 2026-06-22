#!/usr/bin/env python3
"""Turn JMH CSV output into speedup charts.
Usage:  python plot_results.py results.csv
Produces compose_speedup.png and square_speedup.png."""
import sys, csv, collections
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

def load(path):
    rows = list(csv.DictReader(open(path)))
    # JMH csv columns include: "Benchmark","Param: bits","Score"
    data = collections.defaultdict(dict)   # bench -> {bits: score}
    for r in rows:
        bench = r["Benchmark"].split(".")[-1]
        bits = int(r.get("Param: bits") or r.get('"Param: bits"') or 0)
        score = float(r["Score"])
        data[bench][bits] = score
    return data

def chart(data, base, opt, title, out):
    sizes = sorted(set(data[base]) & set(data[opt]))
    if not sizes:
        print("no data for", base, opt); return
    base_t = [data[base][s] for s in sizes]
    opt_t  = [data[opt][s] for s in sizes]
    speed  = [b/o if o else 0 for b, o in zip(base_t, opt_t)]
    fig, ax1 = plt.subplots(figsize=(7,4))
    ax1.plot(sizes, base_t, "o-", label=base)
    ax1.plot(sizes, opt_t, "s-", label=opt)
    ax1.set_xlabel("discriminant size (bits)"); ax1.set_ylabel("avg time (us)")
    ax1.set_yscale("log"); ax1.legend(loc="upper left"); ax1.set_title(title)
    ax2 = ax1.twinx(); ax2.plot(sizes, speed, "d--", color="green", label="speedup")
    ax2.set_ylabel("speedup (x)")
    fig.tight_layout(); fig.savefig(out, dpi=140)
    print("wrote", out, "speedups:", [round(s,2) for s in speed])

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    d = load(sys.argv[1])
    chart(d, "composeSchoolbook", "composeNucomp", "Composition: schoolbook vs NUCOMP", "compose_speedup.png")
    chart(d, "squareSchoolbook", "squareNudupl", "Squaring: schoolbook vs NUDUPL", "square_speedup.png")

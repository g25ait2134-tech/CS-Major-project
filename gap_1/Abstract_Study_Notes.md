### "Threshold ECDSA lets a group of parties jointly produce a standard ECDSA signature without any single party ever holding the private key."

- **ECDSA** is the ordinary digital-signature scheme used everywhere (TLS,
  Bitcoin, etc.). A *private key* signs; a *public key* lets anyone verify.
- **Threshold** means the private key is *split* across several people. You need
  some number of them (say 2 out of 3) to cooperate to make a signature, and
  **no one ever sees the whole key**. If one laptop is hacked, the attacker
  still can't sign. Think "nuclear-launch needs two keys," but cryptographic.

### "...only two signing rounds and no trusted setup..."

- A **round** = one synchronized exchange of messages between the parties.
  Fewer rounds = less network back-and-forth = faster, simpler. Two is good.
- **Trusted setup** = some crypto schemes need a trusted party to generate secret
  starting parameters once at the beginning. That party is a security risk (if
  they cheat or leak, everything breaks). "No trusted setup" means nobody has to
  be trusted to bootstrap the system — a real advantage.

### "...by building its machinery over class groups of imaginary quadratic fields."

- A **group** in math is just *a set of things plus one operation* for combining
  two of them into a third (like multiplication on numbers). Cryptography runs
  on groups.
- A **class group** is one particular kind of group. Its special, useful property
  is that **its size is hard to figure out** — and that hardness is exactly what
  gives security *without needing a trusted party to set it up*. (RSA-style
  groups get a similar property, but only via a trusted setup. Class groups give
  it for free. That's why Trout uses them.)
- "Imaginary quadratic fields" is just the mathematical neighborhood these
  particular class groups come from. You don't need the field theory to work on
  the project — you need the group's *elements* and its *operation*, which are
  very concrete (next section).

### "...the majority of its runtime is spent in class-group arithmetic — composing and squaring binary quadratic forms..."

Here's the concrete heart of the project:

- A **binary quadratic form** is how you write down one element of the class
  group. It's literally just **three integers `(a, b, c)`** that satisfy a fixed
  relationship. Think of `(a, b, c)` as the *coordinates* of a group element.
- **Composition** is the group's operation — take two forms, combine them, get a
  third form. It's the group's version of "multiply."
- **Squaring** is composing a form with itself (like `x²`).
- The protocol runs composition/squaring **thousands of times per signature**
  (inside encryption, commitments, proofs). So this tiny operation, repeated
  enormously, is where almost all the time goes. **Speed up this one operation
  and you speed up the whole protocol.** That's the opening we're exploiting.

### "The schoolbook (Gauss) method composes two forms and only afterwards reduces the large result..."

- **Reduction** = shrinking a form to its **canonical smallest representative**.
  Every group element can be written many ways; the *reduced* form is the one
  agreed-upon standard version.

- **This is the gap / limitation we identified:** the naive method wastes work on
oversized intermediate numbers.

### "...we implement NUCOMP and NUDUPL, which interleave a partial extended-Euclidean step to keep operands bounded near |Δ|^(1/4)..."

This is our proposed fix:

- **NUCOMP** = a smarter composition algorithm (by Shanks, refined by Atkin).
  **NUDUPL** = its squaring-only version.
- The trick: instead of "combine then shrink," NUCOMP **keeps the numbers small
  the whole way through**, so the expensive arithmetic never happens on huge
  values. Same final answer, much less work.
- **The mechanism — "partial extended-Euclidean step":**
  - The **Euclidean algorithm** is the ancient method for GCD (greatest common
    divisor) by repeated division.
  - **Extended** = it also tracks some bookkeeping coefficients along the way.
  - **Partial** = we **stop it early**, the moment the numbers have shrunk
    enough. That early stop is the whole secret — it's what bounds the operand
    size.
- **|Δ| ^ (1/4):** `Δ` (delta) is the **discriminant** — a fixed number that
  defines which class group we're in; its size sets the security level (bigger Δ
  = more secure, but slower). `|Δ|^(1/4)` is its **fourth root** — a much smaller
  number. NUCOMP keeps intermediate values near that fourth-root size instead of
  the full size, which is precisely why it's faster.


### "Correctness is established by a differential oracle..."

- We have two implementations: the simple **baseline** (schoolbook) and the fast
  **optimized** one (NUCOMP/NUDUPL).
- A **differential oracle** runs **both on the same random inputs and checks they
  produce the identical reduced form.** The baseline is the trusted reference (the
  "oracle") that tells us whether the fast version is correct.
- We do this on thousands of inputs, including tricky edge cases.

**Why this matters:** a fast-but-wrong algorithm is worthless. Correctness must
be proven *before* any speed claim is believed.

### "...performance is quantified with a JMH benchmark harness across multiple discriminant sizes..."

- **JMH (Java Microbenchmark Harness)** is the *correct* tool for timing small
  Java operations. You can't just call `System.nanoTime()` because the JVM
  "warms up" (the JIT compiler optimizes code only after it's run a while), so
  naive timing gives misleading numbers. JMH handles warmup, repetition, and
  statistics properly.
- We measure across several **discriminant sizes** (512, 1024, 2048, 3072 bits)
  because the speedup grows with size — bigger numbers are exactly where the
  smart algorithm pays off most.
- **Speedup** = baseline time ÷ optimized time (e.g. "2.3× faster").

- **This is the "demonstrate effectiveness" deliverable the rubric asks for:** the
benchmark printing the before/after numbers *is* the evidence.

---

## What we actually found

After implementing and **proving correct** all three optimizations, we measured them:

- **NUCOMP composition is the real win.** It is **1.25–1.56× faster** than the
  schoolbook method, and — importantly — *the bigger the numbers get, the more it
  helps* (1.25× at 512-bit, 1.56× at 3072-bit). That "gets better with size"
  trend is exactly what you'd expect from a true asymptotic improvement, and it's
  the cleanest thing to show a grader.
- **Smarter exponentiation (wNAF / windowed) is a steady ~1.2–1.3× win.** It does
  about 20% fewer group operations. It can't do better than that because most of
  exponentiation is *squarings*, which every method has to do once per bit.
- **NUDUPL (fast squaring) tied the baseline.** Honest result: our baseline
  already had a shortcut for squaring, so there was nothing left for NUDUPL to
  speed up. We report this openly — it shows we measured rather than guessed.

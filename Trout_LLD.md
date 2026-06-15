# Low-Level Design (LLD)
## Trout: Two-Round Threshold ECDSA from Class Groups — Java Implementation

**Companion to:** Trout HLD v1 (Java)
**Scope:** MVP / Phase 1 (security-with-abort, target config 2-of-3)
**Language:** Java 17+, Maven/Gradle multi-module, JUnit 5, Bouncy Castle (secp256k1)

> **Conventions**
> - Group/proof/message types are **immutable** (Java `records` or final classes).
> - **Never** use Java `Serializable` for cryptographic bytes — only the explicit
>   `trout-wire` codec.
> - Exact constants / message fields / relations come verbatim from IACR ePrint
>   2025/1666. Placeholders are marked `<<from paper>>` and must be filled before
>   coding the dependent module (see Appendix A checklist).

---

## trout-wire — Serialization & Transcript Spec (shared — B, D, F)

Single source of truth for any bytes that cross a process boundary or get
hashed. Freeze before M3.

### W.1 Encoding conventions
- Integers: big-endian, fixed-width, magnitude + explicit sign byte for signed
  values. Width per field in the tables below. (`BigInteger.toByteArray()` is
  **not** fixed-width — wrap it with left-padding/truncation helpers in
  `trout-core`.)
- Variable-length fields: 4-byte big-endian unsigned length prefix.
- Big-endian everywhere unless a field table says otherwise.

### W.2 Class-group element serialization
A reduced binary quadratic form `(a, b, c)` is canonical given `Δ`, so serialize
`a` and `b` only and recompute `c`.

| Field | Type | Width | Notes |
|---|---|---|---|
| signB | byte | 1 | 0 = b ≥ 0, 1 = b < 0 |
| a | unsigned | `L` bytes | `L = ceil(bitlen(Δ)/16)`, fixed per parameter set |
| b | unsigned | `L` bytes | magnitude of b |

`deserialize` recomputes `c = (b² − Δ) / (4a)` and re-runs reduction as a
validity check; reject if not already reduced.

### W.3 Fiat–Shamir transcript
- Hash: SHA-256 via `MessageDigest`.
- Domain-separation tag per proof type, ASCII, length-prefixed:
  `"TROUT/v1/zk/dlog"`, etc.
- Fixed absorb order, identical on prover and verifier:
  `tag || publicParams || statement || proverCommitments`.
- Challenge `e = H(transcript) mod q` (curve order `q`); reduction method
  `<<from paper>>` (rejection vs. modular).

### W.4 Message envelope (demo transport only)
JSON via Jackson/Gson; the `payloadB64` is raw `trout-wire` bytes — JSON is
never used for the cryptographic content itself.
```json
{ "sender":"P2", "round":1, "session":"sess-abc",
  "type":"SIGN_ROUND1", "payloadB64":"...." }
```

---

## trout-core — Foundations & Parameters (Owner: Person A)

### C.1 Responsibilities
`BigInteger` helpers (fixed-width encode/decode), prime & discriminant
generation, public-parameter setup, and the shared JUnit KAT harness.

### C.2 Types
```java
record Params(BigInteger delta,   // negative fundamental discriminant
              BigInteger q,        // secp256k1 group order
              GroupElement fGen,   // subgroup generator (filled by classgroup)
              int secParam) {}     // e.g. 128
```

### C.3 Public API
| Method | Signature | Notes |
|---|---|---|
| genDiscriminant | `BigInteger genDiscriminant(int lambda)` | negative fundamental Δ of required size `<<from paper>>` |
| genPrime | `BigInteger genPrime(int bits, SecureRandom rng)` | `BigInteger.probablePrime` + extra Miller–Rabin rounds per λ |
| setup | `Params setup(int lambda)` | assemble public params, no trusted secret |
| toFixed | `byte[] toFixed(BigInteger v, int len)` | left-padded big-endian (used by trout-wire) |
| fromFixed | `BigInteger fromFixed(byte[] b)` | inverse |
| loadKat | `List<TestVector> loadKat(Path p)` | parse shared KAT file |

### C.4 Notes
- Δ must satisfy the paper's form/sign conditions `<<from paper>>`.
- All randomness via `SecureRandom`; expose a seedable instance for KAT
  reproducibility (`new SecureRandom(seed)` deterministic variant for tests).

### C.5 Errors
- Composite where prime required → retry to bound, then `ParamException`.
- Δ failing residue condition → `ParamException`.

### C.6 Tests (JUnit)
- KAT: fixed seed → fixed Δ and generators.
- Property: generated primes pass an independent primality check.
- `fromFixed(toFixed(v,len)).equals(v)` for random `v`.

---

## trout-classgroup — Class-Group Arithmetic (Owner: Person B) — CRITICAL PATH

### CG.1 Responsibilities
Binary quadratic form arithmetic: composition, reduction, exponentiation,
identity/equality, inverse, and canonical serialization (per W.2).

### CG.2 Types
```java
final class Bqf implements GroupElement {   // binary quadratic form
    final BigInteger a, b, c;                // c derived: (b^2 - delta)/(4a)
    // constructor validates discriminant; factory reduce(...) returns canonical
}
interface GroupElement {
    GroupElement compose(GroupElement other);
    GroupElement exp(BigInteger k);
    GroupElement inverse();
    boolean isIdentity();
    byte[] serialize();
}
```

### CG.3 Public API
| Method | Signature | Notes |
|---|---|---|
| identity | `static Bqf identity(BigInteger delta)` | principal form |
| compose | `Bqf compose(Bqf x, Bqf y)` | Gauss composition (schoolbook first; NUCOMP optional) |
| reduce | `Bqf reduce(Bqf x)` | canonical `|b| ≤ a ≤ c` |
| exp | `Bqf exp(Bqf x, BigInteger k)` | square-and-multiply (variable-time MVP) |
| inverse | `Bqf inverse(Bqf x)` | `(a, -b, c)` then reduce |
| equals | `boolean equals(Object o)` | compares reduced forms |
| serialize / deserialize | per W.2 | `byte[] serialize()` / `static Bqf deserialize(byte[], BigInteger delta)` |

### CG.4 Notes
- Reduction: standard normalization loop.
- Composition: schoolbook Gauss first for clarity; NUCOMP a Phase-2 optimization.
- `exp` MVP is variable-time; constant-time is out of scope on the JVM (HLD §6.4).

### CG.5 Errors
- Non-reduced input where reduced expected → `FormException`.
- Discriminant mismatch between operands → `FormException`.

### CG.6 Tests (JUnit)
- KAT: `compose`, `reduce`, `exp` against fixed vectors.
- Property: `exp(x, k1.add(k2)).equals(compose(exp(x,k1), exp(x,k2)))`.
- Round-trip: `deserialize(x.serialize(), delta).equals(x)`.

---

## trout-crypto — Encryption & Commitments (Owner: Person C)

### EC.1 Responsibilities
CL linearly-homomorphic encryption over the class group and a
linearly-homomorphic commitment scheme.

### EC.2 Types
```java
record ClKeyPair(BigInteger sk, GroupElement pk) {}
record ClCiphertext(GroupElement c1, GroupElement c2) {}
record Commitment(GroupElement value) {}
```

### EC.3 Public API
| Method | Signature | Notes |
|---|---|---|
| clKeygen | `ClKeyPair clKeygen(Params p, SecureRandom rng)` | |
| clEncrypt | `ClCiphertext clEncrypt(GroupElement pk, BigInteger m, SecureRandom rng)` | m in plaintext space `<<from paper>>` |
| clDecrypt | `BigInteger clDecrypt(BigInteger sk, ClCiphertext ct)` | includes dlog-in-F step `<<from paper>>` |
| clAdd | `ClCiphertext clAdd(ClCiphertext a, ClCiphertext b)` | homomorphic addition |
| clScalar | `ClCiphertext clScalar(ClCiphertext ct, BigInteger k)` | scalar multiply |
| commit | `Commitment commit(Params p, BigInteger m, BigInteger r)` | hiding + binding |
| open | `boolean open(Commitment c, BigInteger m, BigInteger r)` | verify opening |

### EC.4 Notes
- Plaintext space and message-recovery step are defined by CL parameters
  `<<from paper>>`; document the exact subgroup used.
- Commitment binding/hiding rests on the class-group assumptions (HLD §4.3).

### EC.5 Errors
- Ciphertext under wrong discriminant → `CryptoException`.
- Decryption failure (no valid dlog) → `CryptoException`.

### EC.6 Tests (JUnit)
- Round-trip: `clDecrypt(sk, clEncrypt(pk, m)).equals(m)`.
- Homomorphism: `clDecrypt(add(enc(a),enc(b))).equals(a.add(b))`.
- Commitment: correct openings verify; tampered openings reject.

---

## trout-zk — Zero-Knowledge Proofs + eVRF (Owner: Person D)

### ZK.1 Responsibilities
Sigma protocols with Fiat–Shamir for the relations the protocol needs; owns the
transcript spec (W.3). The exponent-VRF is **Phase 2 / stretch**.

### ZK.2 Relations (MVP set — confirm against paper)
| Relation | Statement (informal) | Used in |
|---|---|---|
| `dlog` | knowledge of `x` s.t. `X = g^x` in class group | DKG, signing |
| `encCorrect` | a ciphertext encrypts a committed value | signing |
| `<<from paper>>` | additional relations per Appendix A | signing |

### ZK.3 Types
```java
record Proof(List<GroupElement> commitments, List<BigInteger> responses) {}
final class Transcript {            // SHA-256 backed
    Transcript(String domainTag);
    void absorb(byte[] data);
    BigInteger challenge(BigInteger q);  // H(transcript) mod q
}
interface Prover  { Proof prove(Statement s, Witness w, SecureRandom rng); }
interface Verifier{ boolean verify(Statement s, Proof p); }
```

### ZK.4 Public API
| Method | Signature | Notes |
|---|---|---|
| prove`<Rel>` | `Proof prove(...)` | Fiat–Shamir non-interactive |
| verify`<Rel>` | `boolean verify(...)` | recompute challenge, check |
| newTranscript | `Transcript newTranscript(String tag)` | per W.3 |

### ZK.5 Notes
- Each proof: commit → derive challenge via transcript → respond.
- Cite the exact lemma numbers from the paper in Javadoc for soundness/ZK.

### ZK.6 Errors / abort
- `verify` returns `false` → caller (protocol) triggers abort (MVP).

### ZK.7 Tests (JUnit)
- Completeness: honest proof verifies.
- Soundness (negative): tampered witness/statement → verify fails.
- KAT: transcript bytes match fixed reference vectors.

---

## trout-protocol — Protocol Logic (Owner: Person E)

### PR.1 Responsibilities
DKG, two-round signing, the combination/decryption step, abort handling.
Scaled-decryption and IA are **Phase 2**.

### PR.2 State machine
```
INIT -> DKG_R1 -> DKG_R2 -> READY
READY -> SIGN_R1 -> SIGN_R2 -> DONE
any  -> ABORT
```

### PR.3 Types
```java
final class PartyState {
    int id;
    Params params;
    BigInteger skShare;
    ECPoint pubKey;                 // joint ECDSA public key (Bouncy Castle)
    List<PeerInfo> peers;
    SessionCtx session;
}
sealed interface ProtocolMessage permits DkgR1Msg, DkgR2Msg, SignR1Msg, SignR2Msg {}
// each is a record; field layout per trout-wire
```

### PR.4 Public API
| Method | Signature | Notes |
|---|---|---|
| dkgRound1 | `DkgR1Msg dkgRound1(PartyState s)` | sample poly, commit coeffs |
| dkgRound2 | `DkgR2Msg dkgRound2(PartyState s, List<DkgR1Msg> r1)` | reveal, distribute shares (private chan) |
| dkgFinalize | `void dkgFinalize(PartyState s, List<DkgR2Msg> r2)` | VSS verify, derive `skShare` + `pubKey` |
| signRound1 | `SignR1Msg signRound1(PartyState s, byte[] msgHash)` | nonce material + ZK proofs |
| signRound2 | `SignR2Msg signRound2(PartyState s, List<SignR1Msg> r1)` | combine/decrypt + proofs |
| signFinalize | `EcdsaSig signFinalize(PartyState s, List<SignR2Msg> r2)` | assemble `(r, s)` |

### PR.5 Notes
- DKG uses commit-then-reveal on polynomial coefficients to prevent key biasing
  (HLD §7.1); shares over private point-to-point channels.
- Round-by-round message contents `<<from paper>>` — fill from ePrint before
  coding; densest part of the spec.

### PR.6 Errors / abort
- Any failed VSS check or ZK verification → `ABORT` with reason.
- Phase 2: record evidence sufficient to attribute the faulty party (IA).

### PR.7 Tests (JUnit)
- DKG: all honest parties derive the **same** `pubKey`; shares interpolate
  consistently.
- Signing: `(r,s)` verifies via Bouncy Castle ECDSA against the joint key.
- Abort: a deliberately malformed message causes a clean, reasoned abort.

---

## trout-app — Network, Orchestration, Test & Bench (Owner: Person F)

### AP.1 Responsibilities
Transport/messaging, party orchestrator, JUnit integration harness, benchmark
harness, and the end-to-end demo. Co-owns `trout-wire`.

### AP.2 Types
- Envelope per W.4.
- `record OrchestratorConfig(int n, int t, List<PartyHandle> parties, Transport transport) {}`

### AP.3 Public API
| Method | Signature | Notes |
|---|---|---|
| startParty | `void startParty(int id, OrchestratorConfig cfg)` | party event loop (thread / virtual thread) |
| route | `void route(Envelope msg)` | broadcast + point-to-point |
| runSession | `EcdsaSig runSession(OrchestratorConfig cfg, byte[] msgHash)` | drive DKG then signing (demo entrypoint) |
| bench | `BenchResult bench(OrchestratorConfig cfg, int iters)` | time each phase (Phase 2) |

### AP.4 Notes
- Start with **in-JVM mock parties** (direct method calls) so F is unblocked
  before `trout-protocol` is ready; swap to sockets later.
- Private channels modeled explicitly; coordinator never sees secret payloads.

### AP.5 Tests (JUnit)
- Integration: full 2-of-3 happy path → verifying signature.
- Fault injection: dropped / corrupted message → abort surfaces correctly.

---

## Appendix A. `<<from paper>>` checklist (fill before coding dependents)
- [ ] Discriminant size & form conditions (`trout-core`).
- [ ] CL plaintext space & decryption dlog step (`trout-crypto`).
- [ ] Exact ZK relation set & challenge reduction (`trout-zk`).
- [ ] Round-1 / Round-2 message field lists (`trout-protocol`).
- [ ] Scaled-decryption sub-protocol (Phase 2, `trout-protocol`).
- [ ] eVRF construction details (Phase 2, `trout-zk`).

## Appendix B. Cross-module test-vector flow
A single KAT file (owned by A, deterministic `SecureRandom` seed) lets B, C, D
assert identical intermediate values in JUnit. F runs the same vectors
end-to-end. Any divergence localizes the bug to one module.

## Appendix C. Suggested Maven module graph
```
trout-wire  <- trout-core <- trout-classgroup <- trout-crypto <- trout-zk
                                                       \             /
                                                        trout-protocol <- trout-app
```

---

*Disclaimer: Academic coursework. Not audited; not for production use.*

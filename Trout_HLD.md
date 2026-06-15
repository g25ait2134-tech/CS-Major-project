# High-Level Design (HLD)
## Trout: Two-Round Threshold ECDSA from Class Groups — Java Implementation

**Project:** Cybersecurity coursework — fresh implementation of the Trout protocol
**Reference paper:** Dahari-Garbian, Nof & Parker, *Trout: Two-Round Threshold ECDSA from Class Groups*, ACM CCS 2025 (IACR ePrint 2025/1666)
**Team size:** 6 — **all developing in Java**
**Document owners:** Protocol lead + Integration lead
**Status:** Draft v1 — MVP scope

---

## 1. Purpose & Scope

### 1.1 Purpose
Describes the overall architecture, security model, and module decomposition for
a from-scratch **Java** implementation of the Trout threshold-ECDSA signing
protocol as a group coursework deliverable. It is the shared reference that the
per-module Low-Level Designs (LLDs) expand on.

### 1.2 In scope (MVP / Phase 1)
- Big-integer layer built on `java.math.BigInteger`.
- Class-group arithmetic over imaginary quadratic fields (correctness-focused).
- CL-style linearly-homomorphic encryption over class groups.
- A linearly-homomorphic commitment scheme over class groups.
- Sigma protocols with Fiat–Shamir for the required relations.
- A simple VSS-based Distributed Key Generation (DKG).
- Two-round signing for a small configuration (target: 2-of-3) with
  **security-with-abort** (not Identifiable Abort).
- An in-JVM / socket orchestration layer and an end-to-end demo.

### 1.3 Out of scope (deferred to Phase 2 / stretch)
- General `t`-of-`n` for large `n` (e.g. 67-of-100).
- Exponent-VRF (eVRF) integration.
- The "scaled decryption" sub-protocol in full generality.
- Identifiable Abort (IA) and per-party fault attribution.
- Constant-time / side-channel-resistant class-group implementation.
- Formal performance benchmarking at scale.

### 1.4 Explicit non-goals
- This is **not** production cryptography. It is a learning artifact and must
  carry a clear "do not use in production" warning.
- We do not aim to reproduce the paper's reported performance numbers.

---

## 2. Background (one-paragraph summary)

Trout is a distributed protocol that lets any `t`-of-`n` parties jointly produce
a standard ECDSA signature without any single party ever holding the private
key. Its distinguishing properties: only two rounds of interaction for signing,
no trusted setup, constant per-party upload bandwidth, and optional Identifiable
Abort. It builds these on **class groups of imaginary quadratic fields**, which
provide groups of unknown order without a trusted dealer, plus CL homomorphic
encryption, class-group commitments, an exponent-VRF, and a "scaled decryption"
sub-protocol. Our MVP implements the foundational stack and a minimal signing
flow; the novel eVRF/scaled-decryption and IA pieces are stretch goals.

---

## 3. Goals & Quality Attributes

| Attribute | Target for MVP |
|---|---|
| Correctness | Signatures verify under standard ECDSA verification (secp256k1) |
| Determinism | All parties agree byte-for-byte on encodings and transcripts |
| Testability | Each module ships JUnit known-answer tests (KATs) |
| Clarity | Code readable enough to map directly back to paper sections |
| Modularity | Layers swappable; class-group backend behind a Java interface |
| Security (academic) | Honest-but-curious + abort; documented assumptions |

---

## 4. Threat Model & Security Assumptions

### 4.1 Adversary model
- **MVP target:** static, malicious-but-aborting adversary corrupting up to
  `t-1` parties; on detected misbehaviour the protocol safely **aborts**
  (no signature, no key leakage).
- **Phase 2 target:** Identifiable Abort — a failed run names a culprit.

### 4.2 Trust assumptions
- No trusted setup / no trusted dealer. Public parameters limited to a small
  number of class-group generators derivable transparently.
- Authenticated channels between parties are assumed (point-to-point private
  channels for share distribution; everything else broadcast).

### 4.3 Hardness assumptions (as relied on by the paper)
- Hardness in class groups of unknown order (subgroup-membership / "ARSA"-style).
- DDH (for the exponent-VRF, Phase 2).
- Standard ECDSA assumptions on secp256k1.

### 4.4 NOT defended against in the MVP
- Side-channel / timing attacks (Java `BigInteger` is **not** constant-time;
  constant-time work is Phase 2 and partly infeasible on the JVM — see §6.4).
- Network-level attacks beyond the assumed authenticated channels.
- Denial of service.

---

## 5. Actors

| Actor | Description |
|---|---|
| Party (`P_i`) | One of `n` signers; holds a secret share, participates in DKG and signing |
| Coordinator / relay | Optional message router for the demo; **not** trusted with secrets |
| Verifier | Anyone who checks the final ECDSA signature with the public key |

---

## 6. System Architecture

### 6.1 Layered view (Maven/Gradle multi-module project)
Dependencies flow upward; lower layers must be correct before upper layers work.
Each layer is its own module in a single multi-module build.

```
+-----------------------------------------------------------+
|  trout-app     Demo / CLI / Orchestrator runner           |
+-----------------------------------------------------------+
|  trout-protocol  DKG, 2-round signing, abort handling     |
+-----------------------------------------------------------+
|  trout-zk      Zero-knowledge proofs (sigma + Fiat-Shamir)|
+-----------------------------------------------------------+
|  trout-crypto  CL encryption + class-group commitments    |
+-----------------------------------------------------------+
|  trout-classgroup  Binary quadratic form arithmetic       |
+-----------------------------------------------------------+
|  trout-core    BigInteger helpers, params, discriminant   |
+-----------------------------------------------------------+
|  trout-wire    Serialization & transcript spec (shared)   |
+-----------------------------------------------------------+
```

### 6.2 Why all-Java simplifies the design
Because every party runs the same JVM bytecode, the historic risk in
multi-language threshold crypto — parties disagreeing byte-for-byte on
serialization or Fiat–Shamir transcript hashing — is sharply reduced. We still
define a **canonical serialization** in `trout-wire` (so a future re-impl or an
on-disk format stays stable, and so cross-process socket runs are consistent),
but it is an internal convention rather than a cross-language contract. This
lets the team spend its effort on the cryptography instead of on interop glue.

### 6.3 Recommended Java stack
| Concern | Choice | Rationale |
|---|---|---|
| Build | Maven or Gradle, **multi-module** | one module per layer; clean dependency edges |
| Language level | Java 17+ LTS (records, sealed types, pattern matching) | concise immutable data carriers for forms/messages |
| Big integers | `java.math.BigInteger` | built-in; sufficient for MVP correctness |
| Elliptic curve | Bouncy Castle (`org.bouncycastle`) secp256k1 | vetted ECDSA + curve math; avoid hand-rolling |
| Hashing | `MessageDigest` SHA-256 (domain-separated) | Fiat–Shamir transcripts |
| Tests | JUnit 5 + AssertJ | KAT vectors and property tests |
| Serialization | hand-written canonical byte codec in `trout-wire` | NOT Java `Serializable`; explicit big-endian layout |
| Transport (demo) | Java sockets or a thin HTTP layer + JSON envelope | Jackson/Gson for the envelope only, never for crypto bytes |
| Concurrency | one thread (or virtual thread) per simulated party | simple party event loops |

### 6.4 Note on constant-time
`BigInteger` operations are inherently variable-time and the JVM offers no
reliable constant-time guarantees, so a faithful constant-time class-group
implementation (the paper's stretch contribution) is **not realistically
achievable in pure Java**. We therefore mark it out of scope for the MVP and, if
attempted in Phase 2, scope it as a *best-effort* documented experiment rather
than a real side-channel defense.

---

## 7. Key Protocol Flows (high level)

### 7.1 Distributed Key Generation (DKG)
1. Each party samples a secret polynomial; **commits** to its coefficients.
2. Parties reveal commitments and distribute shares over private channels
   (commit-then-reveal prevents biasing the joint public key).
3. Each party verifies received shares (VSS check) and derives its share of the
   joint secret key; the joint ECDSA public key is computed.
4. Any inconsistency → abort.

### 7.2 Signing — Round 1
- Parties produce and broadcast first-round contributions (nonce-related
  commitments / encryptions and the accompanying ZK proofs).

### 7.3 Signing — Round 2
- Parties combine round-1 material, perform the decryption/combination step,
  broadcast second-round contributions, and locally assemble the final `(r, s)`
  ECDSA signature.
- Any proof failing → abort (MVP) / attribute culprit (Phase 2).

> Exact field-by-field message contents for rounds 1 and 2 are specified in the
> LLD and `trout-wire`, taken from the ePrint paper.

---

## 8. Module Responsibilities (maps to team assignments)

| Maven module | Responsibility | Owner role |
|---|---|---|
| `trout-core` | `BigInteger` helpers, prime/discriminant generation, parameter setup, shared KAT vectors | Person A |
| `trout-classgroup` | Binary quadratic forms: composition, reduction, exponentiation, **canonical serialization** | Person B |
| `trout-crypto` | CL keygen/enc/dec + homomorphic ops; commitment scheme | Person C |
| `trout-zk` | Sigma protocols, Fiat–Shamir transcript; eVRF (stretch) | Person D |
| `trout-protocol` | DKG, two-round signing, scaled-decryption orchestration, abort/IA logic | Person E |
| `trout-app` + `trout-wire` | Transport, orchestrator, integration tests, benchmark harness, demo; co-owns wire spec | Person F |

---

## 9. Interfaces Between Modules (Java types, high level)

- **core → classgroup:** `BigInteger`, generated discriminant `Δ`, group params.
- **classgroup → crypto/zk:** a `GroupElement` interface —
  `compose`, `exp`, `identity`, `inverse`, `equals`, `serialize`/`deserialize`.
- **crypto → protocol:** `CLCiphertext` + `encrypt/decrypt/add/scalarMul`;
  `Commitment` + `commit/open/verify`.
- **zk → protocol:** per-relation `Prover`/`Verifier` objects + a `Transcript`.
- **protocol → app:** sealed `ProtocolMessage` hierarchy (records) and a party
  state machine the orchestrator drives.
- **wire (shared):** the canonical byte codec + the transcript definition —
  co-owned by B, D, F.

---

## 10. Phasing & Milestones

| Milestone | Content | Gate |
|---|---|---|
| M1 | `trout-core` + `trout-classgroup` correct, KAT vectors pass | Class-group ops verified |
| M2 | `trout-crypto` passes round-trip & homomorphic tests | — |
| M3 | `trout-zk` proofs verify; transcript/serialization spec frozen | wire spec locked |
| M4 | `trout-protocol` DKG produces consistent shares + joint public key | — |
| M5 | **MVP demo:** 2-of-3 end-to-end signature verifies | Phase 1 complete |
| M6 (stretch) | eVRF, scaled decryption, IA, t-of-n, best-effort constant-time | Bonus |

---

## 11. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Class-group module slips (hardest, on critical path) | Blocks everything | Pair A+B from day one; start immediately |
| Misimplemented crypto looks fine but is insecure | Wrong grade / false confidence | JUnit KAT vectors per module; map code to paper; peer review |
| Scope overrun | Nothing works at deadline | Strict Phase 1 MVP; Phase 2 optional |
| Accidental use of Java `Serializable` for crypto bytes | Non-canonical / unstable encoding | Mandate hand-written codec in `trout-wire`; ban `Serializable` for group/proof types |
| eVRF/scaled-decryption complexity | Time sink | Keep in Phase 2; only after MVP demos |

---

## 12. Deliverables Summary

- Working Java code per module (`trout-core` … `trout-app`).
- Shared `trout-wire` serialization & transcript spec.
- This HLD + per-module LLDs.
- JUnit integration test suite + KAT vectors.
- Final report (threat model, design rationale, known limitations) + demo.

---

*Disclaimer: This is academic coursework. The implementation is not audited and
must not be used to secure real assets.*

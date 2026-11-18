# HEIMDALL M2-D — Guarded Effect Port and Ownership Transfer

**18 November 2026 · Shadow-only source increment · Draft review**

Base: `feature/heimdall-m2c-shadow-platform-evidence` at `5152d2e5261f2be5abccc949dfb072c55eac109e`.

## Scope and exact implementation

- `domain/LegacyGuardedEffectPort.kt`: implements M2-B's `EngineEffectPort` behind a service-supplied `LegacyServiceEffectHost` and an exact service epoch.
- `domain/LegacyGuardedEffectPortTest.kt`: 19 new JVM tests cover authorization, original session identity, successful durable STOP/pause/finalize order, refusal to report unconfirmed operations, lease-recovery restrictions, and overlay ownership/cleanup.
- Existing Android recorder, `RecordingService`, `RecorderLease`, `StatusProvider`, `MonitorState`, SQLite schema v9, Quick Settings tiles and UI are unchanged.

## Critical constraint: this is NOT an active recorder adapter

No production implementation of `LegacyServiceEffectHost` exists yet and nothing constructs this port in the Android service. No second sampler, window, database writer or foreground service has been introduced. A fake host can prove the port's contract in local tests; it cannot establish that Android runtime operations satisfy the contract.

This step formalizes the precise service-side proofs required **before** enabling M2-B's coordinator:

1. Evidence is issued and consumed from the same monitor-service owner epoch. UI/provider snapshots do not authorize mutation. Transfer of exclusive recording ownership must be explicit.
2. A START must return the original newly persisted SQLite session ID and be verified against the service's active owner. A second open while active fails closed.
3. The STOP adapter must not report success until `RecorderLease.requestStop()` succeeds (or an equivalent durable terminal marker is confirmed for the same original ID).
4. A pause acknowledgment must follow joining the actual sample-writing coroutine, not merely calling `Job.cancel()`.
5. Finalization needs both durable STOP and quiescence evidence. The existing `RecordingService` already has a `finally` finalizer; it **must not** remain an independent competing finalizer after ownership transfer.
6. Recovery must validate the original SQLite row and eligibility in the **service**, not from M2-C's passive status/lease probes. No recovery path may allocate a replacement session ID.
7. HUD attach/detach success is acknowledged only after the WindowManager owner confirms the expected operation. Unknown cleanup prevents a second overlay attach.

## Engineering limits and hazards

- The current new port serializes effect calls through a coroutine mutex, but it is **not a substitute** for transactional service ownership and race-free handoff. A legacy STOP intent or a stale Android intent could still execute independently until the service itself delegates to one owner.
- The proof fields are a proposed internal service contract; they are not stored in SQLite and are not derived from M2-C `StatusProvider` observations.
- Loss of process epoch after an operation is reported as failure, even if the operation partially completed. Recovery must reconcile the persisted row/lease rather than blindly retrying creation.
- A durable STOP proof in this proposed contract identifies the session. Explicit command IDs remain necessary for deduplication; an Android service adapter must persist/reconstruct enough information to distinguish re-delivered commands.
- No claim of exactly-once delivery, Android validation, or successful Odin recording is made from JVM tests.

## M2-D exit and M2-E entry gate

- **Completed in this PR:** type-safe guarded port, pure JVM tests, no runtime wiring.
- **Required before activation:** implement a service-owned host in a debug-only path; benchmark a single-owner handoff; add fault injection for STOP between lease commit, sampler join and SQLite finalization; validate sticky restart, overlay teardown and duplicate commands on Odin.
- **Do not enable the new coordinator in a release build** until those fault scenarios pass and the old and new lifecycles cannot both mutate a session.
- UI/HUD redesign remains under M1/M3 review; no new visual work in this PR.

## Test evidence

Source compiled locally with `kotlinc-jvm` using the project-provided coroutine dependency equivalent, and **72 contract checks passed** in a lightweight JUnit-compatible harness (53 existing M2-A/B/C checks + 19 new M2-D checks). The local harness is not equivalent to full Android Gradle or device testing; GitHub CI is the authoritative next gate.

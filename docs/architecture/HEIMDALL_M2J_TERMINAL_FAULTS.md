# HEIMDALL M2-J — Failure-safe native terminal commit (phase 1)

**18 November 2026.** Base: M2-I at `c482db709881a45d2d41bc4700b37f0d9f7d3640`.

## Motivation

M2-I's Android IO performed `SessionStore.finish(id, status)` and then `RecorderLease.clear()` in that order. If the SQLite update succeeded but the synchronous lease-clear commit failed, `EngineController` correctly reported `FinalizeFailed`. However a later retry would rewrite the same SQLite row's status and ended_at. If a conflicting terminal result appeared, that unconditional update could also overwrite it. The stored session must never mutate twice just because a cleanup acknowledgment was lost.

## This change

- `HeimdallTerminalFinalizer` is an Android-free commit protocol: durable owner+ID+STOP proof; first/only SQL update from `RECORDING` to the exact expected terminal status; verify persisted status; then conditionally clear the matching Heimdall owner lease.
- `SessionStore.finishRecordingOnce()` is a compare-and-set SQLite UPDATE with `WHERE id=? AND status='RECORDING'`. `recordingStatus()` reads one scalar. Existing legacy `finish()`, SQLite schema v9, migrations and report/export formats are untouched.
- `RecorderLease.rawSession()` provides raw same-process lease evidence even if too stale for recording recovery. `clearHeimdallStoppedSession()` refuses to erase an active lease unless owner, ID, recording and persisted STOP all match. This is guarded in the one existing `:monitor` service process, not a new Service.
- `HeimdallAndroidSessionIo.finish()` invokes the protocol. A transient failure after the SQLite COMMIT leaves the persisted STOP lease intact. A repeated finalization must leave the terminal outcome and ended_at unchanged and retry only lease cleanup.
- 16 pure JVM fault-injection tests: no STOP, wrong owner/ID, database commit error or exception, lease cleanup false/exception after row commit, already finalized or missing rows, repeated result, alternate StopReason and absent lease acknowledgment.
- Three API 33 isolated tests use an independent `SessionStore` database and test-only SharedPreferences: real STOP proof and SQL CAS, failed lease cleanup followed by retry preserving ended_at, and protection against rewriting an existing different outcome.

## State-machine sequencing (unchanged)

`EngineController` → `PersistStopIntent` → `PauseSampling` (cancel and **join** actual sampler) → `FinalizeSession`. The guarded effect port only acknowledges finalization after the commit protocol returns success. No automatic row rewrite on retries. A database or SharedPreferences exception becomes a correlated `SessionFinalizeFailed` event; `RetryFinalization` reuses the same session/STOP identifier. A process death after SQL completion but before lease cleanup is recognized as a terminal row on the next native recovery plan; cleanup does not resume sampling.

## Limitations and next acceptance gates

- This PR does **not** implement an in-app fault injection control or run dangerous failures on the user's device. Existing normal legacy recorder is unchanged; Heimdall remains debug-only.
- An injected JVM fake is not a power-loss or disk-full simulation. Full Gradle and API33 instrumentation, including native runtime and native restart suites, must pass.
- Next phase M2-J2: inject failures at STOP storage, sample-loop cancellation/join, DB errors during sustained sampling, and process death precisely between persisted STOP and final lease cleanup. Validate on a disposable emulator first. Only then attempt controlled Odin tests.
- Do not retire legacy recorder, change the app ID, remove SQLite schema or replace HUD/Quick Settings in this PR. Final product still has one Heimdall engine and three native HUD layouts.

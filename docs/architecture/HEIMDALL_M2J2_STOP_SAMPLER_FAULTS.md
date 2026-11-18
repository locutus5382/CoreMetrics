# HEIMDALL M2-J Phase 2 — STOP retry, sampler join and terminal-restart integrity

**Date:** 18 November 2026. **Base:** `feature/heimdall-m2j-finalization-faults` at `634d0fd41f51baeda56ade1d6434397cb252cd79`.

## Issue fixed

Previously, when both `PersistStopIntent` and `PauseSampling` failed, a repeated STOP could retry only the persisted lease write. A successful lease retry left the old `pauseError` unresolved and required a **third** user STOP before finalization. The reducer now independently detects both unconfirmed proofs and enqueues the required retries in the safe sequence (persist STOP before pause), never repeating a proof that is already confirmed or still in flight. Command IDs remain deduplicated.

## Changes

- `domain/EngineReducer.kt`: on a repeated STOP while `StopRequested`, retry **every failed proof**, preserving the original session ID and STOP command ID. Already durable STOP and already paused sampler must not be rerun.
- `domain/EngineReducerTest.kt`: 4 new pure-JVM scenarios: both STOP + pause failed, duplicate retry while acknowledgments pending, durable STOP + failed pause, paused sampler + failed STOP.
- `engine/HeimdallNativeEngineTest.kt`: 2 additional native-engine JVM scenarios. An injected non-cancellable database write remains in flight across a STOP request; cancel and join must await its completion before finalization can finish. Separately, repeated storage failures must drive coordinated STOP with `StopReason.SAMPLE_FAILURE`, not allow the sampling loop to independently finalize. The test uses in-memory fake IO only.
- `HeimdallTerminalRecoveryAndroidTest.kt`: new **emulator-only**, disposable-state instrumentation. It creates a real SQLite `RECORDING` row and HEIMDALL_DEBUG recovery lease, durably requests STOP, and launches the real `:monitor` service to reconcile that post-crash evidence. It expects the original session to finish exactly once, no additional SQLite session, zero samples (sampler must not start), and the terminal lease to clear.
- Android CI adds a **separate** `heimdall-terminal-recovery` API 33 emulator group. The destructive test requires a verified emulator hardware identity and must never run against a physical Odin or valuable user data.

## Boundaries preserved

- Existing `:monitor` foreground service and default CoreMetrics recorder unchanged.
- New Heimdall engine remains explicitly debug-only and is **not** the default START target.
- No SQLite schema migration, app ID change, new service, permission, telemetry collector replacement, HUD implementation or repository migration.
- Process-death test from M2-I remains in CI; synthetic post-crash STOP test is complementary, **not** a claim of killing the process at an exact machine instruction between disk writes.

## Remaining risks and engineering gates

- Failure of a long-lived recovery-lease heartbeat is currently treated as best-effort presentation notification in `HeimdallAndroidSessionIo.committed`; this must be separated from optional HUD presentation or validated as nonfatal with an explicit safety policy.
- Need actual disk-full/locked-database fault injection on a disposable Android emulator. JVM injected IO verifies ordering but cannot reproduce file-system power loss or OEM process lifecycle.
- Need STOP and pending-finalization retry that remain accessible to the user when automatic cleanup is impossible.
- **Before default-engine cutover:** full Android build/JVM/instrumentation CI, controlled Odin 2 Portal tests under Eden and launcher transitions, performance/thermal/battery profiling, and session export/analytics regressions.
- Keep the old engine until the cutover gates pass; after activation, remove only obsolete lifecycle/sampler code, not the shared DirectSampler, sensor readers, SQLite/DUKO analysis or export logic.

## Acceptance

Review PR as draft until the new API 33 terminal-recovery group and all existing CI checks pass. The separate emulator integration jobs create data only in ephemeral emulator installations; failures do not silently delete evidence.

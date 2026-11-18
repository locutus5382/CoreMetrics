# HEIMDALL M2-A — Pure recording-state contract and reducer

**Status:** Isolated foundation, proposed for review. **Not integrated with production RecordingService, StatusProvider, SessionTile or HUD.** 18 November 2026.

**Base:** locutus5382/CoreMetrics, design/product-reset-v1, audited at 0b324613fb57025de44c490bbdf51b734b371e5b.

## Architecture lineage

This increment follows **Claude's core recommendation** (pure Kotlin state/event/effect reducer; one future monitor-owned controller) without implementing its complete multi-module build, new Messenger layer, dual engine-host runtime, changed SQLite representation or live service rewrite.

| Claude boundary | M2-A treatment |
| --- | --- |
| :core:model | Android-free domain/EngineContract.kt in current :app (extract later) |
| Pure reducer | domain/EngineReducer.kt; no I/O, timers or Android imports |
| EngineController | Deferred to M2-B, one serialized owner in :monitor |
| EffectRunner, lease/recovery adapters | Deferred to M2-B/C; preserve existing recorder semantics |
| :monitor process, SQLite v9/WAL | Unchanged |
| Canvas HUD :hud module | M3, after M1 design gate |
| Ten Gradle modules | Deferred until existing Android-dependent analytics/data models are decoupled |

Three main screens, one REC tile, structured test profiles, Cobalt/Marigold/Graphite identity, three HUD presentations, Heimdall single-session analysis and comparative DUKO Reports remain the agreed product direction.

## Model and invariants

- Recording: Idle, Starting (can retain a STOP-before-open), Recording, StopRequested, Finalizing, FinalizeFailed, Recovering, Failed.
- Overlay: Detached, Attaching, Attached, Detaching, Blocked, CleanupRequired. **Attached is only an acknowledged result of WindowManager.addView**, not an inferred intention.
- Presentation: Compact, Expanded, Edge-Docked, independent of attachment. Multiple data-health flags may coexist.
- State includes per-process epoch and increasing per-epoch sequence. The UI's cached state cannot override the actual SQLite row or recovery lease.
- Commands carry caller-defined IDs. Bounded in-process deduplication is not a durable, cross-process exactly-once guarantee.

### Critical STOP and recovery requirements

STOP emits PauseSampling and PersistStopIntent but **not** FinalizeSession until a matching StopIntentPersisted event arrives. The future platform adapter must durably persist the stop intent and never claim it has persisted before the write succeeds. Failed persistence remains retryable; failed finalization retains the same session ID and can be retried without resuming sampling.

**Important unresolved crash window:** if the process dies before STOP intent reaches durable storage, an in-memory StopRequested flag disappears. M2-B must retain the old RecorderLease write ordering and delay any *durable STOP* acknowledgment until that actual write commits. This reducer is not wired into the recorder, so it does not yet change current stop behavior.

Reconcile may be created **only** by platform code that actually checks the SQLite row and durable recovery lease, including persisted stop intent and boot eligibility. When STOP is already persisted, finalize that session; with a valid lease, resume **the same session ID** and record the sample gap; otherwise close as interrupted.

### Overlay teardown

A requested overlay enters Attaching, not Attached. If the user hides it during attachment, a late success schedules a detach. An unsuccessful detach enters CleanupRequired; **no second overlay attach** is permitted until the owner confirms cleanup. Permission failures do not affect the recorder state. Attachment does not prove the Android window is visually unobscured.

## Compatibility untouched

- Android application ID, app package, manifest, signing and Quick Settings components.
- SQLite file and schema v9; stored profiles, raw samples, legacy CSV/ZIP/DUKO exports.
- RecordingService, MonitorState, RecorderLease, DirectSampler, RecorderHud and previous tests.
- 2-second/5-second sampling and dropped-tick policy; offline/no-root/read-only permissions.
- No new dependencies or Gradle edits.

## Test evidence

Both production source files compile with **kotlinc-jvm 1.9.0** without Android libraries. **23 deterministic contract tests passed locally** in a temporary lightweight JUnit-compatible harness. That harness is **not** included in this branch; local results are not a substitute for official Gradle/JUnit or hardware runs.

Tests cover duplicate START/STOP; STOP-before-session-open; persistence-before-finalization; retry after STOP persistence/finalization failures; stale completion filtering; permission denial/revocation; attachment/detachment and failure acknowledgment; window cleanup preventing duplicate attach; lease recovery, persisted STOP recovery and interrupted closure; original session/profile IDs; process epoch and sequence; bounded command history.

The existing GitHub CI should run :app:testDebugUnitTest, debug/optimizedTest APK builds and Android API 33 instrumented suites. No Odin validation or production integration is claimed in M2-A.

## Next increment: M2-B

1. Create a single serialized monitor-owned controller around the reducer; use a lifecycle-bound coroutine scope.
2. Add small platform adapters over the existing recorder/lease, not a rewritten sampling loop.
3. Resolve the cold-state IPC path with a spike of the existing StatusProvider before selecting Messenger or a different transport.
4. Add Android fault-injection tests for restart, durable STOP, stale IPC and overlay attachment/teardown.
5. Keep all new behavior behind a debug-only integration switch until baseline and design acceptance gates pass.

**Merge gate:** Review source and failure semantics, CI green, no manifest/schema change, and a clear statement that production runtime still uses the legacy service.
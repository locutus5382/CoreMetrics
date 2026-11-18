# HEIMDALL M2-I — Native process-death recovery and legacy retirement gate

**Date:** 18 November 2026. **Base:** `feature/heimdall-m2h-lease-owner-recovery-gate` at `f1f0c165da7c382c500bc2e6a116c8a1e103f85f`.

## Product decision: do not delete the legacy recorder yet

HEIMDALL owns the new state reducer, coordinator, guarded effects, sampling loop and session host. `RecordingService` is still the only Android foreground-service component; it continues to run legacy recording for normal app/Quick Settings sessions and can opt into the new native engine in debug. The **legacy recorder lifecycle is temporary fallback code**, but `DirectSampler`, device-specific sensors, `SessionStore`, the SQLite v9 schema and migrations, DUKO algorithms, exports and report formats are **shared infrastructure**, not disposable engine code.

Retirement order: finish native STOP and recovery → Android emulator validation → Odin hardware and export/migration regression tests → make native engine the default behind a rollback gate → remove the obsolete legacy sample loop, mutable-state mutation and extra tiles without removing shared collectors or losing sessions. No repository migration now.

## M2-I implementation

- `HeimdallNativeRecoveryPolicy` is pure Kotlin and distinguishes five states: resume eligible original, finalize already-persisted STOP, persist a fresh cold STOP before finalization, clear an already-finalized original session's stale lease, and quarantine. Unknown owner, invalid heartbeat/age, missing row, invalid ID, invalid interval or unknown status must never start a sampler.
- `HeimdallSessionHost.acceptNativeRecoveryHandoff` verifies the original HEIMDALL_DEBUG lease and SQLite row via **the host's own platform IO** before accepting an exclusive native recovery transfer. The legacy task/session/overlay must be absent, while the original Heimdall recovery lease is necessarily **present**.
- `HeimdallDebugEngineRuntime.recover` restores the existing controller around the **same original session ID**. No call to `SessionStore.begin()` can occur in this branch. Native START uses `EngineEvent.Reconcile`; fresh user STOP is persisted before any resumed sampling, and already-persisted STOP moves directly to finalization. The elapsed timer is reconstructed from persisted original start and current monotonic uptime after the host verifies boot/lease freshness.
- `RecordingService` promotes to foreground before any lease/DB work, reserves the debug owner on its main thread, reclassifies the original row and lease on IO, and routes only HEIMDALL_DEBUG leases to the native engine. A cold explicit STOP/DISARM is handled as durable terminal intent without resuming the sampler. Invalid/expired/unknown evidence is quarantined and preserved rather than cleared. Normal LEGACY-owned recording still uses the unchanged legacy sampler.
- Duplicate quick commands during recovery are held for the owner; a STOP arriving while `RecordingState.Recovering` waits for the sampler resume acknowledgement instead of failing against a not-yet-owned active session. No second sampling job is ever started.
- `StatusProvider` adds a **debug-only** monitor PID used exclusively by the emulator crash/restart test.
- 17 pure recovery-plan JVM tests plus 3 native-host ownership/recovery tests; a new isolated API 33 `heimdall-native-restart` instrumentation job kills the `:monitor` process and checks original-ID continuation, a persisted sample after restart, and finalization after STOP.

## Safety boundaries and known limits

- **DEBUG only**. Default UI and Quick Settings still use CoreMetrics' old recorder. No new Android Service, exported intent, permission, schema migration, app ID or new UI/HUD implementation.
- If recovery evidence is too old, corrupt or exhausted its retry budget, the original row/lease is quarantined rather than overwritten. Automated repair is **not** implemented in this stage.
- A terminal recovery may still fail when SQLite or SharedPreferences are unavailable; keep durable STOP and original row for later retry. No guarantee of Android service restart after low-memory killing; `START_STICKY` is a request to the OS, not a guarantee.
- A repeated STOP while already in FinalizeFailed requests retry. Recovery after crash within the actual final commit window requires emulator and Odin fault injection before release.
- The emulator-only process-kill test is invasive to disposable emulator app state; it skips on physical devices and leaves evidence on failure. A successful run does not establish Odin 2 Portal lifecycle guarantees.
- Existing HUD preview, one-tile product plan and Cobalt/Marigold/Graphite UI remain separate M3/M4 milestones, not touched here.

## CI gates

Official Gradle JVM and all Android builds, existing API33 database-integrity/reports-ui tests, M2-H native-runtime test and M2-I native-restart test must pass. No acceptance claims until CI results are inspected. Keep the PR draft until the process-kill job and full CI both pass.

## Next

M2-J: introduce explicit fault-injection at durable STOP, sampler cancellation, SQL finalization and lease clear. Validate crash windows without duplicate windows or data loss. Hardware validation on Odin must precede default-engine activation. Only then begin the cleanup/refactor removing obsolete legacy recording code.

# HEIMDALL M2-C — Read-only Android platform evidence bridge

**Status:** proposed shadow-only increment (not integrated into the current Activity, `RecordingService`, notification, HUD or Quick Settings). 18 November 2026. 
**Base:** `feature/heimdall-m2b-shadow-controller` (PR #51, commit `8b628462e3295410ad82627c7200353bb4984be8`).

## Scope and implementation

M2-C begins the Claude-inspired Android adapter layer without switching recording engines. New files:

| File | Responsibility |
|---|---|
| `domain/LegacyShadowEvidence.kt` | Pure Kotlin normalization of the existing provider payload and lease/SQLite recovery hints. Does not import Android, mutate the reducer or make decisions on resumption. |
| `monitor/HeimdallLegacyStatusProbe.kt` | Read the existing private `StatusProvider.call("status")` Bundle through `ContentResolver`; fail closed when keys are absent or IPC fails. |
| `monitor/HeimdallLegacyRecoveryProbe.kt` | Read `RecorderLease.recover()` and the original session's `status` via `SQLiteDatabase.OPEN_READONLY`; no `SQLiteOpenHelper`, migration, inserts, `begin`, `finish` or session resume. |
| `LegacyShadowEvidenceTest.kt` | JVM tests covering live vs cold/provider-only states, freshness, conflicting state, persisted-STOP candidates and database mismatch. |
| `HeimdallShadowPlatformAndroidTest.kt` | API 33+ instrumentation for the private provider contract and side-effect-free recovery evidence read. |

The legacy provider can launch the **`:monitor` process** on first call, **not `RecordingService`**. It does not start the foreground service, sampling or a session. The probe is dormant unless explicitly invoked in diagnostics/tests. The Android instrumentation invokes it in a test, not in production.

## Critical limits

1. **Observed status is not recording truth.** `serviceAlive`, `running` and the session ID come from the existing provider's `MonitorState` projection. It is useful for UI diagnostics only. `PROVIDER_ONLY_IDLE` does not prove that the SQLite database contains no open sessions.
2. **Pending lease is not live REC.** Recovery observations are `RECOVERY_*_CANDIDATE`, never `LIVE_RECORDING`. No recovery event is posted by these probes.
3. **No forged sensor data.** `sampleFresh` requires a live recording state and a real wall-clock `lastSampleAt` no older than 15 seconds. An unknown timestamp, clock rollback, a standby lease and stale data all produce `sampleFresh=false`. The staleness display threshold is not a recorder failure or sampling-policy change.
4. **No schema upgrades during inspection.** The recovery probe uses `OPEN_READONLY`, rather than constructing `SessionStore`, and checks for the database file before opening it. It does not scan all open sessions. A missing, unreadable or incompatible database yields an unavailable candidate.
5. **No autonomous reconciliation.** Only the original service's startup/recovery procedure may validate the recovery lease and SQLite session row and decide whether to resume or finalize. Neither the probe nor the new `EngineController` currently performs this operation.
6. **No duplicate writer or overlay.** No M2-C code implements `EngineEffectPort`, touches the existing WindowManager or creates sessions. The current legacy recorder remains the only active engine.

## Differences between M2-B contract and current Android implementation

- M2-B's effect runner requires **confirmed** durable STOP and confirmed sampler pause before finalization. In production `RecordingService` currently executes `RecorderLease.requestStop()` on its existing STOP path, followed by coroutine cancellation and its `finally` block's SQLite finalization. Any future adapter must wrap/coordinate these operations without racing the legacy coroutine or issuing a second write path.
- A failed STOP preference commit causes legacy `persistTerminalIntent` to try clearing the lease, rather than announcing durable STOP success. No `StopIntentPersisted` event may be emitted for either failure path without a successful terminal write.
- The legacy `StatusProvider` returns `running`, `starting`, `standby`, `sessionId`, `pendingRecovery` and sensor strings. It does not carry the new reducer's epoch, sequence or acknowledged overlay operation identifiers. The bridge therefore never pretends this is an `EngineState` snapshot.
- The existing service has a read-only `StatusProvider` already. Adding Messenger/AIDL and a second persistence cache is not justified yet.

## Testing evidence and acceptance

**Local:** the four existing M2-A/B pure Kotlin files and new pure mapping compiled with `kotlinc-jvm 1.9.0`; **53 local JVM checks passed** with a lightweight JUnit-compatible runner (37 previous + 16 new). This is not full Android Gradle or Odin qualification.

**Android CI:** includes a new API 33 instrumentation check via the existing workflow's `database-integrity` suite. The check exercises the actual private provider IPC and the read-only recovery probe; it intentionally does not assume the recorder is idle (another test may have run previously).

**M2-C exit gate:** Android build and instrumentation pass. Confirm on Odin that reading the status provider can create a monitor-only process but does not start the foreground recorder; inspect process list and journal before/after, while idle and while recording. Validate lease outcomes separately for clean shutdown, interrupted recording, persisted STOP and stale/rebooted lease. These **hardware items remain unverified**.

## Next: M2-D (separate PR)

Introduce the real Android `EngineEffectPort` behind a **disabled-by-default diagnostic switch** and prove operations against the existing service lifecycle without running two samplers. M2-D must establish exact ownership transfer/rollback semantics before enabling new control commands. It must not mix a new coordinator with the old recorder's independent STOP/finally logic while both can mutate the same session.

The main repository stays `locutus5382/CoreMetrics`; the `applicationId`, schema v9, sampling cadence, permissions, raw formats and current UI/HUD are unchanged.

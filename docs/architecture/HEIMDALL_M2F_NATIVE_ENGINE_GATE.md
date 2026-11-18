# HEIMDALL M2-F — Native recording engine, still inactive in production

**18 November 2026 · First native recording-loop implementation**

**Base:** M2-E at `6bac86a3b9cbdc28a5f8db7c4f4dba828b9459cf`.
**Branch:** `feature/heimdall-m2f-native-sampling-engine`.

## The actual architecture choice

HEIMDALL owns its **own** recording-control and sampling-loop implementation:
- `domain/EngineController`: sole serialized state/command actor;
- `domain/EngineReducer`: deterministic transitions;
- `domain/EngineEffectRunner`: correlated platform effects;
- `domain/LegacyGuardedEffectPort`: service-epoch and ownership boundary;
- `engine/HeimdallSessionHost`: session lifecycle, sampler job and exclusive owner;
- `engine/HeimdallSamplingLoop`: native drift-corrected collection, persistence and missing-tick policy;
- `engine/HeimdallAndroidSessionIo`: **adapts, not rewrites** the existing DirectSampler, ForegroundAttribution, SessionStore and RecorderLease.

The `Legacy` prefix on the guarded port describes the migration boundary, not which recording engine controls the session. Once the service transfers ownership, **HEIMDALL's EngineController and HeimdallSessionHost are responsible for the session and new sampling loop**.

## Scope completed

1. Native engine loop runs at the validated 2s or 5s cadence, does not burst to catch up and never creates fake samples after a dropped tick.
2. The native session host begins a session only after exclusive handoff. The recovery lease must be persisted before the sampling job starts.
3. The normal STOP path persists terminal intent before cancellation. If persistence fails, sampling is still stopped promptly; SQL finalization remains blocked until both durable STOP and `cancelAndJoin` quiescence are confirmed.
4. A successful SQLite finish precedes lease clearing. Finalization failures remain retryable against the **original session ID**.
5. A process-restart path validates the original row and lease. Valid resumptions renew the lease. Persisted terminal STOP finalizes without resampling. Invalid leases can close the existing unfinished row.
6. Overlay functions deliberately fail closed until the new Canvas/WindowManager owner is implemented. There is no route for the new engine to start another old HUD.
7. Nonessential HUD callback exceptions do not undo already-persisted samples. Fatal collection errors are reported to a mandatory coordinator callback; the loop never finalizes SQL autonomously.
8. Android adapter reuses SQLite v9 and existing sensor measurement formats, retries one database lock, and maintains a bounded lease heartbeat for long sessions.
9. New end-to-end JVM tests exercise EngineController → guarded port → native host → sample/STOP/finalization via fake I/O.

## Hard migration gate: not active in CoreMetrics yet

**RecordingService does not construct any HeimdallSessionHost and does not route START or STOP to it in M2-F.** There is no second sampling loop in the shipping APK. The native code compiles alongside the legacy recorder but remains dormant. Neither M2-E's shadow audit nor passive StatusProvider reads grant ownership.

Before enabling it, M2-G must make `RecordingService` select **exactly one** owner per service lifetime, verify the legacy task/lease/window are all inactive, instantiate the native engine, and atomically block legacy command handling. It must not rely on an unverified UI/provider snapshot.

## Open integration requirements

- Integrate the foreground-service notification deadline, background launch rules, QS and notification actions without violating Android service policy.
- Wire the fatal-sampling callback to a service-owned `EngineCommand.Stop(... SAMPLE_FAILURE)` and serialize its delivery.
- Ensure a failed new-session lease leaves its newly created row in a safe quarantined/recoverable state; do not silently start another session.
- Address cross-process status publishing and app in-foreground HUD suppression.
- Validate session analytics numerical parity, stored metadata, CSV/ZIP/DUKO exports, and upgrade safety.
- Native Canvas HUD operations are deliberately blocked until the new overlay owner exists.
- Test process kill, forced STOP lease commit failure, blocked collector I/O, sample-store lock/full, failed finalization, force-stop, and Android overlay refusal on Odin 2 Portal.

## Test evidence

The local branch preparation had **112 passing JVM checks** (prior 90 plus 22 new native-engine tests) using a lightweight Kotlin/JUnit-compatible harness; this is not an Android build or device test. The GitHub branch includes its own native-engine JUnit test suite; the authoritative CI and API 33 instrumentation results must be checked before treating the branch as validated.

No application ID, database schema, permissions, installed UI/HUD, session formats or legacy recorder behavior changed.

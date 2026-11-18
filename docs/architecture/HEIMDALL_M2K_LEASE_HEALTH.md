# HEIMDALL M2-K — Recovery-lease heartbeat is mandatory recording integrity

**Date:** 18 November 2026. **Base:** `feature/heimdall-m2j2-stop-sampler-faults`, commit `29f2c256ca2d4d246f4358348ad53c77143e87be`.

## Problem

The M2-J2 native Android recording IO updated its recovery lease from the sampling channel's `committed()` callback. The sampling loop intentionally swallowed exceptions from `committed()`, because it represents **optional HUD/presentation** work. The original `RecorderLease.heartbeat()` also returned `Unit` and ignored SharedPreferences `commit()` results. Consequently, a recording could keep saving samples while its *required* native recovery lease silently stopped renewing or was replaced by another session.

Android's usual recovery eligibility uses a 90-second freshness window and requires an original lease/row; valid samples without a durable lease may become stranded after a process kill.

## Implementation

1. **Mandatory independent callback.** `SamplingChannel.afterPersist(sessionId)` is called after the original sample is saved and counted, **before** optional `committed()` presentation. Failure stops further sampling and returns a structured fatal error to the authoritative `EngineController`. The successfully persisted sample is **not** miscounted as a missing tick or discarded.
2. **Per-sample owner validation.** `RecorderLease.heartbeatHeimdall(context, originalId, renew)` checks raw active/recording flags, exact SQLite session ID, `HEIMDALL_DEBUG` owner and persisted terminal STOP on every saved sample. It never updates a different owner, session or a stopped lease.
3. **Confirmed synchronous heartbeat.** When due (on first sample and then at least approximately every 12 seconds of successfully persisted samples), a synchronous SharedPreferences commit refreshes the two heartbeat timestamps and returns `RENEWED` only when `commit()` reports success. Other valid samples return `HEALTHY` without another disk write. On failure the last successful heartbeat timer is **not** advanced.
4. **Distinct integrity fault.** `HeimdallLeaseHealthException` carries stable `OWNER_CHANGED`, `TERMINAL_STOP_SEEN` or `COMMIT_FAILED` codes. The new sampler reports `LEASE_*` and exits rather than letting another sample persist. `HeimdallFatalStopPolicy` maps those faults to `StopReason.STORAGE_ERROR`, not a sensor failure. The single coordinator requests STOP; the sampler cannot independently finalize or erase the SQLite row.
5. **Optional display remains optional.** A HUD-render failure from `committed()` still cannot discard saved data or stop the recording; normal sensor/storage persist failures retain their separate bounded retry/fatal policy.

## Fault semantics and caveats

- A sample may be successfully persisted immediately before the mandatory lease checkpoint fails. It remains counted as stored (not missing). No further sampling continues after the detected failure.
- A lost/foreign owner means durable STOP may also fail. The engine must **not** fabricate STOP success, finalize or clear an unmatched lease; its existing `StopRequested`/retry path retains the original row for investigation. A full UI repair surface is a later milestone.
- Heartbeats are based on successfully stored sample ticks. Five consecutive storage failures stop the recorder within the existing short sampling window; the heartbeat mechanism does not introduce an idle polling service.
- This is not a power-loss guarantee: Android SharedPreferences `commit()` reports synchronous persistence success, but there is no claim of resilience to faulty flash hardware or forced OEM termination. The monitor remains the sole operating owner.
- The legacy CoreMetrics service's existing `RecorderLease.heartbeat()` is intentionally unchanged and is slated for deletion with the legacy recording lifecycle only after the new engine's controlled cutover. Shared sensor collectors, SQLite v9/exports and DUKO analytical code remain.

## Verification

- Pure JVM tests: mandatory heartbeat failure returns a fatal summary after exactly one saved sample; owner changes/terminal STOP stop sampling; optional presentation exceptions remain nonfatal; ordinary collection failure still produces a missing-tick result; fatal-stop reason mapping.
- Isolated API 33 Android SharedPreferences tests: correct owner, no extra commit on nonrenewal, foreign session/legacy owner, persisted STOP, absent lease and simulated synchronous `Editor.commit() == false`. These tests do not start a recorder or touch production session data and run in the database-integrity group.
- Existing emulator-only recording, process-kill recovery and post-crash STOP reconciliation tests remain mandatory in the CI matrix.
- The M2-K acceptance gate requires full Gradle JVM/build plus all API 33 emulator groups; no Odin 2 Portal validation is claimed until performed on the device.

## Next validation gates

1. Review CI failures, if any, without weakening tests; then archive this draft PR as an auditable milestone.
2. Improve user-visible recovery/STOP-failure diagnostics so a halted sampler never appears as healthy live recording.
3. Prepare a **debug-only Odin test build** with exact safe test instructions and rollback plan; obtain real evidence under Eden/game transitions, screen-off, app force-stop and Quick Settings events before replacing the default recorder.
4. After validated activation, remove the obsolete legacy sampler and lifecycle UI, while retaining measurement collectors, SQLite/analytics and reports.

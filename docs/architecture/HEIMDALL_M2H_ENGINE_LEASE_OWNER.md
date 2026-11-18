# HEIMDALL M2-H — Engine-owned lease and fail-closed recovery gate

**Date:** 18 November 2026. **Base:** `feature/heimdall-m2g-debug-ownership-handoff` at `82ba546b09e5ab238972cff7accd2ea0d3cce2f5`.

## Problem found in M2-G

The new Heimdall sampling engine could start under a debug-only ownership reservation, but `RecorderLease.start()` wrote the same untyped persisted recovery record as the legacy CoreMetrics service. A process restart (a null `onStartCommand` intent or explicit RECOVER, or a stale app/QS START) could then silently resume the historical session using **the legacy sampling loop**. That violates the agreed single-owner engine model even though no second loop runs concurrently after process death. It also invalidates native-engine comparisons and might change recovery rules.

## Implemented fixes

- Added `RecorderLease.Owner`: `LEGACY`, `HEIMDALL_DEBUG`, `UNKNOWN`. Old preferences with no `engine_owner` field still resolve to LEGACY. Any future/tampered owner name resolves UNKNOWN and must not grant legacy recovery.
- The `engine_owner` preference is written **in the same synchronous SharedPreferences commit** as `active`, `recording` and the original SQLite session ID. A separate `markDebugOwner()` commit was deliberately rejected to avoid a process-death race between lease creation and tagging.
- The Heimdall Android sampling IO creates and renews **HEIMDALL_DEBUG** leases, validates that tag for native STOP and preflight recovery, and does not clear a different engine's lease in its close-interrupted operation.
- `HeimdallLeaseRestartGuard` provides a deterministic pure-Kotlin restart decision. A raw active debug/unknown lease blocks legacy commands before legacy RECOVER/START/ARM can reach the old loop. This check applies even if the lease is too old for `RecorderLease.recover()` to authorize sampling.
- On a detected foreign-owner lease, the debug startup service returns **START_NOT_STICKY**, logs `HEIMDALL_RECOVERY_QUARANTINED`, preserves the original SQLite row and raw lease, and refuses to create a replacement row. It does not claim completion, resumed sampling or a successful recovery.
- `RecorderLease.clear()` clears the owner tag on successful normal lease cleanup. No schema/version migration or new SQLite metadata is required.
- Added 10 JVM routing-policy tests and 4 Android instrumentation tests of lease atomicity, old-lease compatibility, unknown-owner rejection and STOP/clear transitions. These use isolated test-only SharedPreferences, included in the API 33 database-integrity group.
- Added a separate API 33 `heimdall-native-runtime` instrumentation group. Its **emulator-only** test actually launches the debug `RecordingService`, waits for an original SQLite session to store genuine samples, sends a competing legacy START (which must be blocked), then requests STOP and verifies the original row is finalized and the recovery lease is cleared. The test uses a fresh disposable emulator, skips on physical hardware and does not silently clean up any failed session evidence.
- The existing private `StatusProvider` exposes additional debug lease-owner/ID evidence read from the **monitor process**. This avoids treating stale UI-process SharedPreferences caches as authoritative cross-process lease status.

## IMPORTANT: this is an intentional quarantine, not native automatic recovery

This stage **prevents cross-engine restart**, but **does not yet implement fully native Heimdall recovery**. A debug session interrupted by Android process death remains in its original SQLite row/lease, quarantined for a later explicit recovery/finalization implementation. The debug engine stays opt-in, unavailable in production and not a normal user-facing toggle. Do not test this on a device with valuable unbacked-up sessions.

M2-I must implement the single-owner native recovery path using the persisted `HEIMDALL_DEBUG` ID and validated SQLite/lease state; handle user STOP already durable, expired leases, interrupted finalization, cancellation and process death without creating a new session ID. Service must be promoted to foreground before any lease/SQLite reads, and claim ownership before launching a sampler. A genuinely expired or corrupt lease may need explicit user-visible repair that does not silently clear existing data.

## Verification gate

- All current legacy instrumentation and JVM suites must pass in GitHub Actions; an M2-G run failed because Maven Central returned HTTP 429 during Gradle dependency resolution, while a second run for that same commit passed. That was infrastructure, not a failed test assertion.
- The isolated Android lease-compatibility tests run only inside a test-specific prefs wrapper. They do not start a service or sample hardware.
- The new separate emulator-only service integration test DOES start a debug recorder and collect real samples in a fresh disposable emulator. It is not an Odin device test and does not test process-death native resumption.
- Do not claim this stage has demonstrated Odin process-death recovery or a complete Android end-to-end recording. Only the ownership-tag behavior and fail-closed policy are covered here.

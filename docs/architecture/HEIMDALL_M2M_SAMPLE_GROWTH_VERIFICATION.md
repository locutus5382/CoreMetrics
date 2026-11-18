# HEIMDALL M2-M — Persisted sample-growth verification

**Based on the isolated trial build in PR #63; applies only to `heimdallTrial`.**

## Feature

The non-exported **HEIMDALL Trial → Settings → device test console** includes a new **Verify persisted sample growth (8 seconds)** action, shown only when the native recorder is in `RECORDING`, the raw recovery lease belongs to `HEIMDALL_DEBUG`, the original SQLite row is `RECORDING`, and no unresolved recovery evidence is reported.

On a user tap, it reads the private `:monitor` status and *persisted SQLite sample count*, waits eight seconds, then reads them again. A successful result requires:

- Both monitor snapshots are available and the correct recorder owner is still present.
- The same strictly positive original session ID exists at both observations.
- Both states and SQLite rows confirm the original session is `RECORDING`.
- The persisted sample count increased. No UI-side counter or inferred FPS is accepted.

The result (explicit pass/failure code, original ID and sample delta) is appended to the copyable diagnostic summary. STOP is deliberately not disabled during verification: a user may stop at any time. If the session changes or stops during verification, the check **fails safely**, not as a fictitious positive.

This measures **database sample progress**, not sampling accuracy, GPU FPS, power accuracy, thermal correctness or battery efficiency. Those need separate Odin game-based validation.

## Test sequence

1. Install **only** the isolated `HEIMDALL-M2M-isolated-Odin-debug-trial-APK` from a successfully validated GitHub CI run. Never overwrite the regular CoreMetrics package; back up existing valuable sessions first.
2. Open Settings → the trial console. START a short native session, verify raw owner and original session ID.
3. Tap **Verify persisted sample growth (8 seconds)**. Expect `VERIFIED_GROWTH` for the same ID and a positive delta. If `NO_NEW_SAMPLES` appears, preserve diagnostics; do not clear leases or force a new session.
4. Press STOP. Verify one completed SQLite row, original ID preserved and no active raw lease.
5. Copy the diagnostic summary; then test a longer Eden session and exports in the isolated app.
6. Do not interpret `NOT_RECORDING`, `SESSION_CHANGED`, `OWNER_MISMATCH` or `STATUS_UNAVAILABLE` as successes. These are actionable diagnostics for development.

## Safety and validation

No service, sampler, manifest, application ID, permissions, database schema, production tile or legacy recording control is changed here. The pure sample-growth policy has nine JVM tests for valid growth, owner/lease conflicts, session swaps, missing status, terminal rows and incorrect phases. The full debug and `heimdallTrial` builds and API33 emulator matrix must pass before an updated Odin APK is recommended.

M2-N production cutover and M2-O legacy-engine deletion remain blocked until physical Odin evidence, session/report compatibility and a verified rollback are available.
